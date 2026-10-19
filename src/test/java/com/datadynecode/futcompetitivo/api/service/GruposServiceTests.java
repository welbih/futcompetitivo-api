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
class GruposServiceTests {
    @Autowired private CampeonatoService service;
    @Autowired private OrganizacaoService organizacoes;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private EntityManager em;

    @ParameterizedTest
    @CsvSource({"4,4,false", "5,4,false", "5,5,false", "6,5,false", "7,5,false", "7,7,false",
            "8,6,true", "10,7,false", "12,8,false"})
    void sorteiaDoisGruposEClassificaAteCampeao(int quantidade, int corte, boolean returno) {
        var criado = criar(quantidade);
        var campeonato = service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.GRUPOS, returno, null, null, corte));
        recarregar();
        campeonato = service.buscar(criado.id());
        assertEquals(TipoFase.GRUPOS, campeonato.fases().getFirst().tipo());
        var grupos = service.grupos(criado.id());
        assertEquals(List.of("Grupo A", "Grupo B"), grupos.stream().map(GrupoResponse::nome).toList());
        assertEquals(2, grupos.size());
        assertTrue(Math.abs(grupos.getFirst().participantes().size() - grupos.getLast().participantes().size()) <= 1);
        Map<Long, Long> grupoPorParticipante = new HashMap<>();
        grupos.forEach(g -> g.participantes().forEach(p -> assertNull(grupoPorParticipante.put(p.id(), g.id()))));
        assertEquals(quantidade, grupoPorParticipante.size());
        assertThrows(RegraCampeonatoException.class, () -> service.classificados(criado.id(), corte));
        assertThrows(RegraCampeonatoException.class, () -> service.classificacao(criado.id()));
        int partidasGrupos = 0;
        for (var rodada : campeonato.fases().getFirst().rodadas()) {
            Set<Long> usados = new HashSet<>();
            for (var partida : rodada.partidas()) {
                assertEquals(grupoPorParticipante.get(partida.mandante().id()), grupoPorParticipante.get(partida.visitante().id()));
                assertTrue(usados.add(partida.mandante().id()));
                assertTrue(usados.add(partida.visitante().id()));
                var placar = partida.mandante().id() < partida.visitante().id()
                        ? new ResultadoRequest(2, 0, null, null, false) : new ResultadoRequest(0, 2, null, null, false);
                service.registrarResultadosSemFinalizar(criado.id(), rodada.id(), List.of(new ResultadoPartidaRequest(partida.id(), placar)));
                partidasGrupos++;
            }
            assertEquals(1, service.buscar(criado.id()).fases().size());
            service.finalizarRodada(criado.id(), rodada.id());
        }
        int esperado = grupos.stream().mapToInt(g -> g.participantes().size() * (g.participantes().size() - 1) / 2).sum();
        assertEquals(esperado * (returno ? 2 : 1), partidasGrupos);
        recarregar();
        campeonato = service.buscar(criado.id());
        assertEquals(2, campeonato.fases().size());
        assertEquals(CampeonatoStatus.EM_ANDAMENTO, campeonato.status());
        assertTrue(campeonato.classificacaoPorRodada().isEmpty());
        var gruposFinalizados = service.grupos(criado.id());
        var selecionados = service.classificados(criado.id(), corte).stream().map(ParticipanteResponse::id).toList();
        assertEquals(corte, selecionados.size());
        for (var grupo : gruposFinalizados) {
            var tabela = service.classificacaoGrupo(criado.id(), grupo.id());
            assertEquals(grupo.participantes().size(), tabela.tabela().size());
            assertTrue(tabela.tabela().stream().allMatch(c -> c.jogos() == (grupo.participantes().size() - 1) * (returno ? 2 : 1)));
            assertTrue(tabela.tabela().stream().allMatch(c -> grupoPorParticipante.get(c.participante().id()).equals(grupo.id())));
            assertTrue(selecionados.containsAll(tabela.tabela().stream().limit(corte / 2).map(c -> c.participante().id()).toList()));
        }
        var mataMata = campeonato.fases().getLast();
        assertEquals(TipoFase.ELIMINATORIA, mataMata.tipo());
        for (var partida : mataMata.rodadas().getFirst().partidas()) {
            assertNotEquals(grupoPorParticipante.get(partida.mandante().id()), grupoPorParticipante.get(partida.visitante().id()));
        }
        int jogosEliminatorios = 0;
        while (campeonato.status() != CampeonatoStatus.FINALIZADO) {
            var rodada = campeonato.fases().getLast().rodadas().stream().filter(r -> r.status() == RodadaStatus.EM_ANDAMENTO).findFirst().orElseThrow();
            for (var partida : rodada.partidas()) {
                assertTrue(selecionados.contains(partida.mandante().id()));
                assertTrue(selecionados.contains(partida.visitante().id()));
                service.registrarResultado(criado.id(), partida.id(), new ResultadoRequest(1, 1, 5, 4, false));
                jogosEliminatorios++;
            }
            recarregar();
            campeonato = service.buscar(criado.id());
        }
        assertEquals(corte - 1, jogosEliminatorios);
        assertTrue(selecionados.contains(service.vencedor(criado.id()).id()));
        assertEquals(gruposFinalizados, service.grupos(criado.id()));
    }

    @Test
    void correcaoRecalculaGruposAntesDeHaverResultadosNoMataMata() {
        var criado = criar(6);
        var campeonato = service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.GRUPOS, false, null, null, 5));
        for (var rodada : campeonato.fases().getFirst().rodadas())
            for (var partida : rodada.partidas()) service.registrarResultado(criado.id(), partida.id(), new ResultadoRequest(1, 0, null, null, false));
        recarregar();
        var salvo = service.buscar(criado.id());
        var primeira = salvo.fases().getFirst().rodadas().getFirst().partidas().getFirst();
        var mataMataId = salvo.fases().getLast().id();
        var corrigido = service.registrarResultado(criado.id(), primeira.id(), new ResultadoRequest(0, 2, null, null, false));
        recarregar();
        assertEquals(mataMataId, corrigido.fases().getLast().id());
        var selecionados = new HashSet<>(service.classificados(criado.id(), 5).stream().map(ParticipanteResponse::id).toList());
        var naChave = new HashSet<Long>();
        service.buscar(criado.id()).fases().getLast().confrontos().stream().filter(c -> c.origemUmId() == null)
                .forEach(c -> { naChave.add(c.participanteUmId()); if (c.participanteDoisId() != null) naChave.add(c.participanteDoisId()); });
        assertEquals(selecionados, naChave);
        var jogo = corrigido.fases().getLast().rodadas().getFirst().partidas().getFirst();
        service.registrarResultado(criado.id(), jogo.id(), new ResultadoRequest(1, 0, null, null, false));
        var historico = service.grupos(criado.id());
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(criado.id(), primeira.id(),
                new ResultadoRequest(1, 0, null, null, false)));
        recarregar();
        assertEquals(historico, service.grupos(criado.id()));
    }

    @Test
    void validaQuantidadeDeClassificadosESemifinaisCruzadas() {
        var criado = criar(4);
        for (Integer vagas : Arrays.asList(null, 0, 1, 2, 3, 5))
            assertThrows(RegraCampeonatoException.class, () -> service.iniciar(criado.id(),
                    new IniciarCampeonatoRequest(TipoFormato.GRUPOS, false, null, null, vagas)));
        var iniciado = service.iniciar(criado.id(), new IniciarCampeonatoRequest(TipoFormato.GRUPOS, false, null, null, 4));
        var rodada = iniciado.fases().getFirst().rodadas().getFirst();
        for (var partida : rodada.partidas()) service.registrarResultado(criado.id(), partida.id(), new ResultadoRequest(1, 0, null, null, false));
        recarregar();
        var grupos = service.grupos(criado.id());
        var a = service.classificacaoGrupo(criado.id(), grupos.getFirst().id()).tabela();
        var b = service.classificacaoGrupo(criado.id(), grupos.getLast().id()).tabela();
        var semifinal = service.buscar(criado.id()).fases().getLast().rodadas().getFirst();
        assertEquals(a.getFirst().participante().id(), semifinal.partidas().getFirst().mandante().id());
        assertEquals(b.getLast().participante().id(), semifinal.partidas().getFirst().visitante().id());
        assertEquals(b.getFirst().participante().id(), semifinal.partidas().getLast().mandante().id());
        assertEquals(a.getLast().participante().id(), semifinal.partidas().getLast().visitante().id());
    }

    private CampeonatoResponse criar(int quantidade) {
        var org = organizacoes.criar(new OrganizacaoRequest("Copa de grupos", true));
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
