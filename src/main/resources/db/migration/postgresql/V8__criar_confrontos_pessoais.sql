CREATE SEQUENCE confronto_pessoal_seq START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE partida_pessoal_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE confronto_pessoal (
    id BIGINT DEFAULT nextval('confronto_pessoal_seq') PRIMARY KEY,
    participante_um_id BIGINT NOT NULL,
    participante_dois_id BIGINT NOT NULL,
    tipo VARCHAR(255) NOT NULL,
    encerrar_antecipadamente BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT fk_confronto_pessoal_participante_um FOREIGN KEY (participante_um_id) REFERENCES participante (id),
    CONSTRAINT fk_confronto_pessoal_participante_dois FOREIGN KEY (participante_dois_id) REFERENCES participante (id),
    CONSTRAINT ck_confronto_pessoal_participantes CHECK (participante_um_id <> participante_dois_id),
    CONSTRAINT ck_confronto_pessoal_tipo CHECK (tipo IN ('MELHOR_DE_TRES', 'MELHOR_DE_CINCO')),
    CONSTRAINT ck_confronto_pessoal_status CHECK (status IN ('EM_ANDAMENTO', 'FINALIZADO'))
);

CREATE TABLE partida_pessoal (
    id BIGINT DEFAULT nextval('partida_pessoal_seq') PRIMARY KEY,
    confronto_id BIGINT NOT NULL,
    numero INTEGER NOT NULL,
    mandante_id BIGINT NOT NULL,
    visitante_id BIGINT NOT NULL,
    status VARCHAR(255) NOT NULL,
    gol_de_ouro BOOLEAN,
    gols_mandante INTEGER,
    gols_visitante INTEGER,
    penaltis_mandante INTEGER,
    penaltis_visitante INTEGER,
    CONSTRAINT fk_partida_pessoal_confronto FOREIGN KEY (confronto_id) REFERENCES confronto_pessoal (id),
    CONSTRAINT fk_partida_pessoal_mandante FOREIGN KEY (mandante_id) REFERENCES participante (id),
    CONSTRAINT fk_partida_pessoal_visitante FOREIGN KEY (visitante_id) REFERENCES participante (id),
    CONSTRAINT uk_partida_pessoal_numero UNIQUE (confronto_id, numero),
    CONSTRAINT ck_partida_pessoal_numero CHECK (numero BETWEEN 1 AND 5),
    CONSTRAINT ck_partida_pessoal_participantes CHECK (mandante_id <> visitante_id),
    CONSTRAINT ck_partida_pessoal_status CHECK (status IN ('AGENDADA', 'FINALIZADA', 'CANCELADA'))
);

CREATE INDEX idx_confronto_pessoal_participante_um ON confronto_pessoal (participante_um_id);
CREATE INDEX idx_confronto_pessoal_participante_dois ON confronto_pessoal (participante_dois_id);
