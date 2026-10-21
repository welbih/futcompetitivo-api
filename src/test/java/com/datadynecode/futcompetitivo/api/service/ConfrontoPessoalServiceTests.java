package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraConfrontoPessoalException;
import com.datadynecode.futcompetitivo.api.repository.CampeonatoRepository;
import com.datadynecode.futcompetitivo.api.repository.EquipeRepository;
import com.datadynecode.futcompetitivo.api.repository.ParticipanteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConfrontoPessoalServiceTests {
    @Autowired ConfrontoPessoalService service;
    @Autowired JogadorService jogadores;
    @Autowired CampeonatoRepository campeonatos;
    @Autowired EquipeRepository equipes;
    @Autowired EquipeService equipeService;
    @Autowired ParticipanteRepository participantes;
    @Autowired EntityManager em;

    @Test
    void criaDoisParticipantesPropriosComJogadorEEquipeSemCampeonato() {
        long campeonatosAntes = campeonatos.count(), equipesAntes = equipes.count(), participantesAntes = participantes.count();
        var confronto = criar(TipoSeriePessoal.MELHOR_DE_TRES, null);
        assertEquals(3, confronto.partidas().size());
        assertEquals(StatusConfrontoPessoal.EM_ANDAMENTO, confronto.status());
        assertFalse(confronto.encerrarAntecipadamente());
        assertNull(confronto.vencedor());
        assertEquals(0, confronto.vitoriasParticipanteUm());
        assertEquals(0, confronto.vitoriasParticipanteDois());
        assertEquals(campeonatosAntes, campeonatos.count());
        assertEquals(equipesAntes + 2, equipes.count());
        assertEquals(participantesAntes + 2, participantes.count());
        assertTrue(service.listar().stream().anyMatch(c -> c.id().equals(confronto.id())));
        for (int i = 0; i < confronto.partidas().size(); i++) {
            var partida = confronto.partidas().get(i);
            assertEquals(i + 1, partida.numero());
            assertEquals(i % 2 == 0 ? confronto.participanteUm().id() : confronto.participanteDois().id(), partida.mandante().id());
            assertEquals(i % 2 == 0 ? confronto.participanteDois().id() : confronto.participanteUm().id(), partida.visitante().id());
        }
    }

    @ParameterizedTest
    @CsvSource({"MELHOR_DE_TRES,true", "MELHOR_DE_TRES,false", "MELHOR_DE_CINCO,true", "MELHOR_DE_CINCO,false"})
    void simulaTodasAsCombinacoesDeVitoriasDosDoisJogadores(TipoSeriePessoal tipo, boolean antecipar) {
        Long um = jogadores.criar(new JogadorRequest("Um", null)).id();
        Long dois = jogadores.criar(new JogadorRequest("Dois", null)).id();
        int total = tipo.getQuantidadePartidas();
        for (int combinacao = 0; combinacao < (1 << total); combinacao++) {
            var confronto = service.criar(new ConfrontoPessoalRequest(selecao(um), selecao(dois), tipo, antecipar));
            int vitoriasUm = 0, vitoriasDois = 0, jogados = 0;
            for (int jogo = 0; jogo < total; jogo++) {
                boolean venceUm = (combinacao & (1 << jogo)) != 0;
                var partida = confronto.partidas().get(jogo);
                confronto = service.registrarResultado(confronto.id(), partida.id(),
                        placar(venceUm == partida.mandante().jogador().id().equals(um)));
                if (venceUm) vitoriasUm++; else vitoriasDois++;
                jogados++;
                assertEquals(vitoriasUm, confronto.vitoriasParticipanteUm());
                assertEquals(vitoriasDois, confronto.vitoriasParticipanteDois());
                if (confronto.status() == StatusConfrontoPessoal.FINALIZADO) break;
                assertNull(confronto.vencedor());
            }
            confronto = recarregar(confronto.id());
            assertEquals(StatusConfrontoPessoal.FINALIZADO, confronto.status());
            assertEquals(vitoriasUm > vitoriasDois ? um : dois, confronto.vencedor().jogador().id());
            if (antecipar) assertEquals(tipo.getVitoriasNecessarias(), Math.max(vitoriasUm, vitoriasDois));
            assertEquals(total - jogados, confronto.partidas().stream().filter(p -> p.status() == StatusPartida.CANCELADA).count());
            assertTrue(confronto.partidas().stream().filter(p -> p.status() == StatusPartida.CANCELADA)
                    .allMatch(p -> p.resultado() == null));
            if (!antecipar) assertEquals(total, jogados);
        }
    }

    @ParameterizedTest
    @CsvSource({"MELHOR_DE_TRES,2", "MELHOR_DE_CINCO,3"})
    void encerramentoManualCancelaSomenteJogosRestantes(TipoSeriePessoal tipo, int limite) {
        var confronto = criar(tipo, false);
        assertThrows(RegraConfrontoPessoalException.class, () -> service.encerrar(confronto.id()));
        for (int i = 0; i < limite; i++)
            service.registrarResultado(confronto.id(), confronto.partidas().get(i).id(), placar(i % 2 == 0));
        assertNull(service.buscar(confronto.id()).vencedor());
        var encerrado = service.encerrar(confronto.id());
        assertEquals(StatusConfrontoPessoal.FINALIZADO, encerrado.status());
        assertTrue(encerrado.encerrarAntecipadamente());
        assertEquals(confronto.participanteUm().id(), encerrado.vencedor().id());
        assertEquals(encerrado, service.encerrar(confronto.id()));
        assertThrows(RegraConfrontoPessoalException.class, () -> service.registrarResultado(confronto.id(),
                confronto.partidas().getLast().id(), placar(true)));
    }

    @Test
    void corrigeResultadoReabreSerieEAtualizaVencedor() {
        var confronto = criar(TipoSeriePessoal.MELHOR_DE_TRES, true);
        service.registrarResultado(confronto.id(), confronto.partidas().get(0).id(), placar(true));
        service.registrarResultado(confronto.id(), confronto.partidas().get(1).id(), placar(false));
        assertEquals(StatusConfrontoPessoal.FINALIZADO, recarregar(confronto.id()).status());
        service.registrarResultado(confronto.id(), confronto.partidas().get(1).id(), placar(true));
        var reaberto = recarregar(confronto.id());
        assertEquals(StatusConfrontoPessoal.EM_ANDAMENTO, reaberto.status());
        assertNull(reaberto.vencedor());
        assertEquals(StatusPartida.AGENDADA, reaberto.partidas().getLast().status());
        service.registrarResultado(confronto.id(), confronto.partidas().getLast().id(), placar(false));
        assertEquals(confronto.participanteDois().id(), recarregar(confronto.id()).vencedor().id());
        service.registrarResultado(confronto.id(), confronto.partidas().getLast().id(), placar(true));
        assertEquals(confronto.participanteUm().id(), recarregar(confronto.id()).vencedor().id());
    }

    @Test
    void empateAceitaPenaltisOpcionaisEVitoriasNaoDependemDoAgregadoDeGols() {
        var confronto = criar(TipoSeriePessoal.MELHOR_DE_TRES, true);
        Long primeira = confronto.partidas().getFirst().id();
        assertDoesNotThrow(() -> service.registrarResultado(confronto.id(), primeira, new ResultadoRequest(0, 0, null, null, false)));
        assertThrows(RegraConfrontoPessoalException.class, () -> service.registrarResultado(confronto.id(), primeira,
                new ResultadoRequest(0, 0, 4, 4, false)));
        assertThrows(RegraConfrontoPessoalException.class, () -> service.registrarResultado(confronto.id(), primeira,
                new ResultadoRequest(0, 0, -1, 4, false)));
        assertThrows(RegraConfrontoPessoalException.class, () -> service.registrarResultado(confronto.id(), primeira,
                new ResultadoRequest(1, 0, 5, 4, false)));
        assertThrows(RegraConfrontoPessoalException.class, () -> service.registrarResultado(confronto.id(), primeira,
                new ResultadoRequest(1, 0, null, null, true)));
        service.registrarResultado(confronto.id(), primeira, new ResultadoRequest(0, 0, 5, 4, false));
        service.registrarResultado(confronto.id(), confronto.partidas().get(1).id(), new ResultadoRequest(10, 0, null, null, false));
        service.registrarResultado(confronto.id(), confronto.partidas().getLast().id(), placar(true));
        var salvo = recarregar(confronto.id());
        assertEquals(2, salvo.vitoriasParticipanteUm());
        assertEquals(1, salvo.vitoriasParticipanteDois());
        assertEquals(confronto.participanteUm().id(), salvo.vencedor().id());
    }

    @Test
    void criaParticipantesExclusivosPorX1EPreservaJogadorEEquipe() {
        Long um = jogadores.criar(new JogadorRequest("Um", null)).id();
        Long dois = jogadores.criar(new JogadorRequest("Dois", null)).id();
        var selecaoUm = selecao(um);
        var selecaoDois = selecao(dois);
        var request = new ConfrontoPessoalRequest(selecaoUm, selecaoDois, TipoSeriePessoal.MELHOR_DE_TRES, true);
        var primeiro = service.criar(request);
        var segundo = recarregar(service.criar(request).id());
        assertNotEquals(primeiro.participanteUm().id(), segundo.participanteUm().id());
        assertNotEquals(primeiro.participanteDois().id(), segundo.participanteDois().id());
        assertEquals(um, segundo.participanteUm().jogador().id());
        assertEquals(selecaoUm.equipeId(), segundo.participanteUm().equipe().id());
        assertEquals(selecaoDois.equipeId(), segundo.participanteDois().equipe().id());
        assertEquals(segundo.participanteUm().id(), segundo.partidas().getFirst().mandante().id());
    }

    @Test
    void rejeitaEquipeInexistenteOuInativa() {
        Long um = jogadores.criar(new JogadorRequest("Um", null)).id();
        Long dois = jogadores.criar(new JogadorRequest("Dois", null)).id();
        var selecaoUm = selecao(um);
        assertThrows(ResponseStatusException.class, () -> service.criar(new ConfrontoPessoalRequest(selecaoUm,
                new ParticipanteRequest(dois, Long.MAX_VALUE), TipoSeriePessoal.MELHOR_DE_TRES, true)));
        var selecaoDois = selecao(dois);
        equipeService.desativar(selecaoDois.equipeId());
        assertThrows(ResponseStatusException.class, () -> service.criar(new ConfrontoPessoalRequest(selecaoUm,
                selecaoDois, TipoSeriePessoal.MELHOR_DE_TRES, true)));
    }

    @Test
    void rejeitaMesmoJogadorInativosIdsInexistentesEPartidaDeOutroConfronto() {
        var confronto = criar(TipoSeriePessoal.MELHOR_DE_TRES, false);
        Long um = confronto.participanteUm().jogador().id(), dois = confronto.participanteDois().jogador().id();
        assertThrows(RegraConfrontoPessoalException.class, () -> service.criar(new ConfrontoPessoalRequest(selecao(um), selecao(um), TipoSeriePessoal.MELHOR_DE_TRES, false)));
        assertThrows(ResponseStatusException.class, () -> service.criar(new ConfrontoPessoalRequest(selecao(um), selecao(Long.MAX_VALUE), TipoSeriePessoal.MELHOR_DE_TRES, false)));
        var outro = criar(TipoSeriePessoal.MELHOR_DE_CINCO, false);
        assertThrows(ResponseStatusException.class, () -> service.registrarResultado(confronto.id(), outro.partidas().getFirst().id(), placar(true)));
        assertThrows(ResponseStatusException.class, () -> service.buscar(Long.MAX_VALUE));
        assertThrows(RegraConfrontoPessoalException.class, () -> service.registrarResultado(confronto.id(), confronto.partidas().get(1).id(), placar(true)));
        jogadores.desativar(dois);
        assertThrows(ResponseStatusException.class, () -> service.criar(new ConfrontoPessoalRequest(selecao(um), selecao(dois), TipoSeriePessoal.MELHOR_DE_TRES, false)));
        assertEquals(dois, recarregar(confronto.id()).participanteDois().jogador().id());
    }

    @Test
    void empatesSemPenaltisNaoContamVitoriaEPermitemSerieSemVencedor() {
        var confronto = criar(TipoSeriePessoal.MELHOR_DE_TRES, true);
        for (var partida : confronto.partidas()) {
            service.registrarResultado(confronto.id(), partida.id(), new ResultadoRequest(1, 1, null, null, false));
        }
        var salvo = recarregar(confronto.id());
        assertEquals(StatusConfrontoPessoal.FINALIZADO, salvo.status());
        assertEquals(0, salvo.vitoriasParticipanteUm());
        assertEquals(0, salvo.vitoriasParticipanteDois());
        assertNull(salvo.vencedor());
        assertTrue(salvo.partidas().stream().allMatch(p -> p.status() == StatusPartida.FINALIZADA));
        var primeira = salvo.partidas().getFirst();
        assertThrows(RegraConfrontoPessoalException.class, () -> service.registrarResultado(salvo.id(), primeira.id(),
                new ResultadoRequest(1, 1, 3, null, false)));
        var editado = service.registrarResultado(salvo.id(), primeira.id(), new ResultadoRequest(1, 1, 4, 3, false));
        assertEquals(1, editado.vitoriasParticipanteUm());
        assertEquals(editado.participanteUm().id(), editado.vencedor().id());
        var empatado = service.registrarResultado(salvo.id(), primeira.id(), new ResultadoRequest(1, 1, null, null, false));
        assertNull(empatado.vencedor());
        assertEquals(0, empatado.vitoriasParticipanteUm());
    }
    private ConfrontoPessoalResponse criar(TipoSeriePessoal tipo, Boolean antecipar) {
        Long um = jogadores.criar(new JogadorRequest("Jogador um", null)).id();
        Long dois = jogadores.criar(new JogadorRequest("Jogador dois", null)).id();
        return recarregar(service.criar(new ConfrontoPessoalRequest(selecao(um), selecao(dois), tipo, antecipar)).id());
    }

    private ParticipanteRequest selecao(Long jogadorId) {
        var equipe = equipeService.criar(new EquipeRequest("Equipe " + jogadorId, "https://example.com/logo.png"));
        return new ParticipanteRequest(jogadorId, equipe.id());
    }

    private ConfrontoPessoalResponse recarregar(Long id) { em.flush(); em.clear(); return service.buscar(id); }
    private ResultadoRequest placar(boolean mandante) { return new ResultadoRequest(mandante ? 1 : 0, mandante ? 0 : 1, null, null, false); }
}


