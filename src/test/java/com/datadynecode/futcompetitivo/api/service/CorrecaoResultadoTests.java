package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.repository.CampeonatoRepository;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CorrecaoResultadoTests {
    @Autowired private CampeonatoService service;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private ParticipanteService participantes;
    @Autowired private CampeonatoRepository repository;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(value = TipoFormato.class, names = {"LIGA", "LIGA_PARCIAL", "SWISS"})
    void corrigeCampeonatoFinalizadoRecalculandoHistoricoSemDuplicarEstatisticas(TipoFormato formato) {
        var campeonato = iniciar(formato);
        Long id = campeonato.id();
        while (campeonato.status() != CampeonatoStatus.FINALIZADO) {
            var rodada = campeonato.fases().getFirst().rodadas().stream()
                    .filter(r -> r.status() != RodadaStatus.FINALIZADA).findFirst();
            RodadaResponse proxima = rodada.orElseGet(() -> service.gerarProximaRodadaSwiss(id));
            proxima.partidas().forEach(p -> service.registrarResultado(id, p.id(), placar(1, 0)));
            recarregar();
            campeonato = service.buscar(id);
        }
        var primeiraClassificacao = campeonato.classificacaoPorRodada().getFirst();
        var agendaOriginal = campeonato.fases().getFirst().rodadas();
        var corrigida = agendaOriginal.get(1).partidas().getFirst();

        service.registrarResultado(id, corrigida.id(), placar(0, 2));
        recarregar();
        var salvo = service.buscar(id);

        assertEquals(CampeonatoStatus.FINALIZADO, salvo.status());
        assertEquals(primeiraClassificacao, salvo.classificacaoPorRodada().getFirst());
        assertEquals(agendaOriginal.size(), salvo.classificacaoPorRodada().size());
        for (int i = 0; i < agendaOriginal.size(); i++) {
            var antes = agendaOriginal.get(i);
            var depois = salvo.fases().getFirst().rodadas().get(i);
            assertEquals(antes.id(), depois.id());
            assertEquals(antes.folgaParticipanteId(), depois.folgaParticipanteId());
            for (int j = 0; j < antes.partidas().size(); j++) {
                assertEquals(antes.partidas().get(j).id(), depois.partidas().get(j).id());
                assertEquals(antes.partidas().get(j).mandante(), depois.partidas().get(j).mandante());
                assertEquals(antes.partidas().get(j).visitante(), depois.partidas().get(j).visitante());
            }
            var classificacao = salvo.classificacaoPorRodada().get(i);
            var original = campeonato.classificacaoPorRodada().get(i);
            for (var linha : classificacao.tabela()) {
                var antiga = original.tabela().stream()
                        .filter(c -> c.participante().id().equals(linha.participante().id())).findFirst().orElseThrow();
                boolean mandanteCorrigido = i >= 1 && linha.participante().id().equals(corrigida.mandante().id());
                boolean visitanteCorrigido = i >= 1 && linha.participante().id().equals(corrigida.visitante().id());
                int deltaPontos = mandanteCorrigido ? -3 : visitanteCorrigido ? 3 : 0;
                assertEquals(antiga.jogos(), linha.jogos());
                assertEquals(antiga.pontos() + deltaPontos, linha.pontos());
                assertEquals(antiga.vitorias() + (mandanteCorrigido ? -1 : visitanteCorrigido ? 1 : 0), linha.vitorias());
                assertEquals(antiga.derrotas() + (mandanteCorrigido ? 1 : visitanteCorrigido ? -1 : 0), linha.derrotas());
                assertEquals(antiga.golsPro() + (mandanteCorrigido ? -1 : visitanteCorrigido ? 2 : 0), linha.golsPro());
                assertEquals(antiga.golsContra() + (mandanteCorrigido ? 2 : visitanteCorrigido ? -1 : 0), linha.golsContra());
                assertEquals(linha.golsPro() - linha.golsContra(), linha.saldoGols());
                if (i > 0) {
                    int posicaoAnterior = salvo.classificacaoPorRodada().get(i - 1).tabela().stream()
                            .filter(c -> c.participante().id().equals(linha.participante().id()))
                            .findFirst().orElseThrow().posicaoAtual();
                    assertEquals(posicaoAnterior, linha.posicaoAnterior());
                    assertEquals(Math.abs(posicaoAnterior - linha.posicaoAtual()), linha.variacao());
                }
            }
        }
        assertEquals(salvo, service.registrarResultado(id, corrigida.id(), placar(0, 2)),
                "Reenviar o placar deve ser idempotente, sem refazer sorteios.");
        recarregar();
        assertEquals(salvo, service.buscar(id));
        assertEquals(agendaOriginal.size(), repository.findById(id).orElseThrow().getClassificacaoPorRodada().size());
    }

    @Test
    void corrigePlacarEmRodadaAbertaSemClassificarAntesDeTodasAsPartidasTerminarem() {
        var campeonato = iniciar(TipoFormato.LIGA);
        var rodada = campeonato.fases().getFirst().rodadas().getFirst();
        var primeira = rodada.partidas().getFirst();
        service.registrarResultado(campeonato.id(), primeira.id(), placar(1, 0));
        service.registrarResultado(campeonato.id(), primeira.id(), placar(2, 2));
        recarregar();
        assertTrue(service.buscar(campeonato.id()).classificacaoPorRodada().isEmpty());
        rodada.partidas().stream().skip(1).forEach(p ->
                service.registrarResultado(campeonato.id(), p.id(), placar(0, 0)));
        recarregar();
        var tabela = service.classificacao(campeonato.id()).tabela();
        assertTrue(tabela.stream().allMatch(c -> c.jogos() == 1 && c.pontos() == 1 && c.empates() == 1));
        assertEquals(1, service.buscar(campeonato.id()).classificacaoPorRodada().size());
    }

    @Test
    void correcaoAntesDaProximaRodadaSwissNaoCriaRodadasAdicionais() {
        var campeonato = iniciar(TipoFormato.SWISS);
        var rodada = campeonato.fases().getFirst().rodadas().getFirst();
        rodada.partidas().forEach(p -> service.registrarResultado(campeonato.id(), p.id(), placar(1, 0)));
        var primeira = rodada.partidas().getFirst();
        service.registrarResultado(campeonato.id(), primeira.id(), placar(0, 3));
        recarregar();
        var salvo = service.buscar(campeonato.id());
        assertEquals(CampeonatoStatus.EM_ANDAMENTO, salvo.status());
        assertEquals(1, salvo.fases().getFirst().rodadas().size());
        assertEquals(1, salvo.classificacaoPorRodada().size());
        var vencedor = service.classificacao(campeonato.id()).tabela().stream()
                .filter(c -> c.participante().id().equals(primeira.visitante().id())).findFirst().orElseThrow();
        assertEquals(3, vencedor.pontos());
        assertEquals(1, vencedor.jogos());
        assertNotNull(service.gerarProximaRodadaSwiss(campeonato.id()).id());
    }

    private CampeonatoResponse iniciar(TipoFormato formato) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador " + i, null));
            var equipe = equipes.criar(new EquipeRequest("Equipe " + i, "https://example.com/logo.png"));
            ids.add(participantes.criar(new ParticipanteRequest(jogador.id(), equipe.id())).id());
        }
        var campeonato = service.criar(new CampeonatoRequest("Correcao", 2026, ids));
        return service.iniciar(campeonato.id(), new IniciarCampeonatoRequest(formato, false,
                formato == TipoFormato.LIGA_PARCIAL ? 2 : null, formato == TipoFormato.SWISS ? 3 : null));
    }

    private ResultadoRequest placar(int casa, int fora) {
        return new ResultadoRequest(casa, fora, null, null, false);
    }

    private void recarregar() { entityManager.flush(); entityManager.clear(); }
}
