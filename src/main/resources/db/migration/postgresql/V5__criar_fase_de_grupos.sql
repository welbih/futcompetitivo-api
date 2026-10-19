CREATE SEQUENCE grupo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE grupo (
    id BIGINT DEFAULT nextval('grupo_seq') PRIMARY KEY,
    fase_id BIGINT,
    nome VARCHAR(255) NOT NULL,
    CONSTRAINT fk_grupo_fase FOREIGN KEY (fase_id) REFERENCES fase (id)
);

CREATE TABLE grupo_participante (
    grupo_id BIGINT NOT NULL,
    participante_id BIGINT NOT NULL,
    ordem INTEGER NOT NULL,
    PRIMARY KEY (grupo_id, ordem),
    CONSTRAINT uk_grupo_participante UNIQUE (grupo_id, participante_id),
    CONSTRAINT fk_grupo_participante_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id),
    CONSTRAINT fk_grupo_participante_participante FOREIGN KEY (participante_id) REFERENCES participante (id)
);

ALTER TABLE classificacao ADD COLUMN grupo_id BIGINT;
ALTER TABLE classificacao ADD CONSTRAINT fk_classificacao_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id);
CREATE INDEX idx_grupo_fase ON grupo (fase_id);
CREATE INDEX idx_grupo_participante_participante ON grupo_participante (participante_id);
CREATE INDEX idx_classificacao_grupo_rodada ON classificacao (grupo_id, rodada);

ALTER TABLE fase DROP CONSTRAINT ck_fase_tipo;
ALTER TABLE fase ADD CONSTRAINT ck_fase_tipo CHECK (tipo IN (
    'LIGA', 'ELIMINATORIA', 'MISTA', 'SWISS', 'PLAYOFF', 'OITAVAS',
    'QUARTAS', 'SEMI', 'FINAL', 'TERCEIRO_LUGAR', 'GRUPOS'
));
