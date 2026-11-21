package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("test")
@Transactional
class TransicaoDesistenciaServiceTests {
    @Autowired CampeonatoService service;
    @Autowired OrganizacaoService organizacoes;
    @Autowired JogadorService jogadores;
    @Autowired EquipeService equipes;
    @Autowired EntityManager em;

    @Test
    void exigeSolicitacaoSomenteParaEntrarNoMataMata() {
        var criado = criar(6);
        var iniciado = service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.MISTA,false,null,null,4));
        assertThrows(RegraCampeonatoException.class, () -> service.iniciarMataMata(criado.id()));
        preencher(criado.id(), iniciado.fases().getFirst());
        var aguardando = service.buscar(criado.id());
        assertTrue(aguardando.aguardandoMataMata()); assertEquals(1, aguardando.fases().size());
        assertEquals(CampeonatoStatus.EM_ANDAMENTO, aguardando.status());
        var mata = service.iniciarMataMata(criado.id()).fases().getLast();
        assertThrows(RegraCampeonatoException.class, () -> service.iniciarMataMata(criado.id()));
        assertEquals(2, mata.rodadas().getFirst().partidas().size());
        var finalId = mata.rodadas().getLast().id();
        assertThrows(RegraCampeonatoException.class, () -> service.iniciarRodadaMataMata(criado.id(),finalId));
        for (var p : mata.rodadas().getFirst().partidas()) service.registrarResultado(criado.id(),p.id(),placar(1,0));
        var aberta = service.buscar(criado.id()).fases().getLast().rodadas().getLast();
        assertEquals(RodadaStatus.EM_ANDAMENTO, aberta.status()); assertEquals(1, aberta.partidas().size());
        assertThrows(RegraCampeonatoException.class, () -> service.iniciarRodadaMataMata(criado.id(),finalId));
    }

    @Test
    void desistirAplicaWoSomenteNosJogosPendentesSemInativarCadastros() {
        var criado = criar(6);
        var iniciado = service.iniciar(criado.id(),new IniciarCampeonatoRequest(TipoFormato.LIGA,false,null,null));
        var jogada = iniciado.fases().getFirst().rodadas().getFirst().partidas().getFirst();
        Long participante = jogada.mandante().id();
        service.registrarResultado(criado.id(),jogada.id(),placar(2,1));
        var resposta = service.desistirParticipante(criado.id(),participante);
        assertEquals(List.of(participante),resposta.participantesDesistentes());
        var jogos = resposta.fases().getFirst().rodadas().stream().flatMap(r -> r.partidas().stream())
                .filter(p -> p.mandante().id().equals(participante) || p.visitante().id().equals(participante)).toList();
        assertEquals(5,jogos.size()); assertEquals(4,jogos.stream().filter(PartidaResponse::wo).count());
        var original = jogos.stream().filter(p -> p.id().equals(jogada.id())).findFirst().orElseThrow();
        assertFalse(original.wo()); assertEquals(2,original.resultado().golsMandante());
        for (var p : jogos.stream().filter(PartidaResponse::wo).toList()) {
            assertEquals(StatusPartida.FINALIZADA,p.status());
            assertEquals(p.mandante().id().equals(participante) ? 0 : 3,p.resultado().golsMandante());
            assertEquals(p.visitante().id().equals(participante) ? 0 : 3,p.resultado().golsVisitante());
        }
        var cadastro = resposta.participantes().stream().filter(p -> p.id().equals(participante)).findFirst().orElseThrow();
        assertTrue(cadastro.ativo()); assertTrue(cadastro.jogador().ativo()); assertTrue(cadastro.equipe().ativo());
        assertEquals(resposta.participantesDesistentes(),service.desistirParticipante(criado.id(),participante).participantesDesistentes());
        em.flush(); em.clear();
        assertEquals(List.of(participante), service.buscar(criado.id()).participantesDesistentes());
    }

    @Test
    void doisDesistentesFicamEmZeroAZeroSemReescreverJogoDisputado() {
        var criado = criar(6);
        var iniciado = service.iniciar(criado.id(),new IniciarCampeonatoRequest(TipoFormato.LIGA,false,null,null));
        var primeira = iniciado.fases().getFirst().rodadas().getFirst().partidas().getFirst();
        service.registrarResultado(criado.id(),primeira.id(),placar(2,0));
        var futura = iniciado.fases().getFirst().rodadas().stream().skip(1).flatMap(r -> r.partidas().stream())
                .filter(p -> p.mandante().id().equals(primeira.mandante().id()) || p.visitante().id().equals(primeira.mandante().id())).findFirst().orElseThrow();
        service.desistirParticipante(criado.id(),futura.mandante().id());
        var resposta = service.desistirParticipante(criado.id(),futura.visitante().id());
        var dupla = resposta.fases().getFirst().rodadas().stream().flatMap(r -> r.partidas().stream()).filter(p -> p.id().equals(futura.id())).findFirst().orElseThrow();
        assertTrue(dupla.wo()); assertEquals(0,dupla.resultado().golsMandante()); assertEquals(0,dupla.resultado().golsVisitante());
        var disputada = resposta.fases().getFirst().rodadas().getFirst().partidas().stream().filter(p -> p.id().equals(primeira.id())).findFirst().orElseThrow();
        assertEquals(2,disputada.resultado().golsMandante()); assertFalse(disputada.wo());
    }

    @Test
    void vagaDeDesistenteExigeEscolhaManualNoMesmoGrupoSemPromocaoAutomatica() {
        var criado = criar(12);
        var ids = criado.participantes().stream().map(ParticipanteResponse::id).toList();
        var iniciado = service.iniciarComGruposManuais(criado.id(),new IniciarGruposManuaisRequest(ids.subList(0,6),ids.subList(6,12),4,false,false));
        assertThrows(RegraCampeonatoException.class, () -> service.vagasMataMata(criado.id()));
        preencher(criado.id(),iniciado.fases().getFirst());
        var grupos = service.grupos(criado.id());
        Long desistente = service.classificacaoGrupo(criado.id(),grupos.getFirst().id()).tabela().getFirst().participante().id();
        var antes = service.classificacaoGrupo(criado.id(),grupos.getFirst().id());
        service.desistirParticipante(criado.id(),desistente);
        assertEquals(antes, service.classificacaoGrupo(criado.id(),grupos.getFirst().id()));
        var vaga = service.vagasMataMata(criado.id()).getFirst();
        assertEquals(desistente,vaga.desistenteId()); assertEquals(grupos.getFirst().id(),vaga.grupoId());
        assertEquals(4,vaga.candidatos().size());
        assertThrows(RegraCampeonatoException.class, () -> service.iniciarMataMata(criado.id()));
        Long escolhido = vaga.candidatos().getLast().id();
        Long outroGrupo = service.classificacaoGrupo(criado.id(),grupos.getLast().id()).tabela().getLast().participante().id();
        assertThrows(RegraCampeonatoException.class, () -> service.iniciarMataMata(criado.id(),
                new IniciarMataMataRequest(List.of(new IniciarMataMataRequest.Substituicao(desistente,outroGrupo)))));
        var iniciadoMata = service.iniciarMataMata(criado.id(),
                new IniciarMataMataRequest(List.of(new IniciarMataMataRequest.Substituicao(desistente,escolhido))));
        var selecionados = iniciadoMata.fases().getLast().rodadas().getFirst().partidas().stream()
                .flatMap(p -> java.util.stream.Stream.of(p.mandante().id(),p.visitante().id())).toList();
        assertTrue(selecionados.contains(escolhido)); assertFalse(selecionados.contains(desistente));
        assertEquals(4,selecionados.size());
        em.flush(); em.clear();
        assertEquals(escolhido,service.buscar(criado.id()).substituicoesMataMata().get(desistente));
    }

    @Test
    void desistenteForaDosClassificadosNaoAbreVaga() {
        var criado = criar(6);
        var iniciado = service.iniciar(criado.id(),new IniciarCampeonatoRequest(TipoFormato.MISTA,false,null,null,4));
        preencher(criado.id(),iniciado.fases().getFirst());
        var ultimo = service.classificacao(criado.id()).tabela().getLast().participante().id();
        service.desistirParticipante(criado.id(),ultimo);
        assertTrue(service.vagasMataMata(criado.id()).isEmpty());
        assertEquals(2,service.iniciarMataMata(criado.id()).fases().size());
    }

    @Test
    void vagaNaLigaTambemDependeDeSubstituicaoManual() {
        var criado = criar(6);
        var iniciado = service.iniciar(criado.id(),new IniciarCampeonatoRequest(TipoFormato.MISTA,false,null,null,4));
        preencher(criado.id(),iniciado.fases().getFirst());
        Long lider = service.classificacao(criado.id()).tabela().getFirst().participante().id();
        service.desistirParticipante(criado.id(),lider);
        var vaga = service.vagasMataMata(criado.id()).getFirst();
        assertNull(vaga.grupoId()); assertEquals(2,vaga.candidatos().size());
        assertThrows(RegraCampeonatoException.class, () -> service.iniciarMataMata(criado.id()));
        Long escolhido = vaga.candidatos().getLast().id();
        var resposta = service.iniciarMataMata(criado.id(),new IniciarMataMataRequest(
                List.of(new IniciarMataMataRequest.Substituicao(lider,escolhido))));
        assertEquals(escolhido,resposta.substituicoesMataMata().get(lider));
    }

    @Test
    void doisDesistentesNoMataMataNaoAvancamComoVencedores() {
        var criado = criar(4);
        var iniciado = service.iniciar(criado.id(),new IniciarCampeonatoRequest(TipoFormato.ELIMINATORIO,false,null,null));
        var semis = iniciado.fases().getFirst().rodadas().getFirst().partidas();
        var desistentes = semis.getFirst();
        service.desistirParticipante(criado.id(),desistentes.mandante().id());
        service.desistirParticipante(criado.id(),desistentes.visitante().id());
        var outra = semis.getLast();
        service.registrarResultado(criado.id(),outra.id(),placar(1,0));
        var resposta = service.buscar(criado.id());
        assertEquals(CampeonatoStatus.FINALIZADO,resposta.status());
        assertEquals(outra.mandante().id(),service.vencedor(criado.id()).id());
    }

    @Test
    void desistenciasDeUmCampeonatoNaoAfetamOutroNemPermitemEditarWo() {
        var criado = criar(6);
        var iniciado = service.iniciar(criado.id(),new IniciarCampeonatoRequest(TipoFormato.LIGA,false,null,null));
        var p = iniciado.fases().getFirst().rodadas().getFirst().partidas().getFirst();
        service.desistirParticipante(criado.id(),p.mandante().id());
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(criado.id(),p.id(),placar(5,0)));
        var outro = criar(4);
        assertTrue(service.buscar(outro.id()).participantesDesistentes().isEmpty());
        assertThrows(RegraCampeonatoException.class, () -> service.desistirParticipante(outro.id(),outro.participantes().getFirst().id()));
    }

    @Test
    void woNaVoltaEliminaDesistenteMesmoComVantagemNaIda() {
        var criado = criar(4);
        var iniciado = service.iniciar(criado.id(),new IniciarCampeonatoRequest(TipoFormato.ELIMINATORIO,false,null,null,null,FormatoEliminatorio.IDA_E_VOLTA));
        var primeira = iniciado.fases().getFirst().rodadas().getFirst();
        var confronto = iniciado.fases().getFirst().confrontos().getFirst();
        var ida = primeira.partidas().stream().filter(p -> p.id().equals(confronto.partidaIds().getFirst())).findFirst().orElseThrow();
        service.registrarResultado(criado.id(),ida.id(),placar(10,0));
        service.desistirParticipante(criado.id(),ida.mandante().id());
        var outra = service.buscar(criado.id()).fases().getFirst().rodadas().getFirst();
        for (var p : outra.partidas()) if (p.status()!=StatusPartida.FINALIZADA) {
            var c = iniciado.fases().getFirst().confrontos().stream().filter(x -> x.partidaIds().contains(p.id())).findFirst().orElseThrow();
            service.registrarResultado(criado.id(),p.id(),p.id().equals(c.partidaIds().getLast()) ? placar(0,1) : placar(1,0));
        }
        var finalRodada = service.buscar(criado.id()).fases().getFirst().rodadas().getLast();
        var aberta = service.buscar(criado.id());
        var finalistas = aberta.fases().getFirst().rodadas().getLast().partidas().getFirst();
        assertTrue(finalistas.mandante().id().equals(ida.visitante().id()) || finalistas.visitante().id().equals(ida.visitante().id()));
        assertFalse(finalistas.mandante().id().equals(ida.mandante().id()) || finalistas.visitante().id().equals(ida.mandante().id()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(FormatoEliminatorio.class)
    void permiteWoNaFinalENoTerceiroLugar(FormatoEliminatorio modo) {
        var criado = criar(4);
        var iniciado = service.iniciar(criado.id(),new IniciarCampeonatoRequest(
                TipoFormato.ELIMINATORIO,false,null,null,null,modo,true));
        var fase = iniciado.fases().getFirst();
        for (var confronto : fase.confrontos().stream().filter(c -> c.origemUmId()==null).toList()) {
            for (int i=0;i<confronto.partidaIds().size();i++)
                service.registrarResultado(criado.id(),confronto.partidaIds().get(i),i==0 ? placar(1,0) : placar(0,1));
        }
        var decisoes = service.buscar(criado.id()).fases().getFirst();
        var finalConfronto = decisoes.confrontos().getLast();
        var bronze = decisoes.confrontos().stream().filter(ConfrontoResponse::terceiroLugar).findFirst().orElseThrow();
        int jogos = modo==FormatoEliminatorio.IDA_E_VOLTA ? 2 : 1;
        assertEquals(jogos,finalConfronto.partidaIds().size());
        assertEquals(jogos,bronze.partidaIds().size());
        service.desistirParticipante(criado.id(),finalConfronto.participanteUmId());
        var resposta = service.desistirParticipante(criado.id(),bronze.participanteUmId());
        assertEquals(CampeonatoStatus.FINALIZADO,resposta.status());
        assertTrue(resposta.fases().getFirst().rodadas().getLast().partidas().stream().allMatch(PartidaResponse::wo));
        assertEquals(finalConfronto.participanteDoisId(),service.vencedor(criado.id()).id());
        assertEquals(bronze.participanteDoisId(),service.terceiroLugar(criado.id()).id());
    }

    private void preencher(Long id, FaseResponse fase) {
        for (var rodada : fase.rodadas()) for (var p : rodada.partidas())
            if (p.status()!=StatusPartida.FINALIZADA) service.registrarResultado(id,p.id(),placar(1,0));
    }
    private ResultadoRequest placar(int mandante,int visitante) { return new ResultadoRequest(mandante,visitante,null,null,false); }
    private CampeonatoResponse criar(int quantidade) {
        var org = organizacoes.criar(new OrganizacaoRequest("Transicao",true));
        var participantes = new ArrayList<ParticipanteRequest>();
        for(int i=0;i<quantidade;i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador " + i,null));
            var equipe = equipes.criar(new EquipeRequest("Equipe " + i,"https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(jogador.id(),equipe.id()));
        }
        return service.criar(new CampeonatoRequest(org.id(),2026,participantes));
    }
}
