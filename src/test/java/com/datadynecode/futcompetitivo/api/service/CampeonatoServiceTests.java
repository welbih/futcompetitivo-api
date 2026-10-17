package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import com.datadynecode.futcompetitivo.api.repository.CampeonatoRepository;
import com.datadynecode.futcompetitivo.api.service.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampeonatoServiceTests {
    @Autowired private CampeonatoService service;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private ParticipanteService participantes;
    @Autowired private CampeonatoRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void persisteCampeonatoComParticipantesExistentesEPermiteAtualizarAntesDeIniciar() {
        List<Long> ids = criarParticipantes(3);
        var criado = service.criar(new CampeonatoRequest("Liga", 2026, ids));
        service.atualizar(criado.id(), new CampeonatoRequest("Liga Local", 2027, ids.reversed()));
        recarregar();
        var salvo = service.buscar(criado.id());
        assertEquals("Liga Local", salvo.nome());
        assertEquals(2027, salvo.edicao());
        assertEquals(ids.reversed(), salvo.participantes().stream().map(ParticipanteResponse::id).toList());
        assertEquals(CampeonatoStatus.CRIADO, salvo.status());
        assertTrue(service.listar().stream().anyMatch(c -> c.id().equals(criado.id())));
    }

    @Test
    void geraLigaComReturnoEFinalizaSemAlterarClassificacoesAnteriores() {
        List<Long> ids = criarParticipantes(4);
        var criado = service.criar(new CampeonatoRequest("Liga", 2026, ids));
        var iniciado = service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.LIGA, true, null, null));
        recarregar();
        var rodadas = service.buscar(criado.id()).fases().getFirst().rodadas();
        assertEquals(6, rodadas.size());
        assertEquals(12, rodadas.stream().mapToInt(r -> r.partidas().size()).sum());
        assertEquals(iniciado.status(), CampeonatoStatus.EM_ANDAMENTO);
        assertThrows(RegraCampeonatoException.class, () -> service.iniciar(criado.id(),
                new IniciarCampeonatoRequest(TipoFormato.LIGA, false, null, null)));
        assertThrows(RegraCampeonatoException.class, () -> service.atualizar(criado.id(),
                new CampeonatoRequest("Outro", 2026, ids)));

        var partidaFutura = rodadas.get(1).partidas().getFirst();
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(criado.id(),
                partidaFutura.id(), placar()));
        for (RodadaResponse rodada : rodadas) {
            assertEquals(4, rodada.partidas().stream().flatMap(p ->
                    java.util.stream.Stream.of(p.mandante().id(), p.visitante().id())).distinct().count());
            for (PartidaResponse partida : rodada.partidas())
                service.registrarResultado(criado.id(), partida.id(), placar());
        }
        recarregar();
        var finalizado = service.buscar(criado.id());
        assertEquals(CampeonatoStatus.FINALIZADO, finalizado.status());
        assertEquals(6, finalizado.classificacaoPorRodada().size());
        assertTrue(finalizado.classificacaoPorRodada().getFirst().tabela().stream().allMatch(c -> c.jogos() == 1));
        assertTrue(service.classificacao(criado.id()).tabela().stream().allMatch(c -> c.jogos() == 6));
        assertEquals(2, service.classificados(criado.id(), 2).size());
        assertEquals(finalizado, service.registrarResultado(criado.id(),
                rodadas.getFirst().partidas().getFirst().id(), placar()));
    }

    @Test
    void ligaImparPreservaFolgasELigaParcialRespeitaQuantidadeDeJogos() {
        var impar = service.criar(new CampeonatoRequest("Impar", 2026, criarParticipantes(3)));
        var agenda = service.iniciar(impar.id(), new IniciarCampeonatoRequest(TipoFormato.LIGA, false, null, null));
        assertEquals(3, agenda.fases().getFirst().rodadas().size());
        assertEquals(3, agenda.fases().getFirst().rodadas().stream()
                .map(RodadaResponse::folgaParticipanteId).distinct().count());
        List<Long> ids = criarParticipantes(6);
        var parcial = service.criar(new CampeonatoRequest("Parcial", 2026, ids));
        var gerado = service.iniciar(parcial.id(), new IniciarCampeonatoRequest(TipoFormato.LIGA_PARCIAL, false, 2, null));
        recarregar();
        List<PartidaResponse> partidas = service.buscar(gerado.id()).fases().getFirst().rodadas().stream()
                .flatMap(r -> r.partidas().stream()).toList();
        assertEquals(6, partidas.size());
        for (Long id : ids) assertEquals(2, partidas.stream()
                .filter(p -> p.mandante().id().equals(id) || p.visitante().id().equals(id)).count());
        assertEquals(6, partidas.stream().map(p -> Math.min(p.mandante().id(), p.visitante().id())
                + ":" + Math.max(p.mandante().id(), p.visitante().id())).distinct().count());
    }

    @Test
    void swissRetomaDoBancoEvitaRevancheEAlternaFolga() {
        var criado = service.criar(new CampeonatoRequest("Swiss", 2026, criarParticipantes(5)));
        var iniciado = service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.SWISS, false, null, 2));
        RodadaResponse primeira = iniciado.fases().getFirst().rodadas().getFirst();
        assertThrows(RegraCampeonatoException.class, () -> service.gerarProximaRodadaSwiss(criado.id()));
        primeira.partidas().forEach(p -> service.registrarResultado(criado.id(), p.id(), placar()));
        recarregar();
        RodadaResponse segunda = service.gerarProximaRodadaSwiss(criado.id());
        assertNotEquals(primeira.folgaParticipanteId(), segunda.folgaParticipanteId());
        for (var a : primeira.partidas()) for (var b : segunda.partidas())
            assertNotEquals(Set.of(a.mandante().id(), a.visitante().id()), Set.of(b.mandante().id(), b.visitante().id()));
        segunda.partidas().forEach(p -> service.registrarResultado(criado.id(), p.id(), placar()));
        recarregar();
        assertEquals(CampeonatoStatus.FINALIZADO, service.buscar(criado.id()).status());
    }

    @Test
    void rejeitaParticipantesDuplicadosInativosEInexistentes() {
        List<Long> ids = criarParticipantes(3);
        assertThrows(RegraCampeonatoException.class, () -> service.criar(
                new CampeonatoRequest("Duplicado", 2026, List.of(ids.getFirst(), ids.getFirst()))));
        assertThrows(ResponseStatusException.class, () -> service.criar(
                new CampeonatoRequest("Inexistente", 2026, List.of(Long.MAX_VALUE))));
        var participante = participantes.buscar(ids.getFirst());
        equipes.desativar(participante.equipe().id());
        assertThrows(RegraCampeonatoException.class, () -> service.criar(new CampeonatoRequest("Inativo", 2026, ids)));
        assertThrows(ResponseStatusException.class, () -> service.buscar(Long.MAX_VALUE));
    }

    @Test
    void formatosIncompletosDaOrigemNaoIniciamCampeonato() {
        var criado = service.criar(new CampeonatoRequest("Copa", 2026, criarParticipantes(4)));
        for (TipoFormato formato : List.of(TipoFormato.ELIMINATORIO, TipoFormato.MISTA)) {
            assertThrows(RegraCampeonatoException.class, () -> service.iniciar(criado.id(),
                    new IniciarCampeonatoRequest(formato, false, null, null)));
            assertEquals(CampeonatoStatus.CRIADO, repository.findById(criado.id()).orElseThrow().getStatus());
        }
    }

    private List<Long> criarParticipantes(int quantidade) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador " + i, null));
            var equipe = equipes.criar(new EquipeRequest("Equipe " + i, "https://example.com/logo.png"));
            ids.add(participantes.criar(new ParticipanteRequest(jogador.id(), equipe.id())).id());
        }
        return ids;
    }

    private ResultadoRequest placar() { return new ResultadoRequest(2, 1, null, null, false); }
    private void recarregar() { entityManager.flush(); entityManager.clear(); }
}
