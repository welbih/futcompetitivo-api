ALTER TABLE confronto_pessoal RENAME TO x1;

ALTER TABLE partida ADD COLUMN x1_id BIGINT;
ALTER TABLE partida ADD COLUMN numero INTEGER;
ALTER TABLE partida ADD CONSTRAINT fk_partida_x1 FOREIGN KEY (x1_id) REFERENCES x1 (id);
ALTER TABLE partida ADD CONSTRAINT uk_partida_x1_numero UNIQUE (x1_id, numero);
ALTER TABLE partida ADD CONSTRAINT ck_partida_x1_numero CHECK (
    (x1_id IS NULL AND numero IS NULL) OR (x1_id IS NOT NULL AND numero IS NOT NULL AND numero BETWEEN 1 AND 5)
);
ALTER TABLE partida ADD CONSTRAINT ck_partida_x1_exclusiva CHECK (
    x1_id IS NULL OR (rodada_id IS NULL AND confronto_id IS NULL)
);
ALTER TABLE partida DROP CONSTRAINT ck_partida_status;
ALTER TABLE partida ADD CONSTRAINT ck_partida_status CHECK (
    status IN ('AGENDADA', 'EM_ANDAMENTO', 'FINALIZADA', 'CANCELADA')
);

-- Usa a sequence compartilhada para evitar colisões com IDs de partidas de campeonatos.
INSERT INTO partida (id, x1_id, numero, mandante_id, visitante_id, status,
                     gol_de_ouro, gols_mandante, gols_visitante, penaltis_mandante, penaltis_visitante)
SELECT nextval('partida_seq'), confronto_id, numero, mandante_id, visitante_id, status,
       gol_de_ouro, gols_mandante, gols_visitante, penaltis_mandante, penaltis_visitante
FROM partida_pessoal ORDER BY confronto_id, numero;

DROP TABLE partida_pessoal;
DROP SEQUENCE partida_pessoal_seq;
