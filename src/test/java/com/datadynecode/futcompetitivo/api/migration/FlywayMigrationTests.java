package com.datadynecode.futcompetitivo.api.migration;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.CampeonatoStatus;
import com.datadynecode.futcompetitivo.api.entity.TipoFormato;
import com.datadynecode.futcompetitivo.api.service.*;
import jakarta.persistence.EntityManager;

import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

// Smoke test em H2 no modo PostgreSQL; nao substitui validacao em PostgreSQL real.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:flyway_migrations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration/postgresql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.defer-datasource-initialization=false",
        "spring.jpa.show-sql=false",
        "spring.sql.init.mode=never"
})
@ActiveProfiles("test")
class FlywayMigrationTests {
    @Autowired private Flyway flyway;
    @Autowired private CampeonatoService campeonatos;
    @Autowired private OrganizacaoService organizacoes;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private ParticipanteService participantes;
    @Autowired private EntityManager entityManager;

    @Test
    void aplicaTodasAsVersoesEReexecutaSemRepetirMigracoes() {
        assertEquals(7, flyway.info().applied().length);
        assertEquals(0, flyway.info().pending().length);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
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
