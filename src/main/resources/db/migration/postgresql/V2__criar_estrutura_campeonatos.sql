CREATE SEQUENCE campeonato_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE campeonato (
    id BIGINT DEFAULT nextval('campeonato_seq') PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    edicao INTEGER NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT ck_campeonato_status CHECK (status IN ('CRIADO', 'EM_ANDAMENTO', 'FINALIZADO'))
);

CREATE TABLE campeonato_participante (
    campeonato_id BIGINT NOT NULL,
    participante_id BIGINT NOT NULL,
    ordem INTEGER NOT NULL,
    PRIMARY KEY (campeonato_id, ordem),
    CONSTRAINT fk_campeonato_participante_campeonato FOREIGN KEY (campeonato_id) REFERENCES campeonato (id),
    CONSTRAINT fk_campeonato_participante_participante FOREIGN KEY (participante_id) REFERENCES participante (id)
);

-- Os vinculos das colecoes unidirecionais permanecem nullable: o Hibernate
-- insere as entidades filhas e depois atualiza a FK e a ordem da colecao.
CREATE SEQUENCE fase_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE fase (
    id BIGINT DEFAULT nextval('fase_seq') PRIMARY KEY,
    campeonato_id BIGINT,
    ordem INTEGER,
    tipo VARCHAR(255),
    turno_ereturno BOOLEAN NOT NULL DEFAULT FALSE,
    jogos_por_participante INTEGER,
    total_rodadas INTEGER,
    CONSTRAINT fk_fase_campeonato FOREIGN KEY (campeonato_id) REFERENCES campeonato (id),
    CONSTRAINT ck_fase_tipo CHECK (tipo IN (
        'LIGA', 'ELIMINATORIA', 'MISTA', 'SWISS', 'PLAYOFF', 'OITAVAS',
        'QUARTAS', 'SEMI', 'FINAL', 'TERCEIRO_LUGAR'
    ))
);

CREATE SEQUENCE rodada_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE rodada (
    id BIGINT DEFAULT nextval('rodada_seq') PRIMARY KEY,
    fase_id BIGINT,
    numero INTEGER,
    status VARCHAR(255),
    folga_id BIGINT,
    CONSTRAINT fk_rodada_fase FOREIGN KEY (fase_id) REFERENCES fase (id),
    CONSTRAINT fk_rodada_folga FOREIGN KEY (folga_id) REFERENCES participante (id),
    CONSTRAINT ck_rodada_status CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'FINALIZADA'))
);

CREATE SEQUENCE confronto_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE confronto (
    id BIGINT DEFAULT nextval('confronto_seq') PRIMARY KEY,
    fase_id BIGINT,
    ordem INTEGER,
    participante_um_id BIGINT,
    participante_dois_id BIGINT,
    origem_um_id BIGINT,
    origem_dois_id BIGINT,
    tipo VARCHAR(255),
    CONSTRAINT fk_confronto_fase FOREIGN KEY (fase_id) REFERENCES fase (id),
    CONSTRAINT fk_confronto_participante_um FOREIGN KEY (participante_um_id) REFERENCES participante (id),
    CONSTRAINT fk_confronto_participante_dois FOREIGN KEY (participante_dois_id) REFERENCES participante (id),
    CONSTRAINT fk_confronto_origem_um FOREIGN KEY (origem_um_id) REFERENCES confronto (id),
    CONSTRAINT fk_confronto_origem_dois FOREIGN KEY (origem_dois_id) REFERENCES confronto (id),
    CONSTRAINT ck_confronto_tipo CHECK (tipo IN ('JOGO_UNICO', 'IDA_E_VOLTA', 'MELHOR_DE_TRES', 'MELHOR_DE_CINCO'))
);

CREATE SEQUENCE partida_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE partida (
    id BIGINT DEFAULT nextval('partida_seq') PRIMARY KEY,
    rodada_id BIGINT,
    confronto_id BIGINT,
    ordem INTEGER,
    mandante_id BIGINT NOT NULL,
    visitante_id BIGINT NOT NULL,
    status VARCHAR(255),
    -- Resultado e um embeddable opcional: todas as suas colunas podem ser NULL.
    gol_de_ouro BOOLEAN,
    gols_mandante INTEGER,
    gols_visitante INTEGER,
    penaltis_mandante INTEGER,
    penaltis_visitante INTEGER,
    CONSTRAINT fk_partida_rodada FOREIGN KEY (rodada_id) REFERENCES rodada (id),
    CONSTRAINT fk_partida_confronto FOREIGN KEY (confronto_id) REFERENCES confronto (id),
    CONSTRAINT fk_partida_mandante FOREIGN KEY (mandante_id) REFERENCES participante (id),
    CONSTRAINT fk_partida_visitante FOREIGN KEY (visitante_id) REFERENCES participante (id),
    CONSTRAINT ck_partida_status CHECK (status IN ('AGENDADA', 'EM_ANDAMENTO', 'FINALIZADA'))
);

CREATE SEQUENCE classificacao_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE classificacao (
    id BIGINT DEFAULT nextval('classificacao_seq') PRIMARY KEY,
    campeonato_id BIGINT,
    rodada INTEGER NOT NULL,
    CONSTRAINT fk_classificacao_campeonato FOREIGN KEY (campeonato_id) REFERENCES campeonato (id)
);

CREATE SEQUENCE classificacao_participante_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE classificacao_participante (
    id BIGINT DEFAULT nextval('classificacao_participante_seq') PRIMARY KEY,
    classificacao_id BIGINT,
    participante_id BIGINT NOT NULL,
    pontos INTEGER NOT NULL,
    jogos INTEGER NOT NULL,
    vitorias INTEGER NOT NULL,
    empates INTEGER NOT NULL,
    derrotas INTEGER NOT NULL,
    gols_pro INTEGER NOT NULL,
    gols_contra INTEGER NOT NULL,
    saldo_gols INTEGER NOT NULL,
    posicao_atual INTEGER NOT NULL,
    posicao_anterior INTEGER NOT NULL,
    variacao INTEGER NOT NULL,
    movimentacao VARCHAR(255),
    CONSTRAINT fk_classificacao_participante_classificacao FOREIGN KEY (classificacao_id) REFERENCES classificacao (id),
    CONSTRAINT fk_classificacao_participante_participante FOREIGN KEY (participante_id) REFERENCES participante (id),
    CONSTRAINT ck_classificacao_movimentacao CHECK (movimentacao IN ('SUBIU', 'DESCEU', 'MANTEVE'))
);
