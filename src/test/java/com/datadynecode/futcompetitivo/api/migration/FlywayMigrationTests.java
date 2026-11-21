package com.datadynecode.futcompetitivo.api.migration;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.CampeonatoStatus;
import com.datadynecode.futcompetitivo.api.entity.TipoFormato;
import com.datadynecode.futcompetitivo.api.service.*;
import com.datadynecode.futcompetitivo.api.suporte.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@ActiveProfiles("test-postgres")
class FlywayMigrationTests {
    @Autowired private Flyway flyway;
    @Autowired private CampeonatoService campeonatos;
    @Autowired private OrganizacaoService organizacoes;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private ParticipanteService participantes;
    @Autowired private EntityManager entityManager;
    @Autowired private ConfrontoPessoalService confrontosPessoais;
    @Autowired private JogadorEstatisticasService estatisticas;

    @Test
    @Transactional
    void persisteDesistenciaESubstituicaoManualNoPostgres() {
        var organizacao = organizacoes.criar(new OrganizacaoRequest("Substituicoes PG",true));
        var participantes = new ArrayList<ParticipanteRequest>();
        for(int i=0;i<6;i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador PG " + i,null));
            var equipe = equipes.criar(new EquipeRequest("Equipe PG " + i,"https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(jogador.id(),equipe.id()));
        }
        var criado = campeonatos.criar(new CampeonatoRequest(organizacao.id(),2026,participantes));
        var iniciado = campeonatos.iniciar(criado.id(),new IniciarCampeonatoRequest(TipoFormato.MISTA,false,null,null,4,null,false,
                com.datadynecode.futcompetitivo.api.entity.FormaGeracaoMataMata.ALEATORIA));
        for(var rodada:iniciado.fases().getFirst().rodadas()) for(var p:rodada.partidas())
            campeonatos.registrarResultado(criado.id(),p.id(),new ResultadoRequest(1,0,null,null,false));
        var lider = campeonatos.classificacao(criado.id()).tabela().getFirst().participante().id();
        campeonatos.desistirParticipante(criado.id(),lider);
        var escolhido = campeonatos.vagasMataMata(criado.id()).getFirst().candidatos().getLast().id();
        var classificados = campeonatos.classificados(criado.id(),4).stream()
                .map(p -> p.id().equals(lider) ? escolhido : p.id()).toList();
        campeonatos.iniciarMataMata(criado.id(),new IniciarMataMataRequest(
                List.of(new IniciarMataMataRequest.Substituicao(lider,escolhido)),
                List.of(new ConfrontoInicialRequest(classificados.get(2),classificados.get(0)),
                        new ConfrontoInicialRequest(classificados.get(1),classificados.get(3)))));
        entityManager.flush(); entityManager.clear();
        var salvo = campeonatos.buscar(criado.id());
        assertEquals(List.of(lider),salvo.participantesDesistentes());
        assertEquals(escolhido,salvo.substituicoesMataMata().get(lider));
        assertTrue(entityManager.find(com.datadynecode.futcompetitivo.api.entity.Fase.class,salvo.fases().getLast().id()).isConfrontosManuais());
        assertEquals(com.datadynecode.futcompetitivo.api.entity.FormaGeracaoMataMata.ALEATORIA,salvo.fases().getLast().formaGeracaoMataMata());
        assertEquals(classificados.get(2),salvo.fases().getLast().rodadas().getFirst().partidas().getFirst().mandante().id());
    }

    @Test
    @Transactional
    void consultaEstatisticasNoPostgresSemDuplicarPartidas() {
        var historico = new com.datadynecode.futcompetitivo.api.suporte.HistoricoJogadorFixture(entityManager);
        historico.fase.setTipo(com.datadynecode.futcompetitivo.api.entity.TipoFase.ELIMINATORIA);
        var confronto = new com.datadynecode.futcompetitivo.api.entity.Confronto();
        confronto.setParticipanteUm(historico.um); confronto.setParticipanteDois(historico.dois);
        confronto.setTipo(com.datadynecode.futcompetitivo.api.entity.TipoConfronto.IDA_E_VOLTA);
        historico.fase.getConfrontos().add(confronto); entityManager.persist(confronto);
        confronto.getPartidas().add(historico.campeonato(historico.dois, true, 4, 1));
        confronto.getPartidas().add(historico.campeonato(historico.dois, false, 2, 3));
        historico.x1(historico.tres, false, 2, 2);
        entityManager.flush(); entityManager.clear();
        var response = estatisticas.buscar(historico.jogador.getId(), OrigemPartida.TODOS);
        assertEquals(new JogadorEstatisticasResponse.Resumo(3, 1, 1, 1, 8, 6, 2), response.resumo());
        assertEquals("IDA", response.maioresGoleadasAplicadasPorGols().getFirst().idaOuVolta());
        assertEquals("VOLTA", response.maioresGoleadasSofridasPorGols().getFirst().idaOuVolta());
        assertEquals("SEMIFINAL", response.maioresGoleadasAplicadasPorGols().getFirst().fase().etapa().codigo());
        assertEquals(1, estatisticas.buscar(historico.jogador.getId(), OrigemPartida.X1).resumo().partidasDisputadas());
    }

    @Test
    @Transactional
    void schemaMigradoSuportaConfrontoPessoalSemCampeonato() {
        var um = jogadores.criar(new JogadorRequest("Um", null));
        var dois = jogadores.criar(new JogadorRequest("Dois", null));
        var equipe = equipes.criar(new EquipeRequest("Equipe X1", "https://example.com/logo.png"));
        var confronto = confrontosPessoais.criar(new ConfrontoPessoalRequest(
                new ParticipanteRequest(um.id(), equipe.id()), new ParticipanteRequest(dois.id(), equipe.id()),
                com.datadynecode.futcompetitivo.api.entity.TipoSeriePessoal.MELHOR_DE_TRES, true));
        confrontosPessoais.registrarResultado(confronto.id(), confronto.partidas().get(0).id(),
                new ResultadoRequest(2, 0, null, null, false));
        confrontosPessoais.registrarResultado(confronto.id(), confronto.partidas().get(1).id(),
                new ResultadoRequest(0, 1, null, null, false));
        entityManager.flush();
        entityManager.clear();
        var salvo = confrontosPessoais.buscar(confronto.id());
        assertEquals(um.id(), salvo.vencedor().jogador().id());
        assertEquals(com.datadynecode.futcompetitivo.api.entity.StatusPartida.CANCELADA,
                salvo.partidas().getLast().status());
        assertNull(salvo.partidas().getLast().resultado());
    }

    @Test
    @Transactional
    void persisteRetiradaDeDesistenciaEResetaResultadosWoNoPostgres() {
        var lista=new ArrayList<ParticipanteRequest>();
        for(int i=0;i<4;i++) {
            var j=jogadores.criar(new JogadorRequest("Retorno PG "+i,null));
            var e=equipes.criar(new EquipeRequest("Equipe retorno "+i,"https://example.com/logo.png"));
            lista.add(new ParticipanteRequest(j.id(),e.id()));
        }
        var c=campeonatos.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Retorno PG",true)).id(),1,lista));
        var antes=campeonatos.iniciar(c.id(),new IniciarCampeonatoRequest(TipoFormato.LIGA,false,null,null));
        Long participante=antes.fases().getFirst().rodadas().getFirst().partidas().getFirst().mandante().id();
        campeonatos.desistirParticipante(c.id(),participante); entityManager.flush(); entityManager.clear();
        assertFalse(campeonatos.buscar(c.id()).participantesDesistentes().isEmpty());
        campeonatos.cancelarDesistencia(c.id(),participante); entityManager.flush(); entityManager.clear();
        assertEquals(antes,campeonatos.buscar(c.id()));
    }

    @Test
    void aplicaTodasAsVersoesEReexecutaSemRepetirMigracoes() {
        assertEquals(15, flyway.info().applied().length);
        assertEquals(0, flyway.info().pending().length);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    @Transactional
    void corrigeIdentificacaoNoPostgresPreservandoCompeticao() {
        var participantes=new ArrayList<ParticipanteRequest>();
        for(int i=0;i<4;i++) {
            var j=jogadores.criar(new JogadorRequest("Identificacao PG "+i,null));
            var e=equipes.criar(new EquipeRequest("Equipe PG "+i,"https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(j.id(),e.id()));
        }
        var c=campeonatos.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Origem PG",true)).id(),1,participantes));
        var inicio=campeonatos.iniciar(c.id(),new IniciarCampeonatoRequest(TipoFormato.LIGA,false,null,null));
        campeonatos.registrarResultado(c.id(),inicio.fases().getFirst().rodadas().getFirst().partidas().getFirst().id(),
                new ResultadoRequest(2,1,null,null,false));
        var antes=campeonatos.buscar(c.id());
        var destino=organizacoes.criar(new OrganizacaoRequest("Destino PG",true));
        var corrigido=campeonatos.alterarIdentificacao(c.id(),new IdentificacaoCampeonatoRequest(destino.id(),Integer.MAX_VALUE));
        entityManager.flush(); entityManager.clear();
        assertEquals(corrigido,campeonatos.buscar(c.id()));
        assertEquals(antes.fases(),corrigido.fases()); assertEquals(antes.participantes(),corrigido.participantes());
        assertEquals(antes.classificacaoAtual(),corrigido.classificacaoAtual()); assertEquals(antes.status(),corrigido.status());
        organizacoes.desativar(destino.id());
        assertEquals(5,campeonatos.alterarIdentificacao(c.id(),new IdentificacaoCampeonatoRequest(destino.id(),5)).edicao());
    }

    @Test
    @Transactional
    void persisteLigaParcialComMataMataEAgendaManual() {
        var lista=new ArrayList<ParticipanteRequest>();
        for(int i=0;i<4;i++) {
            var jogador=jogadores.criar(new JogadorRequest("Antigo PG "+i,null));
            var equipe=equipes.criar(new EquipeRequest("Equipe antiga "+i,"https://example.com/logo.png"));
            lista.add(new ParticipanteRequest(jogador.id(),equipe.id()));
        }
        var c=campeonatos.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Parcial PG",true)).id(),2020,lista));
        var ids=c.participantes().stream().map(ParticipanteResponse::id).toList();
        int[][][] agenda={{{0,1},{2,3}},{{0,2},{1,3}},{{0,3},{1,2}}};
        var rodadas=java.util.Arrays.stream(agenda).map(r -> new IniciarLigaParcialManualRequest.RodadaManual(java.util.Arrays.stream(r)
                .map(p -> new ConfrontoInicialRequest(ids.get(p[0]),ids.get(p[1]))).toList())).toList();
        var configuracao=new IniciarCampeonatoRequest(TipoFormato.MISTA_PARCIAL,false,3,null,4,null,true,
                com.datadynecode.futcompetitivo.api.entity.FormaGeracaoMataMata.ALEATORIA);
        campeonatos.iniciarLigaParcialManual(c.id(),new IniciarLigaParcialManualRequest(configuracao,rodadas));
        entityManager.flush(); entityManager.clear();
        var salvo=campeonatos.buscar(c.id());
        assertEquals(3,salvo.fases().getFirst().jogosPorParticipante()); assertEquals(3,salvo.fases().getFirst().totalRodadas());
        assertEquals(ids.getFirst(),salvo.fases().getFirst().rodadas().getFirst().partidas().getFirst().mandante().id());
        for(var r:salvo.fases().getFirst().rodadas()) for(var p:r.partidas())
            campeonatos.registrarResultado(c.id(),p.id(),new ResultadoRequest(1,0,null,null,false));
        assertTrue(campeonatos.buscar(c.id()).aguardandoMataMata());
        var mata=campeonatos.iniciarMataMata(c.id());
        assertEquals(2,mata.fases().size());
        assertEquals(com.datadynecode.futcompetitivo.api.entity.FormaGeracaoMataMata.ALEATORIA,mata.fases().getLast().formaGeracaoMataMata());
    }

    @Test
    @Transactional
    void schemaMigradoSuportaCampeonatoResultadosEClassificacao() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador " + i, null));
            var equipe = equipes.criar(new EquipeRequest("Equipe " + i, "https://example.com/logo.png"));
            ids.add(participantes.criar(new ParticipanteRequest(jogador.id(), equipe.id())).id());
        }
        var criado = campeonatos.criar(new CampeonatoRequest(organizacoes.criar(new OrganizacaoRequest("Liga migrada", true)).id(), 2026, selecoes(ids)));
        var iniciado = campeonatos.iniciar(criado.id(),
                new IniciarCampeonatoRequest(TipoFormato.LIGA, false, null, null));
        campeonatos.registrarResultado(criado.id(),iniciado.fases().getFirst().rodadas().getFirst().partidas().getFirst().id(),
                new ResultadoRequest(2,1,null,null,false));
        entityManager.flush(); entityManager.clear();
        assertTrue(campeonatos.classificacao(criado.id()).parcial());
        assertTrue(campeonatos.buscar(criado.id()).classificacaoPorRodada().isEmpty());
        for (var rodada : iniciado.fases().getFirst().rodadas())
            for (var partida : rodada.partidas())
                campeonatos.registrarResultado(criado.id(), partida.id(),
                        new ResultadoRequest(2, 1, null, null, false));
        entityManager.flush();
        entityManager.clear();
        var finalizado = campeonatos.buscar(criado.id());
        assertEquals(CampeonatoStatus.FINALIZADO, finalizado.status());
        assertEquals(3, finalizado.classificacaoPorRodada().size());
        assertTrue(campeonatos.classificacao(criado.id()).tabela().stream().allMatch(c -> c.jogos() == 3));

        campeonatos.registrarResultado(criado.id(), iniciado.fases().getFirst().rodadas()
                .getFirst().partidas().getFirst().id(), new ResultadoRequest(0, 3, null, null, false));
        entityManager.flush();
        entityManager.clear();
        assertEquals(3, campeonatos.buscar(criado.id()).classificacaoPorRodada().size());
    }

    private List<ParticipanteRequest> selecoes(List<Long> ids) {
        return ids.stream().map(id -> {
            if (id == Long.MAX_VALUE) return new ParticipanteRequest(id, id);
            var participante = participantes.buscar(id);
            return new ParticipanteRequest(participante.jogador().id(), participante.equipe().id());
        }).toList();
    }
}
