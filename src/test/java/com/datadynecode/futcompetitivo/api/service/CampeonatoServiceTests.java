package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import com.datadynecode.futcompetitivo.api.repository.CampeonatoRepository;
import com.datadynecode.futcompetitivo.api.service.*;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CampeonatoServiceTests {
    @Autowired private CampeonatoService service;
    @Autowired private OrganizacaoService organizacoes;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private ParticipanteService participantes;
    @Autowired private CampeonatoRepository repository;
    @Autowired private EntityManager entityManager;

    @ParameterizedTest
    @EnumSource(value = TipoFormato.class, names = {"LIGA", "LIGA_PARCIAL", "SWISS"})
    void registraTodosOsResultadosSemEncerrarEAguardaFinalizacaoManual(TipoFormato formato) {
        var org = organizacoes.criar(new OrganizacaoRequest("Rodadas manuais", true));
        var criado = service.criar(new CampeonatoRequest(org.id(), 2026, selecoes(criarParticipantes(4))));
        var campeonato = service.iniciar(criado.id(), new IniciarCampeonatoRequest(formato, false,
                formato == TipoFormato.LIGA_PARCIAL ? 3 : null, formato == TipoFormato.SWISS ? 2 : null));
        var primeira = campeonato.fases().getFirst().rodadas().getFirst();
        assertThrows(RegraCampeonatoException.class, () -> service.finalizarRodada(criado.id(), primeira.id()));
        var resultados = primeira.partidas().stream().map(p -> new ResultadoPartidaRequest(p.id(), placar())).toList();
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultadosSemFinalizar(criado.id(),
                primeira.id(), List.of(resultados.getFirst(), resultados.getFirst())));
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultadosSemFinalizar(criado.id(),
                primeira.id(), List.of(resultados.getFirst(), new ResultadoPartidaRequest(Long.MAX_VALUE, placar()))));
        service.registrarResultadosSemFinalizar(criado.id(), primeira.id(), resultados.subList(0, 1));
        recarregar();
        var parcial = service.buscar(criado.id()).fases().getFirst().rodadas().getFirst();
        assertEquals(RodadaStatus.EM_ANDAMENTO, parcial.status());
        assertNotNull(parcial.partidas().getFirst().resultado());
        assertNull(parcial.partidas().getLast().resultado());
        assertThrows(RegraCampeonatoException.class, () -> service.finalizarRodada(criado.id(), primeira.id()));
        var correcao = new ResultadoPartidaRequest(resultados.getFirst().partidaId(), new ResultadoRequest(4, 3, null, null, false));
        service.registrarResultadosSemFinalizar(criado.id(), primeira.id(), List.of(correcao));
        service.registrarResultadosSemFinalizar(criado.id(), primeira.id(), resultados.subList(1, resultados.size()));
        recarregar();
        var completa = service.buscar(criado.id()).fases().getFirst().rodadas().getFirst();
        assertEquals(4, completa.partidas().getFirst().resultado().golsMandante());
        assertEquals(3, completa.partidas().getFirst().resultado().golsVisitante());
        assertEquals(RodadaStatus.EM_ANDAMENTO, completa.status());
        assertTrue(service.buscar(criado.id()).classificacaoPorRodada().isEmpty());
        while (campeonato.status() != CampeonatoStatus.FINALIZADO) {
            var rodada = campeonato.fases().getFirst().rodadas().stream()
                    .filter(r -> r.status() == RodadaStatus.EM_ANDAMENTO).findFirst().orElseThrow();
            int classificacoes = campeonato.classificacaoPorRodada().size();
            var placares = rodada.partidas().stream().map(p -> new ResultadoPartidaRequest(p.id(), placar())).toList();
            service.registrarResultadosSemFinalizar(criado.id(), rodada.id(), placares);
            service.registrarResultadosSemFinalizar(criado.id(), rodada.id(), placares);
            recarregar();
            var salvo = service.buscar(criado.id());
            var aberta = salvo.fases().getFirst().rodadas().stream().filter(r -> r.id().equals(rodada.id())).findFirst().orElseThrow();
            assertEquals(RodadaStatus.EM_ANDAMENTO, aberta.status());
            assertEquals(CampeonatoStatus.EM_ANDAMENTO, salvo.status());
            assertEquals(classificacoes, salvo.classificacaoPorRodada().size());
            if (formato == TipoFormato.SWISS) {
                assertThrows(RegraCampeonatoException.class, () -> service.gerarProximaRodadaSwiss(criado.id()));
            } else {
                assertTrue(salvo.fases().getFirst().rodadas().stream().filter(r -> r.numero() > rodada.numero())
                        .allMatch(r -> r.status() == RodadaStatus.PENDENTE));
            }
            campeonato = service.finalizarRodada(criado.id(), rodada.id());
            assertEquals(classificacoes + 1, campeonato.classificacaoPorRodada().size());
            if (formato == TipoFormato.SWISS && campeonato.status() != CampeonatoStatus.FINALIZADO) {
                service.gerarProximaRodadaSwiss(criado.id());
                campeonato = service.buscar(criado.id());
            }
        }
        assertEquals(CampeonatoStatus.FINALIZADO, service.buscar(criado.id()).status());
    }

    @ParameterizedTest
    @EnumSource(value = TipoFormato.class, names = {"LIGA", "LIGA_PARCIAL", "SWISS"})
    void iniciaPrimeiraRodadaEBloqueiaResultadosAteRodadaEntrarEmAndamento(TipoFormato formato) {
        var org = organizacoes.criar(new OrganizacaoRequest("Fluxo de rodadas", true));
        var criado = service.criar(new CampeonatoRequest(org.id(), 2026, selecoes(criarParticipantes(4))));
        var iniciado = service.iniciar(criado.id(), new IniciarCampeonatoRequest(formato, false,
                formato == TipoFormato.LIGA_PARCIAL ? 3 : null, formato == TipoFormato.SWISS ? 2 : null));
        recarregar();
        var rodadas = service.buscar(criado.id()).fases().getFirst().rodadas();
        var primeira = rodadas.getFirst();
        assertEquals(RodadaStatus.EM_ANDAMENTO, primeira.status());
        assertTrue(rodadas.stream().skip(1).allMatch(r -> r.status() == RodadaStatus.PENDENTE));

        var entidade = repository.findById(criado.id()).orElseThrow();
        entidade.getFases().getFirst().getRodadas().getFirst().setStatus(RodadaStatus.PENDENTE);
        var erro = assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(criado.id(),
                primeira.partidas().getFirst().id(), placar()));
        assertEquals("Só é possível adicionar resultado em uma rodada em andamento.", erro.getMessage());
        entidade.getFases().getFirst().getRodadas().getFirst().setStatus(RodadaStatus.EM_ANDAMENTO);

        service.registrarResultado(criado.id(), primeira.partidas().getFirst().id(), placar());
        assertEquals(RodadaStatus.EM_ANDAMENTO, service.buscar(criado.id()).fases().getFirst().rodadas().getFirst().status());
        primeira.partidas().stream().skip(1).forEach(p -> service.registrarResultado(criado.id(), p.id(), placar()));
        recarregar();
        assertEquals(RodadaStatus.FINALIZADA, service.buscar(criado.id()).fases().getFirst().rodadas().getFirst().status());
        if (formato == TipoFormato.SWISS) {
            assertEquals(RodadaStatus.EM_ANDAMENTO, service.gerarProximaRodadaSwiss(criado.id()).status());
        } else {
            var seguintes = service.buscar(criado.id()).fases().getFirst().rodadas();
            assertEquals(RodadaStatus.EM_ANDAMENTO, seguintes.get(1).status());
            assertTrue(seguintes.stream().skip(2).allMatch(r -> r.status() == RodadaStatus.PENDENTE));
        }
    }

    @Test
    void exigeQuatroParticipantesNaCriacaoEAtualizacao() {
        var org = organizacoes.criar(new OrganizacaoRequest("Liga minimo", true));
        var selecao = selecoes(criarParticipantes(4));
        var criado = service.criar(new CampeonatoRequest(org.id(), 2026, selecao));
        for (int quantidade = 0; quantidade < 4; quantidade++) {
            var request = new CampeonatoRequest(org.id(), 2026, selecao.subList(0, quantidade));
            assertEquals("O campeonato deve ter no mínimo 4 participantes.",
                    assertThrows(RegraCampeonatoException.class, () -> service.criar(request)).getMessage());
            assertThrows(RegraCampeonatoException.class, () -> service.atualizar(criado.id(), request));
        }
        recarregar();
        assertEquals(4, service.buscar(criado.id()).participantes().size());
    }

    @Test
    void criaParticipantesExclusivosESemAlterarHistoricoDeOutrosCampeonatos() {
        var org = organizacoes.criar(new OrganizacaoRequest("Liga da tela", true));
        var jogador = jogadores.criar(new JogadorRequest("Jogador da tela", null));
        var equipe = equipes.criar(new EquipeRequest("Equipe da tela", "https://example.com/logo.png"));
        var selecao = new ParticipanteRequest(jogador.id(), equipe.id());
        var selecaoCompleta = new ArrayList<>(List.of(selecao));
        selecaoCompleta.addAll(selecoes(criarParticipantes(3)));
        var primeiro = service.criar(new CampeonatoRequest(org.id(), 2026, selecaoCompleta));
        var segundo = service.criar(new CampeonatoRequest(org.id(), 2027, selecaoCompleta));
        var participante = primeiro.participantes().getFirst();
        assertEquals(jogador.id(), participante.jogador().id());
        assertEquals(equipe.id(), participante.equipe().id());
        assertNotEquals(participante.id(), segundo.participantes().getFirst().id());
        assertTrue(Collections.disjoint(
                primeiro.participantes().stream().map(ParticipanteResponse::id).toList(),
                segundo.participantes().stream().map(ParticipanteResponse::id).toList()));
        var iniciado = service.iniciar(primeiro.id(), new IniciarCampeonatoRequest(TipoFormato.LIGA, false, null, null));
        for (var rodada : iniciado.fases().getFirst().rodadas()) {
            for (var partida : rodada.partidas()) service.registrarResultado(primeiro.id(), partida.id(), placar());
        }
        recarregar();
        var historico = service.buscar(primeiro.id());

        var outraEquipe = equipes.criar(new EquipeRequest("Outra equipe", "https://example.com/outra.png"));
        selecaoCompleta.set(0, new ParticipanteRequest(jogador.id(), outraEquipe.id()));
        service.atualizar(segundo.id(), new CampeonatoRequest(org.id(), 2027, selecaoCompleta));
        recarregar();
        assertEquals(equipe.id(), service.buscar(primeiro.id()).participantes().stream()
                .filter(p -> p.jogador().id().equals(jogador.id())).findFirst().orElseThrow().equipe().id());
        var atualizado = service.buscar(segundo.id()).participantes().getFirst();
        assertEquals(outraEquipe.id(), atualizado.equipe().id());
        assertNotEquals(participante.id(), atualizado.id());
        participantes.atualizar(atualizado.id(), selecao);
        recarregar();
        assertEquals(historico, service.buscar(primeiro.id()));
        assertEquals(CampeonatoStatus.FINALIZADO, historico.status());
    }

    @Test
    void rejeitaMesmoJogadorComEquipesDiferentesNaCriacaoEAtualizacao() {
        var org = organizacoes.criar(new OrganizacaoRequest("Liga sem duplicatas", true));
        var jogador = jogadores.criar(new JogadorRequest("Jogador unico", null));
        var equipe = equipes.criar(new EquipeRequest("Equipe A", "https://example.com/a.png"));
        var outraEquipe = equipes.criar(new EquipeRequest("Equipe B", "https://example.com/b.png"));
        var selecao = new ParticipanteRequest(jogador.id(), equipe.id());
        var validos = new ArrayList<>(List.of(selecao));
        validos.addAll(selecoes(criarParticipantes(3)));
        var duplicados = new ArrayList<>(validos);
        duplicados.set(1, new ParticipanteRequest(jogador.id(), outraEquipe.id()));
        String mensagem = "O mesmo jogador não pode participar mais de uma vez no campeonato.";

        var erroCriacao = assertThrows(RegraCampeonatoException.class,
                () -> service.criar(new CampeonatoRequest(org.id(), 2026, duplicados)));
        assertEquals(mensagem, erroCriacao.getMessage());

        var criado = service.criar(new CampeonatoRequest(org.id(), 2026, validos));
        var erroAtualizacao = assertThrows(RegraCampeonatoException.class,
                () -> service.atualizar(criado.id(), new CampeonatoRequest(org.id(), 2026, duplicados)));
        assertEquals(mensagem, erroAtualizacao.getMessage());
        recarregar();
        assertEquals(criado.participantes(), service.buscar(criado.id()).participantes());
    }

    @Test
    void persisteCampeonatoComParticipantesExistentesEPermiteAtualizarAntesDeIniciar() {
        List<Long> ids = criarParticipantes(4);
        var criado = service.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Liga", true)).id(), 2026, selecoes(ids)));
        service.atualizar(criado.id(), new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Liga Local", true)).id(), 2027, selecoes(ids.reversed())));
        recarregar();
        var salvo = service.buscar(criado.id());
        assertEquals("Liga Local", salvo.organizacao().nome());
        assertEquals(2027, salvo.edicao());
        assertEquals(selecoes(ids.reversed()), salvo.participantes().stream()
                .map(p -> new ParticipanteRequest(p.jogador().id(), p.equipe().id())).toList());
        assertTrue(Collections.disjoint(ids, criado.participantes().stream().map(ParticipanteResponse::id).toList()));
        assertTrue(Collections.disjoint(criado.participantes().stream().map(ParticipanteResponse::id).toList(),
                salvo.participantes().stream().map(ParticipanteResponse::id).toList()));
        assertEquals(CampeonatoStatus.CRIADO, salvo.status());
        assertTrue(service.listar().stream().anyMatch(c -> c.id().equals(criado.id())));
    }

    @Test
    void geraLigaComReturnoEFinalizaSemAlterarClassificacoesAnteriores() {
        List<Long> ids = criarParticipantes(4);
        var criado = service.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Liga", true)).id(), 2026, selecoes(ids)));
        var iniciado = service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.LIGA, true, null, null));
        recarregar();
        var rodadas = service.buscar(criado.id()).fases().getFirst().rodadas();
        assertEquals(6, rodadas.size());
        assertEquals(12, rodadas.stream().mapToInt(r -> r.partidas().size()).sum());
        assertEquals(iniciado.status(), CampeonatoStatus.EM_ANDAMENTO);
        assertThrows(RegraCampeonatoException.class, () -> service.iniciar(criado.id(),
                new IniciarCampeonatoRequest(TipoFormato.LIGA, false, null, null)));
        assertThrows(RegraCampeonatoException.class, () -> service.atualizar(criado.id(),
                new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Outro", true)).id(), 2026, selecoes(ids))));

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
        var impar = service.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Impar", true)).id(), 2026, selecoes(criarParticipantes(5))));
        var agenda = service.iniciar(impar.id(), new IniciarCampeonatoRequest(TipoFormato.LIGA, false, null, null));
        assertEquals(5, agenda.fases().getFirst().rodadas().size());
        assertEquals(5, agenda.fases().getFirst().rodadas().stream()
                .map(RodadaResponse::folgaParticipanteId).distinct().count());
        List<Long> ids = criarParticipantes(6);
        var parcial = service.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Parcial", true)).id(), 2026, selecoes(ids)));
        for (Integer jogos : Arrays.asList(null, 0, 1, 2)) {
            var erro = assertThrows(RegraCampeonatoException.class, () -> service.iniciar(parcial.id(),
                    new IniciarCampeonatoRequest(TipoFormato.LIGA_PARCIAL, false, jogos, null)));
            assertEquals("A liga parcial deve ter no mínimo 3 jogos por participante.", erro.getMessage());
            assertEquals(CampeonatoStatus.CRIADO, service.buscar(parcial.id()).status());
        }
        assertThrows(RegraCampeonatoException.class, () -> service.iniciar(parcial.id(),
                new IniciarCampeonatoRequest(TipoFormato.LIGA_PARCIAL, false, 6, null)));
        var gerado = service.iniciar(parcial.id(), new IniciarCampeonatoRequest(TipoFormato.LIGA_PARCIAL, false, 3, null));
        recarregar();
        List<PartidaResponse> partidas = service.buscar(gerado.id()).fases().getFirst().rodadas().stream()
                .flatMap(r -> r.partidas().stream()).toList();
        assertEquals(9, partidas.size());
        for (Long id : gerado.participantes().stream().map(ParticipanteResponse::id).toList()) assertEquals(3, partidas.stream()
                .filter(p -> p.mandante().id().equals(id) || p.visitante().id().equals(id)).count());
        assertEquals(9, partidas.stream().map(p -> Math.min(p.mandante().id(), p.visitante().id())
                + ":" + Math.max(p.mandante().id(), p.visitante().id())).distinct().count());
    }

    @Test
    void swissRetomaDoBancoEvitaRevancheEAlternaFolga() {
        var criado = service.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Swiss", true)).id(), 2026, selecoes(criarParticipantes(5))));
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
        List<Long> ids = criarParticipantes(4);
        assertThrows(RegraCampeonatoException.class, () -> service.criar(
                new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Duplicado", true)).id(), 2026, selecoes(List.of(ids.getFirst(), ids.getFirst(), ids.get(1), ids.get(2))))));
        assertThrows(ResponseStatusException.class, () -> service.criar(
                new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Inexistente", true)).id(), 2026, List.of(new ParticipanteRequest(Long.MAX_VALUE, Long.MAX_VALUE), selecoes(ids).get(0), selecoes(ids).get(1), selecoes(ids).get(2)))));
        var participante = participantes.buscar(ids.getFirst());
        equipes.desativar(participante.equipe().id());
        assertThrows(ResponseStatusException.class, () -> service.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Inativo", true)).id(), 2026, selecoes(ids))));
        assertThrows(ResponseStatusException.class, () -> service.buscar(Long.MAX_VALUE));
    }

    @Test
    void formatosIncompletosDaOrigemNaoIniciamCampeonato() {
        var criado = service.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Copa", true)).id(), 2026, selecoes(criarParticipantes(4))));
        for (TipoFormato formato : List.of(TipoFormato.MISTA)) {
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

    private List<ParticipanteRequest> selecoes(List<Long> ids) {
        return ids.stream().map(id -> {
            if (id == Long.MAX_VALUE) return new ParticipanteRequest(id, id);
            var participante = participantes.buscar(id);
            return new ParticipanteRequest(participante.jogador().id(), participante.equipe().id());
        }).toList();
    }
}
