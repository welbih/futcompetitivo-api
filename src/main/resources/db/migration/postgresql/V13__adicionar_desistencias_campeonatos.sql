CREATE TABLE campeonato_desistencia (
    campeonato_id BIGINT NOT NULL REFERENCES campeonato(id) ON DELETE CASCADE,
    participante_id BIGINT NOT NULL REFERENCES participante(id),
    PRIMARY KEY (campeonato_id, participante_id)
);
ALTER TABLE partida ADD COLUMN wo BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE campeonato_classificado_base (
    campeonato_id BIGINT NOT NULL REFERENCES campeonato(id) ON DELETE CASCADE,
    ordem INTEGER NOT NULL,
    participante_id BIGINT NOT NULL REFERENCES participante(id),
    PRIMARY KEY (campeonato_id, ordem),
    UNIQUE (campeonato_id, participante_id)
);

CREATE TABLE campeonato_substituicao (
    campeonato_id BIGINT NOT NULL REFERENCES campeonato(id) ON DELETE CASCADE,
    desistente_id BIGINT NOT NULL REFERENCES participante(id),
    substituto_id BIGINT NOT NULL REFERENCES participante(id),
    PRIMARY KEY (campeonato_id, desistente_id),
    UNIQUE (campeonato_id, substituto_id)
);
