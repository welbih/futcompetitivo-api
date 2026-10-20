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
class TerceiroLugarServiceTests {
    @Autowired private CampeonatoService service;
    @Autowired private OrganizacaoService organizacoes;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private EntityManager em;

    @ParameterizedTest
    @CsvSource({"4,JOGO_UNICO,ELIMINATORIO", "5,JOGO_UNICO,ELIMINATORIO", "8,JOGO_UNICO,ELIMINATORIO",
            "4,IDA_E_VOLTA,ELIMINATORIO", "5,IDA_E_VOLTA,ELIMINATORIO", "8,IDA_E_VOLTA,ELIMINATORIO",
            "4,IDA_E_VOLTA_FINAL_UNICA,ELIMINATORIO", "5,IDA_E_VOLTA_FINAL_UNICA,ELIMINATORIO",
            "8,IDA_E_VOLTA_FINAL_UNICA,ELIMINATORIO", "6,JOGO_UNICO,MISTA", "6,JOGO_UNICO,GRUPOS"})
    void disputaEntrePerdedoresDasSemifinaisESoEncerraAposAsDuasDecisoes(int quantidade, FormatoEliminatorio modo, TipoFormato formato) {
        var campeonato = iniciar(quantidade, modo, formato);
        Long id = campeonato.id();
        while (true) {
            var fase = campeonato.fases().getLast();
            var rodada = fase.rodadas().stream().filter(r -> r.status() == RodadaStatus.EM_ANDAMENTO).findFirst().orElseThrow();
            if (fase.tipo() == TipoFase.ELIMINATORIA && rodada.id().equals(fase.rodadas().getLast().id())) break;
            preencher(id, rodada, fase, false);
            recarregar();
            campeonato = service.buscar(id);
        }
        var fase = campeonato.fases().getLast();
        var rodada = fase.rodadas().getLast();
        assertTrue(fase.disputaTerceiroLugar());
        assertEquals("DISPUTAS_FINAIS", rodada.etapa().codigo());
        var terceiro = fase.confrontos().stream().filter(ConfrontoResponse::terceiroLugar).findFirst().orElseThrow();
        var finalConfronto = fase.confrontos().getLast();
        assertFalse(finalConfronto.terceiroLugar());
        assertEquals("FINAL", finalConfronto.etapa().codigo());
        assertEquals("TERCEIRO_LUGAR", terceiro.etapa().codigo());
        assertEquals(finalConfronto.origemUmId(), terceiro.origemUmId());
        assertEquals(finalConfronto.origemDoisId(), terceiro.origemDoisId());
        Set<Long> perdedores = new HashSet<>();
        fase.confrontos().stream().filter(c -> c.id().equals(terceiro.origemUmId()) || c.id().equals(terceiro.origemDoisId()))
                .forEach(c -> perdedores.add(c.partidas().getFirst().visitante().id()));
        assertEquals(perdedores, Set.of(terceiro.participanteUmId(), terceiro.participanteDoisId()));
        assertTrue(Collections.disjoint(perdedores, Set.of(finalConfronto.participanteUmId(), finalConfronto.participanteDoisId())));
        assertEquals(modo == FormatoEliminatorio.JOGO_UNICO ? 1 : 2, terceiro.partidas().size());
        assertEquals(modo == FormatoEliminatorio.IDA_E_VOLTA ? 2 : 1, finalConfronto.partidas().size());

        preencherConfronto(id, rodada.id(), finalConfronto, false);
        assertEquals(CampeonatoStatus.EM_ANDAMENTO, service.buscar(id).status());
        assertThrows(RegraCampeonatoException.class, () -> service.terceiroLugar(id));
        boolean manual = quantidade % 2 != 0;
        preencherConfronto(id, rodada.id(), terceiro, manual);
        if (manual) {
            assertEquals(CampeonatoStatus.EM_ANDAMENTO, service.buscar(id).status());
            service.finalizarRodada(id, rodada.id());
        }
        recarregar();
        assertEquals(CampeonatoStatus.FINALIZADO, service.buscar(id).status());
        assertEquals(finalConfronto.participanteUmId(), service.vencedor(id).id());
        assertEquals(terceiro.participanteUmId(), service.terceiroLugar(id).id());
        var decisiva = terceiro.partidas().getLast();
        service.registrarResultado(id, decisiva.id(), terceiro.partidas().size() == 1 ? placar(0, 2) : placar(4, 0));
        recarregar();
        assertEquals(terceiro.participanteDoisId(), service.terceiroLugar(id).id());
        assertEquals(finalConfronto.participanteUmId(), service.vencedor(id).id());
    }

    @Test
    void impedeTrocarPerdedorDaSemifinalSeTerceiroLugarJaTemResultado() {
        var campeonato = iniciar(4, FormatoEliminatorio.JOGO_UNICO, TipoFormato.ELIMINATORIO);
        var fase = campeonato.fases().getFirst();
        var semifinal = fase.rodadas().getFirst();
        preencher(campeonato.id(), semifinal, fase, false);
        var faseFinal = service.buscar(campeonato.id()).fases().getFirst();
        var bronze = faseFinal.confrontos().stream().filter(ConfrontoResponse::terceiroLugar).findFirst().orElseThrow();
        service.registrarResultado(campeonato.id(), bronze.partidas().getFirst().id(), placar(1, 0));
        assertThrows(RegraCampeonatoException.class, () -> service.registrarResultado(campeonato.id(),
                semifinal.partidas().getFirst().id(), placar(0, 2)));
        assertEquals(CampeonatoStatus.EM_ANDAMENTO, service.buscar(campeonato.id()).status());
    }

    private void preencher(Long id, RodadaResponse rodada, FaseResponse fase, boolean manual) {
        if (fase.tipo() != TipoFase.ELIMINATORIA) {
            for (var partida : rodada.partidas()) service.registrarResultado(id, partida.id(), placar(2, 0));
            return;
        }
        Set<Long> ids = new HashSet<>(rodada.partidas().stream().map(PartidaResponse::id).toList());
        for (var confronto : fase.confrontos()) {
            if (confronto.partidas().stream().anyMatch(p -> ids.contains(p.id()))) preencherConfronto(id, rodada.id(), confronto, manual);
        }
    }

    private void preencherConfronto(Long id, Long rodadaId, ConfrontoResponse confronto, boolean manual) {
        for (int i = 0; i < confronto.partidas().size(); i++) {
            var partida = confronto.partidas().get(i);
            var placar = i == 0 ? placar(2, 0) : placar(0, 1);
            if (manual) service.registrarResultadosSemFinalizar(id, rodadaId, List.of(new ResultadoPartidaRequest(partida.id(), placar)));
            else service.registrarResultado(id, partida.id(), placar);
        }
    }

    private CampeonatoResponse iniciar(int quantidade, FormatoEliminatorio modo, TipoFormato formato) {
        var org = organizacoes.criar(new OrganizacaoRequest("Copa com terceiro", true));
        List<ParticipanteRequest> participantes = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador " + i, null));
            var equipe = equipes.criar(new EquipeRequest("Equipe " + i, "https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(jogador.id(), equipe.id()));
        }
        var criado = service.criar(new CampeonatoRequest(org.id(), 2026, participantes));
        service.iniciar(criado.id(), new IniciarCampeonatoRequest(formato, false, null, null,
                formato == TipoFormato.ELIMINATORIO ? null : 4, formato == TipoFormato.ELIMINATORIO ? modo : null, true));
        recarregar();
        return service.buscar(criado.id());
    }

    private ResultadoRequest placar(int casa, int fora) { return new ResultadoRequest(casa, fora, null, null, false); }
    private void recarregar() { em.flush(); em.clear(); }
}
