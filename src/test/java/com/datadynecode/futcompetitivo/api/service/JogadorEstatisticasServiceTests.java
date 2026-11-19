package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.dto.JogadorEstatisticasResponse.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.suporte.HistoricoJogadorFixture;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@ActiveProfiles("test")
@Transactional
class JogadorEstatisticasServiceTests {
    @Autowired EntityManager em;
    @Autowired JogadorEstatisticasService service;
    HistoricoJogadorFixture f;

    @BeforeEach
    void preparar() { f = new HistoricoJogadorFixture(em); }

    @Test
    void retornaTodosOsEmpatesDosQuatroRecordesEFreguesesECarrascos() {
        var seisUm = f.campeonato(f.dois, true, 6, 4);
        var saldoUm = f.campeonato(f.tres, false, 5, 0);
        var empate = f.campeonato(f.dois, true, 2, 2);
        empate.getResultado().setPenaltisMandante(5); empate.getResultado().setPenaltisVisitante(4);
        var derrotaUm = f.campeonato(f.dois, false, 1, 7);
        f.campeonato(f.tres, true, 0, 4);
        var seisDois = f.x1(f.tres, false, 6, 5);
        var saldoDois = f.x1(f.dois, true, 5, 0);
        var empateX1 = f.x1(f.tres, true, 3, 3);
        empateX1.getResultado().setPenaltisMandante(2); empateX1.getResultado().setPenaltisVisitante(4);
        var derrotaDois = f.x1(f.tres, false, 2, 7);
        var derrotaTres = f.x1(f.dois, true, 1, 7);
        var response = buscar(OrigemPartida.TODOS);
        assertEquals(new Resumo(10, 4, 2, 4, 31, 39, -8), response.resumo());
        assertEquals(List.of(seisUm.getId(), seisDois.getId()), ids(response.maioresGoleadasAplicadasPorGols()));
        assertEquals(List.of(saldoUm.getId(), saldoDois.getId()), ids(response.maioresGoleadasAplicadasPorSaldo()));
        assertEquals(List.of(derrotaUm.getId(), derrotaDois.getId(), derrotaTres.getId()), ids(response.maioresGoleadasSofridasPorGols()));
        assertEquals(List.of(derrotaUm.getId(), derrotaTres.getId()), ids(response.maioresGoleadasSofridasPorSaldo()));
        assertEquals(List.of(f.rivalUm.getId(), f.rivalDois.getId()), response.maioresFregueses().stream().map(a -> a.adversario().id()).toList());
        assertEquals(response.maioresFregueses(), response.maioresCarrascos());
        response.maioresFregueses().forEach(a -> {
            assertEquals(5, a.totalConfrontos()); assertEquals(2, a.vitorias()); assertEquals(1, a.empates()); assertEquals(2, a.derrotas());
        });
        var visitante = response.maioresGoleadasAplicadasPorSaldo().getFirst();
        assertEquals("Carla", visitante.adversario().nome());
        assertEquals("C7", visitante.adversario().apelido());
        assertEquals(f.equipeJogador.getId(), visitante.equipeJogador().id());
        assertEquals(f.equipeRival.getLogoUrl(), visitante.equipeAdversario().logoUrl());
        assertEquals("Liga da Vila 2026", visitante.competicao().nome());
        assertEquals(f.campeonato.getId(), visitante.competicao().id());
        assertEquals(f.rodada.getId(), visitante.rodada().id());
        assertNull(visitante.dataPartida());
        var x1 = response.maioresGoleadasAplicadasPorGols().getLast();
        assertEquals(OrigemPartida.X1, x1.origem());
        assertEquals("X1 #" + seisDois.getX1().getId(), x1.competicao().nome());
        assertNull(x1.competicao().organizacao()); assertNull(x1.competicao().edicao());
        assertNull(x1.fase()); assertNull(x1.rodada()); assertEquals(1, x1.numeroPartida());
    }

    @Test
    void filtraOrigemEConsideraTodoHistoricoSemPaginacao() {
        for (int i = 0; i < 35; i++) f.campeonato(f.dois, true, 1, 0);
        f.campeonato(f.dois, false, 9, 0);
        f.x1(f.tres, false, 0, 8);
        var campeonato = buscar(OrigemPartida.CAMPEONATO);
        assertEquals(36, campeonato.resumo().partidasDisputadas());
        assertEquals(9, campeonato.maioresGoleadasAplicadasPorGols().getFirst().golsMarcados());
        assertTrue(campeonato.maioresCarrascos().isEmpty());
        assertEquals(1, buscar(OrigemPartida.X1).resumo().derrotas());
        assertEquals(37, buscar(OrigemPartida.TODOS).resumo().partidasDisputadas());
    }

    @Test
    void penaltisContamComoEmpateEmAmbasAsPerspectivas() {
        var p = f.campeonato(f.dois, false, 2, 2);
        p.getResultado().setPenaltisMandante(4); p.getResultado().setPenaltisVisitante(5);
        var empate = buscar(OrigemPartida.TODOS);
        assertEquals(new Resumo(1, 0, 1, 0, 2, 2, 0), empate.resumo());
        assertTrue(empate.maioresGoleadasAplicadasPorGols().isEmpty());
        assertTrue(empate.maioresGoleadasSofridasPorGols().isEmpty());
        assertTrue(empate.maioresGoleadasAplicadasPorSaldo().isEmpty());
        assertTrue(empate.maioresGoleadasSofridasPorSaldo().isEmpty());
        assertTrue(empate.maioresFregueses().isEmpty()); assertTrue(empate.maioresCarrascos().isEmpty());
        assertEquals(empate.resumo(), service.buscar(f.rivalUm.getId(), OrigemPartida.TODOS).resumo());
    }

    @Test
    void excluiCanceladasAgendadasEmAndamentoSemResultadoEAvancosAutomaticos() {
        for (var status : List.of(StatusPartida.CANCELADA, StatusPartida.AGENDADA, StatusPartida.EM_ANDAMENTO)) {
            f.campeonato(f.dois, true, 99, 0).setStatus(status);
            f.x1(f.tres, false, 99, 0).setStatus(status);
        }
        f.campeonato(f.dois, true, 99, 0).setResultado(null);
        var folga = new Confronto(); folga.setParticipanteUm(f.um);
        f.fase.getConfrontos().add(folga); em.persist(folga); f.rodada.setFolga(f.um);
        var response = buscar(OrigemPartida.TODOS);
        assertEquals(new Resumo(0, 0, 0, 0, 0, 0, 0), response.resumo());
        assertTrue(response.maioresGoleadasAplicadasPorGols().isEmpty());
        assertTrue(response.maioresCarrascos().isEmpty());
    }

    @Test
    void idaEVoltaNaoDuplicamPartidasEPreservamMetadadosDoMataMata() {
        f.fase.setTipo(TipoFase.ELIMINATORIA);
        var confronto = new Confronto(); confronto.setParticipanteUm(f.um); confronto.setParticipanteDois(f.dois);
        confronto.setTipo(TipoConfronto.IDA_E_VOLTA); f.fase.getConfrontos().add(confronto); em.persist(confronto);
        var ida = f.campeonato(f.dois, true, 3, 0);
        var volta = f.campeonato(f.dois, false, 1, 4);
        volta.getResultado().setPenaltisMandante(5); volta.getResultado().setPenaltisVisitante(4);
        confronto.getPartidas().add(ida); confronto.getPartidas().add(volta);
        var response = buscar(OrigemPartida.CAMPEONATO);
        assertEquals(new Resumo(2, 1, 0, 1, 4, 4, 0), response.resumo());
        var aplicada = response.maioresGoleadasAplicadasPorGols().getFirst();
        assertEquals("IDA", aplicada.idaOuVolta()); assertEquals("SEMIFINAL", aplicada.fase().etapa().codigo());
        var sofrida = response.maioresGoleadasSofridasPorGols().getFirst();
        assertEquals("VOLTA", sofrida.idaOuVolta()); assertEquals(3, -sofrida.saldoGols());
        assertEquals(new Penaltis(4, 5), sofrida.penaltis());
    }

    @Test
    void agregaPorJogadorMesmoComParticipantesEEquipesDiferentes() {
        f.campeonato(f.dois, true, 3, 1);
        var equipe = new Equipe(); equipe.setNome("Sem logo"); em.persist(equipe);
        var participante = new Participante(); participante.setJogador(f.jogador); participante.setEquipe(equipe); em.persist(participante);
        var partida = f.x1(f.dois, false, 4, 1);
        partida.setVisitante(participante);
        partida.getX1().setParticipanteUm(participante);
        var response = buscar(OrigemPartida.TODOS);
        assertEquals(2, response.resumo().vitorias());
        assertEquals(2, response.maioresFregueses().getFirst().totalConfrontos());
        assertEquals(equipe.getId(), response.maioresGoleadasAplicadasPorGols().getFirst().equipeJogador().id());
        assertNull(response.maioresGoleadasAplicadasPorGols().getFirst().equipeJogador().logoUrl());
    }

    @Test
    void alteracaoDeResultadoRecalculaEstatisticasSemSnapshotObsoleto() {
        var partida = f.campeonato(f.dois, true, 3, 0);
        Long partidaId = partida.getId();
        assertEquals(1, buscar(OrigemPartida.TODOS).resumo().vitorias());
        var salvo = em.find(Partida.class, partidaId);
        salvo.setResultado(new Resultado(false, 0, 4, null, null));
        var response = buscar(OrigemPartida.TODOS);
        assertEquals(new Resumo(1, 0, 0, 1, 0, 4, -4), response.resumo());
        assertTrue(response.maioresFregueses().isEmpty());
        assertEquals(f.rivalUm.getId(), response.maioresCarrascos().getFirst().adversario().id());
    }

    @Test
    void identificaTerceiroLugarEPreliminar() {
        f.fase.setTipo(TipoFase.ELIMINATORIA);
        var terceiro = new Confronto(); terceiro.setTerceiroLugar(true);
        terceiro.setParticipanteUm(f.um); terceiro.setParticipanteDois(f.dois);
        f.fase.getConfrontos().add(terceiro); em.persist(terceiro);
        terceiro.getPartidas().add(f.campeonato(f.dois, true, 4, 0));
        var folga = new Confronto(); folga.setParticipanteUm(f.tres); f.fase.getConfrontos().add(folga); em.persist(folga);
        var preliminar = new Confronto(); preliminar.setParticipanteUm(f.um); preliminar.setParticipanteDois(f.tres);
        f.fase.getConfrontos().add(preliminar); em.persist(preliminar);
        preliminar.getPartidas().add(f.campeonato(f.tres, true, 4, 0));
        var recordes = buscar(OrigemPartida.TODOS).maioresGoleadasAplicadasPorGols();
        assertEquals(List.of("TERCEIRO_LUGAR", "PRELIMINAR"), recordes.stream().map(p -> p.fase().etapa().codigo()).toList());
        assertTrue(recordes.stream().allMatch(p -> "JOGO_UNICO".equals(p.idaOuVolta())));
    }

    @Test
    void inativosMantemHistoricoEConsultaNaoFazNMaisUm() {
        f.campeonato(f.dois, true, 4, 1); f.x1(f.tres, false, 0, 2);
        f.jogador.setAtivo(false); f.rivalUm.setAtivo(false); f.equipeJogador.setAtivo(false); f.equipeRival.setAtivo(false);
        f.um.setAtivo(false); f.dois.setAtivo(false); f.campeonato.getOrganizacao().setAtivo(false);
        em.flush(); em.clear();
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var response = service.buscar(f.jogador.getId(), OrigemPartida.TODOS);
        assertEquals(new Resumo(2, 1, 0, 1, 4, 3, 1), response.resumo());
        assertFalse(response.jogador().ativo());
        assertFalse(response.maioresGoleadasAplicadasPorGols().getFirst().adversario().ativo());
        assertFalse(response.maioresGoleadasAplicadasPorGols().getFirst().equipeJogador().ativo());
        assertTrue(statistics.getPrepareStatementCount() <= 2, "A consulta deve usar no máximo duas instruções SQL.");
    }

    @Test
    void semHistoricoRetornaZerosEListasVaziasENaoEncontradoRetorna404() {
        var response = buscar(OrigemPartida.TODOS);
        assertEquals(new Resumo(0, 0, 0, 0, 0, 0, 0), response.resumo());
        assertTrue(response.maioresGoleadasAplicadasPorGols().isEmpty());
        assertTrue(response.maioresGoleadasAplicadasPorSaldo().isEmpty());
        assertTrue(response.maioresGoleadasSofridasPorGols().isEmpty());
        assertTrue(response.maioresGoleadasSofridasPorSaldo().isEmpty());
        assertTrue(response.maioresFregueses().isEmpty()); assertTrue(response.maioresCarrascos().isEmpty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.buscar(Long.MAX_VALUE, OrigemPartida.TODOS)).getStatusCode().value());
    }

    private JogadorEstatisticasResponse buscar(OrigemPartida origem) {
        em.flush(); em.clear(); return service.buscar(f.jogador.getId(), origem);
    }
    private List<Long> ids(List<Goleada> partidas) { return partidas.stream().map(Goleada::partidaId).toList(); }
}
