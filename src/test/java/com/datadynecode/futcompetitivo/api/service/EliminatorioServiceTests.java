package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EliminatorioServiceTests {
    @Autowired private CampeonatoService service;
    @Autowired private OrganizacaoService organizacoes;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private EntityManager em;

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 31, 32, 33})
    void ajustaChaveamentoESimulaAteCampeaoSemPerderParticipantes(int quantidade) {
        var campeonato = iniciar(quantidade);
        assertEquals("Final", campeonato.fases().getFirst().rodadas().getLast().etapa().nome());
        assertEquals("FINAL", campeonato.fases().getFirst().confrontos().getLast().etapa().codigo());
        int tamanho = Integer.highestOneBit(quantidade - 1) << 1;
        assertEquals(Integer.numberOfTrailingZeros(tamanho), campeonato.fases().getFirst().rodadas().size());
        var folha = campeonato.fases().getFirst().confrontos().subList(0, tamanho / 2);
        assertEquals(tamanho - quantidade, folha.stream().filter(c -> c.participanteDoisId() == null).count());
        var ids = new HashSet<Long>();
        folha.forEach(c -> {
            assertTrue(ids.add(c.participanteUmId()));
            if (c.participanteDoisId() != null) assertTrue(ids.add(c.participanteDoisId()));
        });
        assertEquals(quantidade, ids.size());
        assertEquals(quantidade - tamanho / 2, campeonato.fases().getFirst().rodadas().getFirst().partidas().size());
        Long id = campeonato.id();
        assertThrows(RegraCampeonatoException.class, () -> service.vencedor(id));
        int disputadas = 0;
        Set<Long> eliminados = new HashSet<>();
        Long campeaoEsperado = null;
        while (campeonato.status() != CampeonatoStatus.FINALIZADO) {
            var rodada = campeonato.fases().getFirst().rodadas().stream()
                    .filter(r -> r.status() == RodadaStatus.EM_ANDAMENTO).findFirst().orElseThrow();
            Set<Long> usados = new HashSet<>();
            for (var partida : rodada.partidas()) {
                assertTrue(usados.add(partida.mandante().id()));
                assertTrue(usados.add(partida.visitante().id()));
                assertFalse(eliminados.contains(partida.mandante().id()));
                assertFalse(eliminados.contains(partida.visitante().id()));
                eliminados.add(partida.visitante().id());
                campeaoEsperado = partida.mandante().id();
                service.registrarResultado(id, partida.id(), new ResultadoRequest(1, 1, 5, 4, false));
                disputadas++;
            }
            recarregar();
            campeonato = service.buscar(id);
            assertNotNull(campeonato.fases().getFirst().rodadas().getFirst().etapa());
            assertTrue(campeonato.fases().getFirst().confrontos().stream()
                    .allMatch(c -> c.partidas().size() <= 1));
        }
        assertEquals(quantidade - 1, disputadas);
        assertEquals(campeaoEsperado, service.vencedor(id).id());
        assertEquals(quantidade - 1, eliminados.size());
        assertTrue(campeonato.classificacaoPorRodada().isEmpty());
        assertTrue(campeonato.fases().getFirst().rodadas().stream().allMatch(r -> r.status() == RodadaStatus.FINALIZADA));
    }

    @Test
    void validaPenaltisEPreservaFluxoManualECorrecaoDoVencedor() {
        var campeonato = iniciar(5);
        var preliminar = campeonato.fases().getFirst().rodadas().getFirst();
        var partida = preliminar.partidas().getFirst();
        for (var invalido : List.of(new ResultadoRequest(0, 0, null, null, false),
                new ResultadoRequest(0, 0, 3, 3, false), new ResultadoRequest(1, 0, 4, 3, false),
                new ResultadoRequest(0, 0, -1, 3, false), new ResultadoRequest(0, 0, 4, null, false))) {
            assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(campeonato.id(), partida.id(), invalido));
        }
        service.registrarResultadosSemFinalizar(campeonato.id(), preliminar.id(),
                List.of(new ResultadoPartidaRequest(partida.id(), new ResultadoRequest(1, 0, null, null, false))));
        recarregar();
        assertEquals(RodadaStatus.EM_ANDAMENTO, service.buscar(campeonato.id()).fases().getFirst().rodadas().getFirst().status());
        var aberto = service.finalizarRodada(campeonato.id(), preliminar.id());
        assertEquals(RodadaStatus.EM_ANDAMENTO, aberto.fases().getFirst().rodadas().get(1).status());
        var corrigido = service.registrarResultado(campeonato.id(), partida.id(), new ResultadoRequest(0, 1, null, null, false));
        var semi = corrigido.fases().getFirst().rodadas().get(1);
        assertTrue(semi.partidas().stream().anyMatch(p -> p.mandante().id().equals(partida.visitante().id())
                || p.visitante().id().equals(partida.visitante().id())));
        var decisiva = semi.partidas().stream().filter(p -> p.mandante().id().equals(partida.visitante().id())
                || p.visitante().id().equals(partida.visitante().id())).findFirst().orElseThrow();
        service.registrarResultado(campeonato.id(), decisiva.id(), new ResultadoRequest(1, 0, null, null, false));
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(campeonato.id(), partida.id(),
                new ResultadoRequest(1, 0, null, null, false)));
        service.registrarResultado(campeonato.id(), partida.id(), new ResultadoRequest(0, 2, null, null, false));
        recarregar();
        assertEquals(2, service.buscar(campeonato.id()).fases().getFirst().rodadas().getFirst().partidas().getFirst().resultado().golsVisitante());
    }

    @Test
    void corrigePenaltisDaFinalSemDuplicarPartidas() {
        var campeonato = iniciar(4);
        var semi = campeonato.fases().getFirst().rodadas().getFirst();
        for (var partida : semi.partidas()) service.registrarResultado(campeonato.id(), partida.id(),
                new ResultadoRequest(1, 0, null, null, false));
        var finalJogo = service.buscar(campeonato.id()).fases().getFirst().rodadas().getLast().partidas().getFirst();
        service.registrarResultado(campeonato.id(), finalJogo.id(), new ResultadoRequest(0, 0, 4, 3, false));
        assertEquals(finalJogo.mandante().id(), service.vencedor(campeonato.id()).id());
        service.registrarResultado(campeonato.id(), finalJogo.id(), new ResultadoRequest(0, 0, 3, 4, false));
        recarregar();
        assertEquals(finalJogo.visitante().id(), service.vencedor(campeonato.id()).id());
        assertEquals(CampeonatoStatus.FINALIZADO, service.buscar(campeonato.id()).status());
    }

    private CampeonatoResponse iniciar(int quantidade) {
        var org = organizacoes.criar(new OrganizacaoRequest("Copa " + quantidade, true));
        List<ParticipanteRequest> participantes = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador " + i, null));
            var equipe = equipes.criar(new EquipeRequest("Equipe " + i, "https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(jogador.id(), equipe.id()));
        }
        var criado = service.criar(new CampeonatoRequest(org.id(), 2026, participantes));
        var iniciado = service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.ELIMINATORIO, false, null, null));
        recarregar();
        return service.buscar(iniciado.id());
    }

    private void recarregar() { em.flush(); em.clear(); }
}
