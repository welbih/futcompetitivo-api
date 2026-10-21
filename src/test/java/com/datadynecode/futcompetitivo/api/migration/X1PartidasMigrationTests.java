package com.datadynecode.futcompetitivo.api.migration;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class X1PartidasMigrationTests {
    @Test
    void migraDadosDaV8SemColidirIdsOuAlterarPartidasExistentes() throws Exception {
        String url = "jdbc:h2:mem:x1_upgrade_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        try (var dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(url);
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/postgresql")
                    .target("8").load().migrate();
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("INSERT INTO jogador (id,nome) VALUES (1,'Um'),(2,'Dois')");
                sql.execute("INSERT INTO equipe (id,nome) VALUES (1,'Equipe')");
                sql.execute("INSERT INTO participante (id,jogador_id,equipe_id) VALUES (1,1,1),(2,2,1)");
                sql.execute("INSERT INTO confronto_pessoal (id,participante_um_id,participante_dois_id,tipo,encerrar_antecipadamente,status) "
                        + "VALUES (1,1,2,'MELHOR_DE_TRES',TRUE,'FINALIZADO')");
                sql.execute("INSERT INTO partida (mandante_id,visitante_id,status,gols_mandante,gols_visitante) VALUES (1,2,'FINALIZADA',9,0)");
                sql.execute("INSERT INTO partida_pessoal (id,confronto_id,numero,mandante_id,visitante_id,status,gols_mandante,gols_visitante) "
                        + "VALUES (1,1,1,1,2,'FINALIZADA',2,0),(2,1,2,2,1,'FINALIZADA',0,3)");
                sql.execute("INSERT INTO partida_pessoal (id,confronto_id,numero,mandante_id,visitante_id,status) VALUES (3,1,3,1,2,'CANCELADA')");
            }
            var flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration/postgresql").load();
            assertEquals(1, flyway.migrate().migrationsExecuted);
            assertTrue(flyway.validateWithResult().validationSuccessful);
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM partida WHERE id=1 AND x1_id IS NULL AND gols_mandante=9")) {
                    assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
                }
                try (var rows = sql.executeQuery("SELECT numero,status,gols_mandante,gols_visitante FROM partida WHERE x1_id=1 ORDER BY numero")) {
                    assertTrue(rows.next()); assertEquals(1, rows.getInt(1)); assertEquals(2, rows.getInt(3));
                    assertTrue(rows.next()); assertEquals(2, rows.getInt(1)); assertEquals(3, rows.getInt(4));
                    assertTrue(rows.next()); assertEquals(3, rows.getInt(1)); assertEquals("CANCELADA", rows.getString(2));
                    assertNull(rows.getObject(3)); assertFalse(rows.next());
                }
                assertThrows(SQLException.class, () -> sql.executeQuery("SELECT * FROM partida_pessoal"));
                assertThrows(SQLException.class, () -> sql.executeQuery("SELECT * FROM confronto_pessoal"));
                sql.execute("INSERT INTO rodada (id,numero) VALUES (1,1)");
                assertThrows(SQLException.class, () -> sql.executeUpdate("UPDATE partida SET rodada_id=1 WHERE x1_id=1"));
            }
        }
    }
}
