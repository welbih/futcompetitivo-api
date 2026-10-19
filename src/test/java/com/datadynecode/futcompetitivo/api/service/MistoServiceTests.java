package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MistoServiceTests {
    @Autowired private CampeonatoService service;
    @Autowired private OrganizacaoService organizacoes;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private EntityManager em;

    @ParameterizedTest
    @CsvSource({"4,4,false", "6,4,false", "8,5,false", "10,6,false", "8,8,false", "5,4,true"})
    void simulaLigaEMataMataPreservandoClassificacao(int quantidade, int classificados, boolean returno) {
        var criado = criar(quantidade);
        var campeonato = service.iniciar(criado.id(), new IniciarCampeonatoRequest(
                TipoFormato.MISTA, returno, null, null, classificados));
        recarregar();
        campeonato = service.buscar(criado.id());
        assertEquals(1, campeonato.fases().size());
        assertEquals(classificados, campeonato.fases().getFirst().quantidadeClassificados());
        for (var rodada : campeonato.fases().getFirst().rodadas()) {
            if (rodada.numero().equals(campeonato.fases().getFirst().rodadas().getLast().numero())) {
                for (var partida : rodada.partidas()) service.registrarResultadosSemFinalizar(criado.id(), rodada.id(),
                        List.of(new ResultadoPartidaRequest(partida.id(), placarLiga(partida))));
                assertEquals(1, service.buscar(criado.id()).fases().size());
                assertEquals(CampeonatoStatus.EM_ANDAMENTO, service.buscar(criado.id()).status());
                service.finalizarRodada(criado.id(), rodada.id());
            } else {
                for (var partida : rodada.partidas()) service.registrarResultado(criado.id(), partida.id(), placarLiga(partida));
            }
        }
        recarregar();
        campeonato = service.buscar(criado.id());
        assertEquals(2, campeonato.fases().size());
        assertEquals(CampeonatoStatus.EM_ANDAMENTO, campeonato.status());
        assertEquals(TipoFase.LIGA, campeonato.fases().getFirst().tipo());
        assertEquals(TipoFase.ELIMINATORIA, campeonato.fases().getLast().tipo());
        assertEquals(2, campeonato.fases().getLast().ordem());
        assertEquals(RodadaStatus.EM_ANDAMENTO, campeonato.fases().getLast().rodadas().getFirst().status());
        var tabela = service.classificacao(criado.id());
        var historico = campeonato.classificacaoPorRodada();
        var selecionados = tabela.tabela().stream().limit(classificados).map(c -> c.participante().id()).toList();
        assertTrue(tabela.tabela().stream().allMatch(c -> c.jogos() == (quantidade - 1) * (returno ? 2 : 1)));
        assertEquals(selecionados, service.classificados(criado.id(), classificados).stream().map(ParticipanteResponse::id).toList());
        int vagas = Integer.highestOneBit(classificados - 1) << 1;
        var folhas = campeonato.fases().getLast().confrontos().subList(0, vagas / 2);
        var naChave = new HashSet<Long>();
        folhas.forEach(c -> {
            if (c.participanteUmId() != null) naChave.add(c.participanteUmId());
            if (c.participanteDoisId() != null) naChave.add(c.participanteDoisId());
        });
        assertEquals(new HashSet<>(selecionados), naChave);
        var folgas = folhas.stream().filter(c -> c.participanteDoisId() == null).map(ConfrontoResponse::participanteUmId).toList();
        assertEquals(new HashSet<>(selecionados.subList(0, vagas - classificados)), new HashSet<>(folgas));
        if (classificados == vagas) {
            assertEquals(selecionados.getFirst(), folhas.getFirst().participanteUmId());
            assertEquals(selecionados.getLast(), folhas.getFirst().participanteDoisId());
        }
        int jogosMataMata = 0;
        while (campeonato.status() != CampeonatoStatus.FINALIZADO) {
            var rodada = campeonato.fases().getLast().rodadas().stream()
                    .filter(r -> r.status() == RodadaStatus.EM_ANDAMENTO).findFirst().orElseThrow();
            for (var partida : rodada.partidas()) {
                service.registrarResultado(criado.id(), partida.id(), new ResultadoRequest(1, 1, 5, 4, false));
                jogosMataMata++;
            }
            recarregar();
            campeonato = service.buscar(criado.id());
        }
        assertEquals(classificados - 1, jogosMataMata);
        assertEquals(historico, campeonato.classificacaoPorRodada());
        assertEquals(tabela, service.classificacao(criado.id()));
        assertTrue(selecionados.contains(service.vencedor(criado.id()).id()));
    }

    @Test
    void validaCorteDeClassificados() {
        var campeonato = criar(4);
        for (Integer quantidade : Arrays.asList(null, 0, 1, 2, 3, 5)) {
            assertThrows(RegraCampeonatoException.class, () -> service.iniciar(campeonato.id(),
                    new IniciarCampeonatoRequest(TipoFormato.MISTA, false, null, null, quantidade)));
            assertEquals(CampeonatoStatus.CRIADO, service.buscar(campeonato.id()).status());
        }
    }

    @Test
    void corrigeLigaAntesDoMataMataEImpedeMudancaAposResultadoEliminatorio() {
        var criado = criar(6);
        var campeonato = service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.MISTA, false, null, null, 4));
        for (var rodada : campeonato.fases().getFirst().rodadas())
            for (var partida : rodada.partidas()) service.registrarResultado(criado.id(), partida.id(), placarLiga(partida));
        recarregar();
        var salvo = service.buscar(criado.id());
        var liga = salvo.fases().getFirst();
        var mataMata = salvo.fases().getLast();
        var partidaLiga = liga.rodadas().getLast().partidas().getFirst();
        var novoPlacar = new ResultadoRequest(0, 0, null, null, false);
        var corrigido = service.registrarResultado(criado.id(), partidaLiga.id(), novoPlacar);
        assertEquals(2, corrigido.fases().size());
        assertEquals(mataMata.id(), corrigido.fases().getLast().id());
        var classificados = service.classificados(criado.id(), 4).stream().map(ParticipanteResponse::id).toList();
        assertEquals(classificados.getFirst(), corrigido.fases().getLast().confrontos().getFirst().participanteUmId());
        var jogo = corrigido.fases().getLast().rodadas().getFirst().partidas().getFirst();
        service.registrarResultado(criado.id(), jogo.id(), new ResultadoRequest(1, 0, null, null, false));
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(criado.id(), partidaLiga.id(),
                new ResultadoRequest(3, 0, null, null, false)));
        assertEquals(novoPlacar, service.buscar(criado.id()).fases().getFirst().rodadas().getLast().partidas().getFirst().resultado());
    }

    private ResultadoRequest placarLiga(PartidaResponse partida) {
        return partida.mandante().id() < partida.visitante().id()
                ? new ResultadoRequest(2, 0, null, null, false) : new ResultadoRequest(0, 2, null, null, false);
    }

    private CampeonatoResponse criar(int quantidade) {
        var org = organizacoes.criar(new OrganizacaoRequest("Misto", true));
        List<ParticipanteRequest> participantes = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador " + i, null));
            var equipe = equipes.criar(new EquipeRequest("Equipe " + i, "https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(jogador.id(), equipe.id()));
        }
        return service.criar(new CampeonatoRequest(org.id(), 2026, participantes));
    }

    private void recarregar() { em.flush(); em.clear(); }
}
