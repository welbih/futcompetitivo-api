package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.jpa.show-sql=false","spring.datasource.url=jdbc:h2:mem:sse-transacoes;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class EventosCampeonatoServiceTests {
    @Autowired CampeonatoService service;
    @Autowired OrganizacaoService organizacoes;
    @Autowired JogadorService jogadores;
    @Autowired EquipeService equipes;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean CampeonatoSseService sse;

    @Test
    void publicaSomenteAposCommitEAgregaPorCampeonato() {
        var c=iniciar(4,TipoFormato.LIGA); reset(sse);
        var jogos=c.fases().getFirst().rodadas().getFirst().partidas();
        Instant antes=Instant.now();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for(var p:jogos) service.registrarResultado(c.id(),p.id(),placar(1,0));
            verifyNoInteractions(sse);
        });
        var evento=unico();
        assertEquals(c.id(),evento.campeonatoId());
        assertEquals(MotivoAtualizacaoCampeonato.RESULTADO_ALTERADO,evento.motivo());
        assertEquals(jogos.getLast().id(),evento.partidaId());
        assertFalse(evento.ocorridoEm().isBefore(antes));
        assertEquals(1,service.buscar(c.id()).classificacaoPorRodada().size());
    }

    @Test
    void rollbackEOperacaoRejeitadaNaoNotificam() {
        var c=iniciar(4,TipoFormato.LIGA); reset(sse);
        Long partida=c.fases().getFirst().rodadas().getFirst().partidas().getFirst().id();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.registrarResultado(c.id(),partida,placar(2,0));
            status.setRollbackOnly();
        });
        verifyNoInteractions(sse);
        assertNull(service.buscar(c.id()).fases().getFirst().rodadas().getFirst().partidas().getFirst().resultado());
        assertThrows(RegraCampeonatoException.class,()->service.registrarResultado(c.id(),partida,placar(-1,0)));
        verifyNoInteractions(sse);
        service.registrarResultado(c.id(),partida,placar(1,0));
        assertEquals(MotivoAtualizacaoCampeonato.RESULTADO_ALTERADO,unico().motivo());
        reset(sse);
        service.registrarResultado(c.id(),partida,placar(1,0));
        verifyNoInteractions(sse);
    }

    @Test
    void loteEFinalizacaoCadaUmProduzemUmaNotificacao() {
        var c=iniciar(4,TipoFormato.LIGA); reset(sse);
        var rodada=c.fases().getFirst().rodadas().getFirst();
        var resultados=rodada.partidas().stream().map(p -> new ResultadoPartidaRequest(p.id(),placar(2,1))).toList();
        service.registrarResultadosSemFinalizar(c.id(),rodada.id(),resultados);
        var evento=unico();
        assertEquals(MotivoAtualizacaoCampeonato.RESULTADOS_ALTERADOS,evento.motivo());
        assertEquals(rodada.id(),evento.rodadaId()); assertNull(evento.partidaId());
        reset(sse);
        service.finalizarRodada(c.id(),rodada.id());
        assertEquals(MotivoAtualizacaoCampeonato.RODADA_FINALIZADA,unico().motivo());
    }

    @Test
    void desistirComVariosWoNaoDuplicaEventos() {
        var c=iniciar(6,TipoFormato.LIGA); reset(sse);
        var primeira=c.fases().getFirst().rodadas().getFirst();
        Long participante=primeira.partidas().getFirst().mandante().id();
        for(var p:primeira.partidas().subList(1,primeira.partidas().size())) service.registrarResultado(c.id(),p.id(),placar(1,0));
        reset(sse);
        var atualizado=service.desistirParticipante(c.id(),participante);
        assertEquals(5,atualizado.fases().getFirst().rodadas().stream().flatMap(r -> r.partidas().stream()).filter(PartidaResponse::wo).count());
        assertEquals(MotivoAtualizacaoCampeonato.DESISTENCIA_REGISTRADA,unico().motivo());
        assertEquals(RodadaStatus.FINALIZADA,atualizado.fases().getFirst().rodadas().getFirst().status());
        reset(sse); service.desistirParticipante(c.id(),participante); verifyNoInteractions(sse);
    }

    @Test
    void inicioAtualizacaoExclusaoEAberturaSwiss() {
        var c=criar(4); reset(sse);
        service.atualizar(c.id(),new CampeonatoRequest(c.organizacao().id(),2021,c.participantes().stream()
                .map(p -> new ParticipanteRequest(p.jogador().id(),p.equipe().id())).toList()));
        assertEquals(MotivoAtualizacaoCampeonato.CAMPEONATO_ALTERADO,unico().motivo());
        reset(sse);
        var iniciado=service.iniciar(c.id(),new IniciarCampeonatoRequest(TipoFormato.SWISS,false,null,2));
        assertEquals(MotivoAtualizacaoCampeonato.CAMPEONATO_INICIADO,unico().motivo());
        for(var p:iniciado.fases().getFirst().rodadas().getFirst().partidas()) service.registrarResultado(c.id(),p.id(),placar(1,0));
        reset(sse);
        var proxima=service.gerarProximaRodadaSwiss(c.id());
        assertEquals(proxima.id(),unico().rodadaId());
        reset(sse); service.deletar(c.id());
        assertEquals(MotivoAtualizacaoCampeonato.CAMPEONATO_EXCLUIDO,unico().motivo());
    }

    @Test
    void criacaoMataMataEAvancoAutomaticoNotificamSemDuplicar() {
        var c=iniciar(4,TipoFormato.MISTA);
        for(var r:c.fases().getFirst().rodadas()) for(var p:r.partidas()) service.registrarResultado(c.id(),p.id(),placar(1,0));
        reset(sse);
        var mata=service.iniciarMataMata(c.id());
        assertEquals(MotivoAtualizacaoCampeonato.MATA_MATA_INICIADO,unico().motivo());
        reset(sse);
        var semis=mata.fases().getLast().rodadas().getFirst().partidas();
        for(var p:semis) service.registrarResultado(c.id(),p.id(),placar(1,0));
        verify(sse,times(2)).publicar(any());
        assertFalse(service.buscar(c.id()).fases().getLast().rodadas().getLast().partidas().isEmpty());
    }

    @Test
    void falhaNoTransporteNaoFalhaOperacaoJaConfirmada() {
        var c=iniciar(4,TipoFormato.LIGA); reset(sse);
        doThrow(new IllegalStateException("transporte indisponivel")).when(sse).publicar(any());
        Long partida=c.fases().getFirst().rodadas().getFirst().partidas().getFirst().id();
        assertDoesNotThrow(()->service.registrarResultado(c.id(),partida,placar(3,0)));
        assertEquals(3,service.buscar(c.id()).classificacaoAtual().tabela().getFirst().golsPro());
        reset(sse);
    }

    @Test
    void rollbackDeLoteEDeExclusaoNaoNotifica() {
        var c=iniciar(4,TipoFormato.LIGA); reset(sse);
        var r=c.fases().getFirst().rodadas().getFirst();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.registrarResultadosSemFinalizar(c.id(),r.id(),r.partidas().stream()
                    .map(p -> new ResultadoPartidaRequest(p.id(),placar(2,0))).toList());
            status.setRollbackOnly();
        });
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.deletar(c.id()); status.setRollbackOnly();
        });
        verifyNoInteractions(sse);
        assertTrue(service.buscar(c.id()).fases().getFirst().rodadas().getFirst().partidas().stream().allMatch(p -> p.resultado()==null));
    }

    @Test
    void transacaoIndependenteNaoMisturaPendenciasDaExterna() {
        var um=iniciar(4,TipoFormato.LIGA); var dois=iniciar(4,TipoFormato.LIGA); reset(sse);
        var r=um.fases().getFirst().rodadas().getFirst();
        var interna=new TransactionTemplate(transactionManager);
        interna.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.registrarResultado(um.id(),r.partidas().getFirst().id(),placar(1,0));
            interna.executeWithoutResult(novo -> {
                service.registrarResultado(dois.id(),dois.fases().getFirst().rodadas().getFirst().partidas().getFirst().id(),placar(2,0));
                verifyNoInteractions(sse);
            });
            assertEquals(dois.id(),unico().campeonatoId());
            service.registrarResultado(um.id(),r.partidas().getLast().id(),placar(1,0));
        });
        var captor=ArgumentCaptor.forClass(EventoCampeonatoResponse.class);
        verify(sse,times(2)).publicar(captor.capture());
        assertEquals(List.of(dois.id(),um.id()),captor.getAllValues().stream().map(EventoCampeonatoResponse::campeonatoId).toList());
    }

    @Test
    void substituicaoDeClassificadoNotificaJuntoDaCriacaoDaChave() {
        var c=iniciar(6,TipoFormato.MISTA);
        for(var r:c.fases().getFirst().rodadas()) for(var p:r.partidas()) service.registrarResultado(c.id(),p.id(),placar(1,0));
        Long lider=service.classificados(c.id(),4).getFirst().id();
        service.desistirParticipante(c.id(),lider);
        var substituto=service.vagasMataMata(c.id()).getFirst().candidatos().getFirst().id();
        reset(sse);
        var atualizado=service.iniciarMataMata(c.id(),new IniciarMataMataRequest(List.of(new IniciarMataMataRequest.Substituicao(lider,substituto))));
        assertEquals(substituto,atualizado.substituicoesMataMata().get(lider));
        assertEquals(MotivoAtualizacaoCampeonato.MATA_MATA_INICIADO,unico().motivo());
    }

    private EventoCampeonatoResponse unico() {
        var captor=ArgumentCaptor.forClass(EventoCampeonatoResponse.class);
        verify(sse,times(1)).publicar(captor.capture());
        return captor.getValue();
    }
    private CampeonatoResponse iniciar(int total,TipoFormato formato) {
        var c=criar(total);
        return service.iniciar(c.id(),new IniciarCampeonatoRequest(formato,false,null,null,formato==TipoFormato.MISTA ? 4:null));
    }
    private CampeonatoResponse criar(int total) {
        var org=organizacoes.criar(new OrganizacaoRequest("SSE",true));
        var lista=new ArrayList<ParticipanteRequest>();
        for(int i=0;i<total;i++) {
            var j=jogadores.criar(new JogadorRequest("Jogador SSE "+i,null));
            var e=equipes.criar(new EquipeRequest("Equipe SSE "+i,"https://example.com/logo.png"));
            lista.add(new ParticipanteRequest(j.id(),e.id()));
        }
        return service.criar(new CampeonatoRequest(org.id(),2020,lista));
    }
    private ResultadoRequest placar(int a,int b) {return new ResultadoRequest(a,b,null,null,false);}
}
