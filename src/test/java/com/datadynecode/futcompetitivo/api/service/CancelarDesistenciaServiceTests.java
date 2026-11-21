package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.jpa.show-sql=false")
@ActiveProfiles("test")
@Transactional
class CancelarDesistenciaServiceTests {
    @Autowired CampeonatoService service;
    @Autowired OrganizacaoService organizacoes;
    @Autowired JogadorService jogadores;
    @Autowired EquipeService equipes;
    @Autowired EntityManager em;

    @ParameterizedTest
    @EnumSource(value=TipoFormato.class,names={"LIGA","LIGA_PARCIAL","MISTA","MISTA_PARCIAL","GRUPOS","SWISS"})
    void restauraAgendaSemApagarJogosDisputadosOuCriarDuasRodadasAbertas(TipoFormato formato) {
        var c=iniciar(formato);
        var primeira=c.fases().getFirst().rodadas().getFirst();
        var jogada=primeira.partidas().getFirst();
        var participante=primeira.partidas().getLast().mandante().id();
        var antes=service.registrarResultado(c.id(),jogada.id(),placar(2,1));
        service.desistirParticipante(c.id(),participante);
        var restaurado=service.cancelarDesistencia(c.id(),participante);
        assertEquals(antes,restaurado);
        assertEquals(1,restaurado.fases().getFirst().rodadas().stream().filter(r -> r.status()==RodadaStatus.EM_ANDAMENTO).count());
        em.flush(); em.clear();
        assertEquals(restaurado,service.buscar(c.id()));
        assertEquals(restaurado,service.cancelarDesistencia(c.id(),participante));
    }

    @Test
    void reabreRodadaEncerradaPorWoSemAlterarOutrosPlacares() {
        var c=iniciar(TipoFormato.LIGA);
        var r=c.fases().getFirst().rodadas().getFirst();
        for(var p:r.partidas().subList(1,r.partidas().size())) service.registrarResultado(c.id(),p.id(),placar(2,1));
        Long participante=r.partidas().getFirst().mandante().id();
        var desistiu=service.desistirParticipante(c.id(),participante);
        assertEquals(RodadaStatus.FINALIZADA,desistiu.fases().getFirst().rodadas().getFirst().status());
        assertEquals(1,desistiu.classificacaoPorRodada().size());
        var voltou=service.cancelarDesistencia(c.id(),participante);
        assertTrue(voltou.classificacaoPorRodada().isEmpty());
        assertTrue(voltou.classificacaoAtual().parcial());
        assertEquals(RodadaStatus.EM_ANDAMENTO,voltou.fases().getFirst().rodadas().getFirst().status());
        assertTrue(voltou.fases().getFirst().rodadas().stream().skip(1).allMatch(x -> x.status()==RodadaStatus.PENDENTE));
        var jogos=voltou.fases().getFirst().rodadas().getFirst().partidas();
        assertNull(jogos.getFirst().resultado()); assertFalse(jogos.getFirst().wo());
        assertTrue(jogos.stream().skip(1).allMatch(p -> p.resultado().golsMandante()==2 && !p.wo()));
        for(var rodada:voltou.fases().getFirst().rodadas()) for(var p:rodada.partidas()) if(p.status()!=StatusPartida.FINALIZADA)
            service.registrarResultado(c.id(),p.id(),placar(1,0));
        assertEquals(CampeonatoStatus.FINALIZADO,service.buscar(c.id()).status());
    }

    @Test
    void woDuploViraVitoriaDoRetornanteEDepoisVoltaAAgendado() {
        var c=iniciar(TipoFormato.LIGA); var p=c.fases().getFirst().rodadas().getFirst().partidas().getFirst();
        service.desistirParticipante(c.id(),p.mandante().id()); service.desistirParticipante(c.id(),p.visitante().id());
        var voltou=service.cancelarDesistencia(c.id(),p.mandante().id());
        var wo=voltou.fases().getFirst().rodadas().getFirst().partidas().getFirst();
        assertTrue(wo.wo()); assertEquals(3,wo.resultado().golsMandante()); assertEquals(0,wo.resultado().golsVisitante());
        assertEquals(List.of(p.visitante().id()),voltou.participantesDesistentes());
        assertEquals(c,service.cancelarDesistencia(c.id(),p.visitante().id()));
    }

    @Test
    void recalculaWoDuploDeRodadaFechadaSemReabrirJogoQueContinuaWo() {
        var c=iniciar(TipoFormato.LIGA); var r=c.fases().getFirst().rodadas().getFirst(); var p=r.partidas().getFirst();
        for(var jogo:r.partidas().subList(1,r.partidas().size())) service.registrarResultado(c.id(),jogo.id(),placar(1,0));
        service.desistirParticipante(c.id(),p.mandante().id()); service.desistirParticipante(c.id(),p.visitante().id());
        var voltou=service.cancelarDesistencia(c.id(),p.mandante().id());
        assertEquals(RodadaStatus.FINALIZADA,voltou.fases().getFirst().rodadas().getFirst().status());
        var tabela=voltou.classificacaoPorRodada().getFirst().tabela();
        assertEquals(3,tabela.stream().filter(x -> x.participante().id().equals(p.mandante().id())).findFirst().orElseThrow().pontos());
        assertEquals(0,tabela.stream().filter(x -> x.participante().id().equals(p.visitante().id())).findFirst().orElseThrow().pontos());
    }

    @Test
    void restauraVagaAntesDaChaveMasBloqueiaParticipanteJaSubstituido() {
        var c=iniciar(TipoFormato.MISTA); preencher(c.id(),c.fases().getFirst());
        Long lider=service.classificados(c.id(),4).getFirst().id();
        var antes=service.buscar(c.id()); service.desistirParticipante(c.id(),lider);
        assertFalse(service.vagasMataMata(c.id()).isEmpty());
        assertEquals(antes,service.cancelarDesistencia(c.id(),lider));
        assertTrue(service.vagasMataMata(c.id()).isEmpty());
        service.desistirParticipante(c.id(),lider);
        var candidato=service.vagasMataMata(c.id()).getFirst().candidatos().getFirst().id();
        var chave=service.iniciarMataMata(c.id(),new IniciarMataMataRequest(List.of(new IniciarMataMataRequest.Substituicao(lider,candidato))));
        assertThrows(RegraCampeonatoException.class,()->service.cancelarDesistencia(c.id(),lider));
        assertEquals(chave,service.buscar(c.id()));
    }

    @Test
    void bloqueiaRodadasPosterioresDisputadasESwissJaPareado() {
        var liga=iniciar(TipoFormato.LIGA); var primeira=liga.fases().getFirst().rodadas().getFirst();
        Long participante=primeira.partidas().getFirst().mandante().id();
        service.desistirParticipante(liga.id(),participante);
        for(var p:service.buscar(liga.id()).fases().getFirst().rodadas().getFirst().partidas()) if(!p.wo()) service.registrarResultado(liga.id(),p.id(),placar(1,0));
        var segunda=service.buscar(liga.id()).fases().getFirst().rodadas().get(1);
        var jogada=segunda.partidas().stream().filter(p -> !p.wo()).findFirst().orElseThrow();
        var antes=service.registrarResultado(liga.id(),jogada.id(),placar(1,0));
        assertThrows(RegraCampeonatoException.class,()->service.cancelarDesistencia(liga.id(),participante));
        assertEquals(antes,service.buscar(liga.id()));
        var swiss=iniciar(TipoFormato.SWISS); var rodada=swiss.fases().getFirst().rodadas().getFirst();
        Long desistente=rodada.partidas().getFirst().mandante().id(); service.desistirParticipante(swiss.id(),desistente);
        for(var p:service.buscar(swiss.id()).fases().getFirst().rodadas().getFirst().partidas()) if(!p.wo()) service.registrarResultado(swiss.id(),p.id(),placar(1,0));
        service.gerarProximaRodadaSwiss(swiss.id()); var pareado=service.buscar(swiss.id());
        assertThrows(RegraCampeonatoException.class,()->service.cancelarDesistencia(swiss.id(),desistente));
        assertEquals(pareado,service.buscar(swiss.id()));
    }

    @Test
    void bloqueiaClassificatoriaQueJaGerouMataMata() {
        var c=iniciar(TipoFormato.MISTA); Long participante=c.fases().getFirst().rodadas().getFirst().partidas().getFirst().mandante().id();
        var desistiu=service.desistirParticipante(c.id(),participante); preencher(c.id(),desistiu.fases().getFirst());
        var escolhas=service.vagasMataMata(c.id()).stream().map(v -> new IniciarMataMataRequest.Substituicao(v.desistenteId(),v.candidatos().getFirst().id())).toList();
        var chave=service.iniciarMataMata(c.id(),new IniciarMataMataRequest(escolhas));
        assertThrows(RegraCampeonatoException.class,()->service.cancelarDesistencia(c.id(),participante));
        assertEquals(chave,service.buscar(c.id()));
    }

    @Test
    void permiteRetirarWoNaRodadaAtualDoMataMataMasNaoDepoisDoAvanco() {
        var c=iniciar(TipoFormato.ELIMINATORIO); var rodada=c.fases().getFirst().rodadas().getFirst();
        var p=rodada.partidas().getFirst(); service.desistirParticipante(c.id(),p.mandante().id());
        assertEquals(c,service.cancelarDesistencia(c.id(),p.mandante().id()));
        service.desistirParticipante(c.id(),p.mandante().id());
        for(var jogo:service.buscar(c.id()).fases().getFirst().rodadas().getFirst().partidas()) if(!jogo.wo()) service.registrarResultado(c.id(),jogo.id(),placar(1,0));
        var avancou=service.buscar(c.id()); assertFalse(avancou.fases().getFirst().rodadas().get(1).partidas().isEmpty());
        assertThrows(RegraCampeonatoException.class,()->service.cancelarDesistencia(c.id(),p.mandante().id()));
        assertEquals(avancou,service.buscar(c.id()));
    }

    @Test
    void reabreFinalEncerradaPorWo() {
        var c=service.iniciar(criar(4).id(),new IniciarCampeonatoRequest(TipoFormato.ELIMINATORIO,false,null,null));
        for(var p:c.fases().getFirst().rodadas().getFirst().partidas()) service.registrarResultado(c.id(),p.id(),placar(1,0));
        var antes=service.buscar(c.id()); var finalJogo=antes.fases().getFirst().rodadas().getLast().partidas().getFirst();
        assertEquals(CampeonatoStatus.FINALIZADO,service.desistirParticipante(c.id(),finalJogo.mandante().id()).status());
        var voltou=service.cancelarDesistencia(c.id(),finalJogo.mandante().id());
        assertEquals(antes,voltou);
        service.registrarResultado(c.id(),finalJogo.id(),placar(0,1));
        assertEquals(finalJogo.visitante().id(),service.vencedor(c.id()).id());
    }

    @Test
    void validaParticipanteEPermiteRetornoSemInativarOuReativarCadastros() {
        var c=iniciar(TipoFormato.LIGA); Long participante=c.fases().getFirst().rodadas().getFirst().partidas().getFirst().mandante().id();
        service.desistirParticipante(c.id(),participante);
        var cadastro=c.participantes().stream().filter(p -> p.id().equals(participante)).findFirst().orElseThrow();
        jogadores.desativar(cadastro.jogador().id()); equipes.desativar(cadastro.equipe().id());
        var voltou=service.cancelarDesistencia(c.id(),participante);
        var registro=voltou.participantes().stream().filter(p -> p.id().equals(participante)).findFirst().orElseThrow();
        assertFalse(registro.jogador().ativo()); assertFalse(registro.equipe().ativo());
        assertThrows(RegraCampeonatoException.class,()->service.cancelarDesistencia(c.id(),Long.MAX_VALUE));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.cancelarDesistencia(Long.MAX_VALUE,participante)).getStatusCode().value());
    }

    private void preencher(Long id,FaseResponse fase) {
        for(var r:fase.rodadas()) for(var p:r.partidas()) if(!p.wo()) service.registrarResultado(id,p.id(),placar(1,0));
    }
    private ResultadoRequest placar(int a,int b) {return new ResultadoRequest(a,b,null,null,false);}
    private CampeonatoResponse iniciar(TipoFormato formato) {
        var c=criar(formato==TipoFormato.ELIMINATORIO ? 8:6);
        return service.iniciar(c.id(),new IniciarCampeonatoRequest(formato,false,
                formato==TipoFormato.LIGA_PARCIAL || formato==TipoFormato.MISTA_PARCIAL ? 3:null,
                formato==TipoFormato.SWISS ? 3:null,
                formato==TipoFormato.MISTA || formato==TipoFormato.MISTA_PARCIAL || formato==TipoFormato.GRUPOS ? 4:null));
    }
    private CampeonatoResponse criar(int total) {
        var org=organizacoes.criar(new OrganizacaoRequest("Retorno",true)); var lista=new ArrayList<ParticipanteRequest>();
        for(int i=0;i<total;i++) {
            var j=jogadores.criar(new JogadorRequest("Jogador "+i,null)); var e=equipes.criar(new EquipeRequest("Equipe "+i,"https://example.com/logo.png"));
            lista.add(new ParticipanteRequest(j.id(),e.id()));
        }
        return service.criar(new CampeonatoRequest(org.id(),1,lista));
    }
}
