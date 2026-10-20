package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EliminatorioIdaVoltaServiceTests {
    @Autowired private CampeonatoService service;
    @Autowired private OrganizacaoService organizacoes;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private EntityManager em;

    @ParameterizedTest
    @CsvSource({"4,IDA_E_VOLTA", "4,IDA_E_VOLTA_FINAL_UNICA", "5,IDA_E_VOLTA", "5,IDA_E_VOLTA_FINAL_UNICA",
            "8,IDA_E_VOLTA", "8,IDA_E_VOLTA_FINAL_UNICA", "9,IDA_E_VOLTA_FINAL_UNICA"})
    void geraEtapasComMandosInvertidosEAvancaPeloAgregado(int quantidade, FormatoEliminatorio formato) {
        var campeonato = iniciar(quantidade, formato);
        Long id = campeonato.id();
        assertEquals(formato, campeonato.fases().getFirst().formatoEliminatorio());
        var todos = campeonato.fases().getFirst().confrontos();
        assertEquals(formato == FormatoEliminatorio.IDA_E_VOLTA ? TipoConfronto.IDA_E_VOLTA : TipoConfronto.JOGO_UNICO,
                todos.getLast().tipo());
        assertTrue(todos.stream().limit(todos.size() - 1).allMatch(c -> c.tipo() == TipoConfronto.IDA_E_VOLTA));
        int jogos = 0;
        Long campeaoEsperado = null;
        while (campeonato.status() != CampeonatoStatus.FINALIZADO) {
            var rodada = campeonato.fases().getFirst().rodadas().stream().filter(r -> r.status() == RodadaStatus.EM_ANDAMENTO).findFirst().orElseThrow();
            var ids = rodada.partidas().stream().map(PartidaResponse::id).toList();
            var confrontos = campeonato.fases().getFirst().confrontos().stream()
                    .filter(c -> c.partidas().stream().anyMatch(p -> ids.contains(p.id()))).toList();
            for (var confronto : confrontos) {
                var ida = confronto.partidas().getFirst();
                campeaoEsperado = ida.mandante().id();
                if (confronto.tipo() == TipoConfronto.IDA_E_VOLTA) {
                    assertEquals(2, confronto.partidas().size());
                    var volta = confronto.partidas().getLast();
                    assertEquals(ida.mandante().id(), volta.visitante().id());
                    assertEquals(ida.visitante().id(), volta.mandante().id());
                    service.registrarResultado(id, ida.id(), placar(3, 0));
                    recarregar();
                    assertEquals(RodadaStatus.EM_ANDAMENTO, service.buscar(id).fases().getFirst().rodadas().stream()
                            .filter(r -> r.id().equals(rodada.id())).findFirst().orElseThrow().status());
                    service.registrarResultado(id, volta.id(), placar(2, 0));
                    jogos += 2;
                } else {
                    assertEquals(1, confronto.partidas().size());
                    service.registrarResultado(id, ida.id(), new ResultadoRequest(0, 0, 5, 4, false));
                    jogos++;
                }
            }
            recarregar();
            campeonato = service.buscar(id);
        }
        assertEquals(formato == FormatoEliminatorio.IDA_E_VOLTA ? 2 * (quantidade - 1) : 2 * (quantidade - 1) - 1, jogos);
        assertEquals(campeaoEsperado, service.vencedor(id).id());
    }

    @Test
    void validaOrdemDaVoltaEEmpateAgregadoMesmoSemEmpateNoJogo() {
        var campeonato = iniciar(4, FormatoEliminatorio.IDA_E_VOLTA_FINAL_UNICA);
        var confronto = campeonato.fases().getFirst().confrontos().getFirst();
        var ida = confronto.partidas().getFirst();
        var volta = confronto.partidas().getLast();
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(campeonato.id(), volta.id(), placar(1, 0)));
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(campeonato.id(), ida.id(),
                new ResultadoRequest(0, 0, 5, 4, false)));
        service.registrarResultado(campeonato.id(), ida.id(), placar(2, 0));
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(campeonato.id(), volta.id(), placar(2, 0)));
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(campeonato.id(), volta.id(),
                new ResultadoRequest(1, 0, 5, 4, false)));
        service.registrarResultado(campeonato.id(), volta.id(), new ResultadoRequest(2, 0, 5, 4, false));
        var outro = campeonato.fases().getFirst().confrontos().get(1);
        service.registrarResultado(campeonato.id(), outro.partidas().getFirst().id(), placar(0, 0));
        service.registrarResultado(campeonato.id(), outro.partidas().getLast().id(), placar(1, 0));
        recarregar();
        var finalJogo = service.buscar(campeonato.id()).fases().getFirst().rodadas().getLast().partidas().getFirst();
        assertEquals(volta.mandante().id(), finalJogo.mandante().id());
    }

    @Test
    void loteValidaEstadoFinalDasDuasPartidasEAguardaFinalizacaoManual() {
        var campeonato = iniciar(4, FormatoEliminatorio.IDA_E_VOLTA);
        var rodada = campeonato.fases().getFirst().rodadas().getFirst();
        var confronto = campeonato.fases().getFirst().confrontos().getFirst();
        var ida = confronto.partidas().getFirst();
        var volta = confronto.partidas().getLast();
        service.registrarResultadosSemFinalizar(campeonato.id(), rodada.id(), List.of(
                new ResultadoPartidaRequest(volta.id(), new ResultadoRequest(2, 0, 5, 4, false)),
                new ResultadoPartidaRequest(ida.id(), placar(2, 0))));
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(campeonato.id(), ida.id(), placar(3, 0)));
        service.registrarResultadosSemFinalizar(campeonato.id(), rodada.id(), List.of(
                new ResultadoPartidaRequest(ida.id(), placar(3, 0)), new ResultadoPartidaRequest(volta.id(), placar(1, 0))));
        var outro = campeonato.fases().getFirst().confrontos().get(1);
        service.registrarResultadosSemFinalizar(campeonato.id(), rodada.id(), List.of(
                new ResultadoPartidaRequest(outro.partidas().getFirst().id(), placar(1, 0)),
                new ResultadoPartidaRequest(outro.partidas().getLast().id(), placar(0, 1))));
        recarregar();
        var aberto = service.buscar(campeonato.id());
        assertEquals(RodadaStatus.EM_ANDAMENTO, aberto.fases().getFirst().rodadas().getFirst().status());
        assertEquals(RodadaStatus.PENDENTE, aberto.fases().getFirst().rodadas().getLast().status());
        var avancado = service.finalizarRodada(campeonato.id(), rodada.id());
        assertEquals(RodadaStatus.EM_ANDAMENTO, avancado.fases().getFirst().rodadas().getLast().status());
        assertEquals(2, avancado.fases().getFirst().rodadas().getLast().partidas().size());
    }

    @ParameterizedTest
    @EnumSource(value = FormatoEliminatorio.class, names = {"IDA_E_VOLTA", "IDA_E_VOLTA_FINAL_UNICA"})
    void correcaoConsideraVencedorAgregadoEPreservaPartidasPosteriores(FormatoEliminatorio formato) {
        var campeonato = iniciar(4, formato);
        for (var confronto : campeonato.fases().getFirst().confrontos().subList(0, 2)) {
            service.registrarResultado(campeonato.id(), confronto.partidas().getFirst().id(), placar(3, 0));
            service.registrarResultado(campeonato.id(), confronto.partidas().getLast().id(), placar(0, 4));
        }
        recarregar();
        var salvo = service.buscar(campeonato.id());
        var semi = salvo.fases().getFirst().confrontos().getFirst();
        var finalJogo = salvo.fases().getFirst().rodadas().getLast().partidas().getFirst();
        var trocado = service.registrarResultado(campeonato.id(), semi.partidas().getFirst().id(), placar(0, 8));
        var partidasFinais = trocado.fases().getFirst().rodadas().getLast().partidas();
        assertEquals(semi.partidas().getFirst().visitante().id(), partidasFinais.getFirst().mandante().id());
        if (partidasFinais.size() == 2)
            assertEquals(semi.partidas().getFirst().visitante().id(), partidasFinais.getLast().visitante().id());
        service.registrarResultado(campeonato.id(), semi.partidas().getFirst().id(), placar(3, 0));
        service.registrarResultado(campeonato.id(), finalJogo.id(), placar(1, 0));
        service.registrarResultado(campeonato.id(), semi.partidas().getFirst().id(), placar(0, 1));
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(campeonato.id(),
                semi.partidas().getLast().id(), placar(4, 0)));
    }

    @Test
    void finalEmIdaEVoltaPermiteCorrigirApenasOsPenaltis() {
        var campeonato = iniciar(4, FormatoEliminatorio.IDA_E_VOLTA);
        for (var confronto : campeonato.fases().getFirst().confrontos().subList(0, 2)) {
            service.registrarResultado(campeonato.id(), confronto.partidas().getFirst().id(), placar(2, 0));
            service.registrarResultado(campeonato.id(), confronto.partidas().getLast().id(), placar(0, 1));
        }
        recarregar();
        var finalConfronto = service.buscar(campeonato.id()).fases().getFirst().confrontos().getLast();
        var ida = finalConfronto.partidas().getFirst();
        var volta = finalConfronto.partidas().getLast();
        service.registrarResultado(campeonato.id(), ida.id(), placar(0, 0));
        service.registrarResultado(campeonato.id(), volta.id(), new ResultadoRequest(0, 0, 5, 4, false));
        assertEquals(volta.mandante().id(), service.vencedor(campeonato.id()).id());
        service.registrarResultado(campeonato.id(), volta.id(), new ResultadoRequest(0, 0, 4, 5, false));
        recarregar();
        assertEquals(volta.visitante().id(), service.vencedor(campeonato.id()).id());
        assertEquals(CampeonatoStatus.FINALIZADO, service.buscar(campeonato.id()).status());
        assertEquals(2, service.buscar(campeonato.id()).fases().getFirst().confrontos().getLast().partidas().size());
    }

    private CampeonatoResponse iniciar(int quantidade, FormatoEliminatorio formato) {
        var org = organizacoes.criar(new OrganizacaoRequest("Copa ida e volta", true));
        List<ParticipanteRequest> participantes = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador " + i, null));
            var equipe = equipes.criar(new EquipeRequest("Equipe " + i, "https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(jogador.id(), equipe.id()));
        }
        var criado = service.criar(new CampeonatoRequest(org.id(), 2026, participantes));
        service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.ELIMINATORIO, false, null, null, null, formato));
        recarregar();
        return service.buscar(criado.id());
    }

    private ResultadoRequest placar(int casa, int fora) { return new ResultadoRequest(casa, fora, null, null, false); }
    private void recarregar() { em.flush(); em.clear(); }
}
