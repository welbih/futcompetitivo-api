package com.datadynecode.futcompetitivo.api.migration;

import com.datadynecode.futcompetitivo.api.dto.ResultadoRequest;
import com.datadynecode.futcompetitivo.api.service.CampeonatoService;
import com.datadynecode.futcompetitivo.api.suporte.PostgresTestConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.application.name=teste-script-campeonato-202")
@Import(PostgresTestConfiguration.class)
@ActiveProfiles("test-postgres")
class Campeonato202ScriptTests {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired CampeonatoService campeonatos;

    @BeforeEach
    void prepararBancoIsolado() {
        // Banco exclusivo do Testcontainers deste contexto; nunca usa o container dev/producao.
        jdbc.execute("TRUNCATE campeonato, participante, jogador, equipe, organizacao CASCADE");
        jdbc.execute("INSERT INTO organizacao (id,nome,ativo) VALUES (202,'Organizacao de teste',TRUE)");
        jdbc.execute("INSERT INTO equipe (id,nome,ativo) VALUES (1,'Equipe de teste',TRUE)");
        jdbc.execute("INSERT INTO jogador (id,nome,ativo) SELECT n,'Jogador ' || n,TRUE FROM generate_series(1,14) n");
        jdbc.execute("INSERT INTO participante (id,jogador_id,equipe_id,ativo) SELECT 251+n,n,1,TRUE FROM generate_series(1,14) n");
        jdbc.execute("INSERT INTO campeonato (id,organizacao_id,edicao,status) VALUES (202,202,2026,'CRIADO')");
        jdbc.execute("INSERT INTO campeonato_participante (campeonato_id,participante_id,ordem) SELECT 202,252+n,n FROM generate_series(0,13) n");
    }

    @Test
    void criaGruposExatosEAgendaCompletaSemResultados() throws Exception {
        executar();
        assertEquals("EM_ANDAMENTO", jdbc.queryForObject("SELECT status FROM campeonato WHERE id=202", String.class));
        assertEquals(8, jdbc.queryForObject("SELECT quantidade_classificados FROM fase WHERE campeonato_id=202", Integer.class));
        assertEquals(List.of("252,253,254,255,256,257,258", "259,260,261,262,263,264,265"), jdbc.queryForList("""
                SELECT string_agg(gp.participante_id::text, ',' ORDER BY gp.ordem)
                FROM grupo g JOIN grupo_participante gp ON gp.grupo_id=g.id
                GROUP BY g.nome ORDER BY g.nome
                """, String.class));
        assertEquals(42, jdbc.queryForObject("SELECT COUNT(*) FROM partida", Integer.class));
        assertEquals(List.of(6,6,6,6,6,6,6), jdbc.queryForList("""
                SELECT COUNT(*)::integer FROM partida p JOIN rodada r ON r.id=p.rodada_id GROUP BY r.numero ORDER BY r.numero
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM rodada WHERE status='EM_ANDAMENTO'", Integer.class));
        assertEquals(42, jdbc.queryForObject("SELECT COUNT(*) FROM partida WHERE status='AGENDADA' AND gols_mandante IS NULL AND gols_visitante IS NULL AND penaltis_mandante IS NULL AND penaltis_visitante IS NULL", Integer.class));
        assertEquals(42, jdbc.queryForObject("SELECT COUNT(DISTINCT (LEAST(mandante_id,visitante_id),GREATEST(mandante_id,visitante_id))) FROM partida", Integer.class));
        assertEquals(14, jdbc.queryForObject("""
                SELECT COUNT(*) FROM (SELECT participante,COUNT(*) quantidade FROM
                    (SELECT mandante_id participante FROM partida UNION ALL SELECT visitante_id FROM partida) jogos
                    GROUP BY participante HAVING COUNT(*)=6) totais
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM partida p
                JOIN grupo_participante m ON m.participante_id=p.mandante_id
                JOIN grupo_participante v ON v.participante_id=p.visitante_id WHERE m.grupo_id<>v.grupo_id
                """, Integer.class));
        assertEquals(84, jdbc.queryForObject("""
                SELECT COUNT(*) FROM (SELECT r.numero,p.mandante_id participante FROM partida p JOIN rodada r ON r.id=p.rodada_id
                    UNION SELECT r.numero,p.visitante_id FROM partida p JOIN rodada r ON r.id=p.rodada_id) participacoes
                """, Integer.class));
        var resposta = campeonatos.buscar(202L);
        assertEquals(7, resposta.fases().getFirst().rodadas().size());
        assertTrue(resposta.fases().getFirst().rodadas().stream().flatMap(r -> r.partidas().stream()).allMatch(p -> p.resultado()==null));
    }

    @Test
    void sistemaGeraMataMataComQuatroClassificadosDeCadaGrupo() throws Exception {
        executar();
        var grupos = campeonatos.buscar(202L).fases().getFirst();
        for (var rodada : grupos.rodadas()) {
            for (var partida : rodada.partidas()) {
                campeonatos.registrarResultado(202L, partida.id(), new ResultadoRequest(1,0,null,null,false));
            }
        }
        assertTrue(campeonatos.buscar(202L).aguardandoMataMata());
        var resposta = campeonatos.iniciarMataMata(202L);
        assertEquals(2, resposta.fases().size());
        assertEquals(com.datadynecode.futcompetitivo.api.entity.TipoFase.ELIMINATORIA, resposta.fases().getLast().tipo());
        var jogos = resposta.fases().getLast().rodadas().getFirst().partidas();
        assertEquals(4, jogos.size());
        Set<Long> selecionados = jogos.stream().flatMap(p -> java.util.stream.Stream.of(p.mandante().id(),p.visitante().id()))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(8, selecionados.size());
        assertEquals(4, selecionados.stream().filter(id -> id>=252 && id<=258).count());
        assertEquals(4, selecionados.stream().filter(id -> id>=259 && id<=265).count());
    }

    @Test
    void reexecucaoNaoDuplicaAgenda() throws Exception {
        executar();
        assertThrows(SQLException.class, this::executar);
        assertEquals(42, jdbc.queryForObject("SELECT COUNT(*) FROM partida", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fase", Integer.class));
    }

    @Test
    void divergenciaDoCsvOuInativoAbortamSemAlteracoes() {
        jdbc.update("UPDATE campeonato_participante SET participante_id=265 WHERE campeonato_id=202 AND ordem=0");
        assertThrows(SQLException.class, this::executar);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM fase", Integer.class));
        assertEquals("CRIADO", jdbc.queryForObject("SELECT status FROM campeonato WHERE id=202", String.class));
        jdbc.update("UPDATE campeonato_participante SET participante_id=252 WHERE campeonato_id=202 AND ordem=0");
        jdbc.update("UPDATE jogador SET ativo=FALSE WHERE id=1");
        assertThrows(SQLException.class, this::executar);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM partida", Integer.class));
    }

    private void executar() throws Exception {
        String script = Files.readString(Path.of("tools/sql/iniciar-campeonato-202-grupos.sql"));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            try { statement.execute(script); }
            catch (SQLException exception) { statement.execute("ROLLBACK"); throw exception; }
        }
    }
}
