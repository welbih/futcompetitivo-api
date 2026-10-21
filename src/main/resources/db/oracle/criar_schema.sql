-- FUTCOMPETITIVO - schema completo para Oracle 19c ou superior.
-- Executar uma unica vez em schema vazio com permissoes CREATE TABLE e CREATE SEQUENCE e quota no tablespace.
-- Modelo final das migracoes PostgreSQL V1 a V9; sem carga inicial e sem historico Flyway.
-- Booleanos NUMBER(1): 0 = falso, 1 = verdadeiro.
-- INCREMENT BY 50 corresponde ao allocationSize das entidades JPA.
-- Os nomes das sequences seguem o JPA atual, inclusive confronto_pessoal_seq para x1.
-- DEFAULT sequence.NEXTVAL permite inserts manuais sem informar ID.
-- FKs e posicoes de colecoes unidirecionais permanecem nullable para o Hibernate.

-- SEQUENCES

CREATE SEQUENCE organizacao_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE jogador_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE equipe_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE participante_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE campeonato_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE fase_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE rodada_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE confronto_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE partida_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE classificacao_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE classificacao_participante_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE grupo_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;
CREATE SEQUENCE confronto_pessoal_seq START WITH 1 INCREMENT BY 50 NOCYCLE CACHE 20;

-- TABELAS, CHAVES E RESTRICOES

CREATE TABLE organizacao (
    id NUMBER(19) DEFAULT organizacao_seq.NEXTVAL,
    nome VARCHAR2(255 CHAR) NOT NULL,
    ativo NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_organizacao PRIMARY KEY (id),
    CONSTRAINT ck_organizacao_ativo CHECK (ativo IN (0, 1))
);

CREATE TABLE jogador (
    id NUMBER(19) DEFAULT jogador_seq.NEXTVAL,
    nome VARCHAR2(255 CHAR),
    apelido VARCHAR2(255 CHAR),
    ativo NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_jogador PRIMARY KEY (id),
    CONSTRAINT ck_jogador_ativo CHECK (ativo IN (0, 1))
);

CREATE TABLE equipe (
    id NUMBER(19) DEFAULT equipe_seq.NEXTVAL,
    nome VARCHAR2(255 CHAR),
    logo_url VARCHAR2(255 CHAR),
    ativo NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_equipe PRIMARY KEY (id),
    CONSTRAINT ck_equipe_ativo CHECK (ativo IN (0, 1))
);

CREATE TABLE participante (
    id NUMBER(19) DEFAULT participante_seq.NEXTVAL,
    jogador_id NUMBER(19) NOT NULL,
    equipe_id NUMBER(19) NOT NULL,
    ativo NUMBER(1) DEFAULT 1 NOT NULL,
    CONSTRAINT pk_participante PRIMARY KEY (id),
    CONSTRAINT fk_participante_jogador FOREIGN KEY (jogador_id) REFERENCES jogador (id),
    CONSTRAINT fk_participante_equipe FOREIGN KEY (equipe_id) REFERENCES equipe (id),
    CONSTRAINT ck_participante_ativo CHECK (ativo IN (0, 1))
);

CREATE TABLE campeonato (
    id NUMBER(19) DEFAULT campeonato_seq.NEXTVAL,
    organizacao_id NUMBER(19) NOT NULL,
    edicao NUMBER(10) NOT NULL,
    status VARCHAR2(255 CHAR) NOT NULL,
    CONSTRAINT pk_campeonato PRIMARY KEY (id),
    CONSTRAINT fk_campeonato_organizacao FOREIGN KEY (organizacao_id) REFERENCES organizacao (id),
    CONSTRAINT ck_campeonato_status CHECK (status IN ('CRIADO', 'EM_ANDAMENTO', 'FINALIZADO'))
);

CREATE TABLE campeonato_participante (
    campeonato_id NUMBER(19) NOT NULL,
    participante_id NUMBER(19) NOT NULL,
    ordem NUMBER(10) NOT NULL,
    CONSTRAINT pk_campeonato_participante PRIMARY KEY (campeonato_id, ordem),
    CONSTRAINT fk_camp_part_camp FOREIGN KEY (campeonato_id) REFERENCES campeonato (id),
    CONSTRAINT fk_camp_part_partic FOREIGN KEY (participante_id) REFERENCES participante (id)
);

CREATE TABLE fase (
    id NUMBER(19) DEFAULT fase_seq.NEXTVAL,
    campeonato_id NUMBER(19),
    ordem NUMBER(10),
    tipo VARCHAR2(255 CHAR),
    turno_ereturno NUMBER(1) DEFAULT 0 NOT NULL,
    jogos_por_participante NUMBER(10),
    total_rodadas NUMBER(10),
    quantidade_classificados NUMBER(10),
    formato_eliminatorio VARCHAR2(255 CHAR),
    disputa_terceiro_lugar NUMBER(1) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_fase PRIMARY KEY (id),
    CONSTRAINT fk_fase_campeonato FOREIGN KEY (campeonato_id) REFERENCES campeonato (id),
    CONSTRAINT ck_fase_qtd_classif CHECK (quantidade_classificados IS NULL OR quantidade_classificados >= 4),
    CONSTRAINT ck_fase_tipo CHECK (tipo IN ( 'LIGA', 'ELIMINATORIA', 'MISTA', 'SWISS', 'PLAYOFF', 'OITAVAS', 'QUARTAS', 'SEMI', 'FINAL', 'TERCEIRO_LUGAR', 'GRUPOS' )),
    CONSTRAINT ck_fase_formato_eliminatorio CHECK ( formato_eliminatorio IS NULL OR formato_eliminatorio IN ( 'JOGO_UNICO', 'IDA_E_VOLTA', 'IDA_E_VOLTA_FINAL_UNICA' ) ),
    CONSTRAINT ck_fase_turno_ereturno CHECK (turno_ereturno IN (0, 1)),
    CONSTRAINT ck_fase_disputa_terceiro_lugar CHECK (disputa_terceiro_lugar IN (0, 1))
);

CREATE TABLE rodada (
    id NUMBER(19) DEFAULT rodada_seq.NEXTVAL,
    fase_id NUMBER(19),
    numero NUMBER(10),
    status VARCHAR2(255 CHAR),
    folga_id NUMBER(19),
    CONSTRAINT pk_rodada PRIMARY KEY (id),
    CONSTRAINT fk_rodada_fase FOREIGN KEY (fase_id) REFERENCES fase (id),
    CONSTRAINT fk_rodada_folga FOREIGN KEY (folga_id) REFERENCES participante (id),
    CONSTRAINT ck_rodada_status CHECK (status IN ('PENDENTE', 'EM_ANDAMENTO', 'FINALIZADA'))
);

CREATE TABLE confronto (
    id NUMBER(19) DEFAULT confronto_seq.NEXTVAL,
    fase_id NUMBER(19),
    ordem NUMBER(10),
    participante_um_id NUMBER(19),
    participante_dois_id NUMBER(19),
    origem_um_id NUMBER(19),
    origem_dois_id NUMBER(19),
    tipo VARCHAR2(255 CHAR),
    terceiro_lugar NUMBER(1) DEFAULT 0 NOT NULL,
    CONSTRAINT pk_confronto PRIMARY KEY (id),
    CONSTRAINT fk_confronto_fase FOREIGN KEY (fase_id) REFERENCES fase (id),
    CONSTRAINT fk_confronto_participante_um FOREIGN KEY (participante_um_id) REFERENCES participante (id),
    CONSTRAINT fk_confronto_participante_dois FOREIGN KEY (participante_dois_id) REFERENCES participante (id),
    CONSTRAINT fk_confronto_origem_um FOREIGN KEY (origem_um_id) REFERENCES confronto (id),
    CONSTRAINT fk_confronto_origem_dois FOREIGN KEY (origem_dois_id) REFERENCES confronto (id),
    CONSTRAINT ck_confronto_tipo CHECK (tipo IN ('JOGO_UNICO', 'IDA_E_VOLTA', 'MELHOR_DE_TRES', 'MELHOR_DE_CINCO')),
    CONSTRAINT ck_confronto_terceiro_lugar CHECK (terceiro_lugar IN (0, 1))
);

CREATE TABLE grupo (
    id NUMBER(19) DEFAULT grupo_seq.NEXTVAL,
    fase_id NUMBER(19),
    nome VARCHAR2(255 CHAR) NOT NULL,
    CONSTRAINT pk_grupo PRIMARY KEY (id),
    CONSTRAINT fk_grupo_fase FOREIGN KEY (fase_id) REFERENCES fase (id)
);

CREATE TABLE classificacao (
    id NUMBER(19) DEFAULT classificacao_seq.NEXTVAL,
    campeonato_id NUMBER(19),
    rodada NUMBER(10) NOT NULL,
    grupo_id NUMBER(19),
    CONSTRAINT pk_classificacao PRIMARY KEY (id),
    CONSTRAINT fk_classificacao_campeonato FOREIGN KEY (campeonato_id) REFERENCES campeonato (id),
    CONSTRAINT fk_classificacao_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id)
);

CREATE TABLE classificacao_participante (
    id NUMBER(19) DEFAULT classificacao_participante_seq.NEXTVAL,
    classificacao_id NUMBER(19),
    participante_id NUMBER(19) NOT NULL,
    pontos NUMBER(10) NOT NULL,
    jogos NUMBER(10) NOT NULL,
    vitorias NUMBER(10) NOT NULL,
    empates NUMBER(10) NOT NULL,
    derrotas NUMBER(10) NOT NULL,
    gols_pro NUMBER(10) NOT NULL,
    gols_contra NUMBER(10) NOT NULL,
    saldo_gols NUMBER(10) NOT NULL,
    posicao_atual NUMBER(10) NOT NULL,
    posicao_anterior NUMBER(10) NOT NULL,
    variacao NUMBER(10) NOT NULL,
    movimentacao VARCHAR2(255 CHAR),
    CONSTRAINT pk_classificacao_participante PRIMARY KEY (id),
    CONSTRAINT fk_class_part_classif FOREIGN KEY (classificacao_id) REFERENCES classificacao (id),
    CONSTRAINT fk_class_part_partic FOREIGN KEY (participante_id) REFERENCES participante (id),
    CONSTRAINT ck_classificacao_movimentacao CHECK (movimentacao IN ('SUBIU', 'DESCEU', 'MANTEVE'))
);

CREATE TABLE grupo_participante (
    grupo_id NUMBER(19) NOT NULL,
    participante_id NUMBER(19) NOT NULL,
    ordem NUMBER(10) NOT NULL,
    CONSTRAINT pk_grupo_participante PRIMARY KEY (grupo_id, ordem),
    CONSTRAINT uk_grupo_participante UNIQUE (grupo_id, participante_id),
    CONSTRAINT fk_grupo_participante_grupo FOREIGN KEY (grupo_id) REFERENCES grupo (id),
    CONSTRAINT fk_grupo_partic_partic FOREIGN KEY (participante_id) REFERENCES participante (id)
);

CREATE TABLE x1 (
    id NUMBER(19) DEFAULT confronto_pessoal_seq.NEXTVAL,
    participante_um_id NUMBER(19) NOT NULL,
    participante_dois_id NUMBER(19) NOT NULL,
    tipo VARCHAR2(255 CHAR) NOT NULL,
    encerrar_antecipadamente NUMBER(1) DEFAULT 0 NOT NULL,
    status VARCHAR2(255 CHAR) NOT NULL,
    CONSTRAINT pk_x1 PRIMARY KEY (id),
    CONSTRAINT fk_x1_partic_um FOREIGN KEY (participante_um_id) REFERENCES participante (id),
    CONSTRAINT fk_x1_partic_dois FOREIGN KEY (participante_dois_id) REFERENCES participante (id),
    CONSTRAINT ck_x1_partics CHECK (participante_um_id <> participante_dois_id),
    CONSTRAINT ck_confronto_pessoal_tipo CHECK (tipo IN ('MELHOR_DE_TRES', 'MELHOR_DE_CINCO')),
    CONSTRAINT ck_confronto_pessoal_status CHECK (status IN ('EM_ANDAMENTO', 'FINALIZADO')),
    CONSTRAINT ck_x1_encerrar_antecipadamente CHECK (encerrar_antecipadamente IN (0, 1))
);

CREATE TABLE partida (
    id NUMBER(19) DEFAULT partida_seq.NEXTVAL,
    rodada_id NUMBER(19),
    confronto_id NUMBER(19),
    ordem NUMBER(10),
    mandante_id NUMBER(19) NOT NULL,
    visitante_id NUMBER(19) NOT NULL,
    status VARCHAR2(255 CHAR),
    gol_de_ouro NUMBER(1),
    gols_mandante NUMBER(10),
    gols_visitante NUMBER(10),
    penaltis_mandante NUMBER(10),
    penaltis_visitante NUMBER(10),
    x1_id NUMBER(19),
    numero NUMBER(10),
    CONSTRAINT pk_partida PRIMARY KEY (id),
    CONSTRAINT fk_partida_rodada FOREIGN KEY (rodada_id) REFERENCES rodada (id),
    CONSTRAINT fk_partida_confronto FOREIGN KEY (confronto_id) REFERENCES confronto (id),
    CONSTRAINT fk_partida_mandante FOREIGN KEY (mandante_id) REFERENCES participante (id),
    CONSTRAINT fk_partida_visitante FOREIGN KEY (visitante_id) REFERENCES participante (id),
    CONSTRAINT fk_partida_x1 FOREIGN KEY (x1_id) REFERENCES x1 (id),
    CONSTRAINT uk_partida_x1_numero UNIQUE (x1_id, numero),
    CONSTRAINT ck_partida_x1_numero CHECK ( (x1_id IS NULL AND numero IS NULL) OR (x1_id IS NOT NULL AND numero IS NOT NULL AND numero BETWEEN 1 AND 5) ),
    CONSTRAINT ck_partida_x1_exclusiva CHECK ( x1_id IS NULL OR (rodada_id IS NULL AND confronto_id IS NULL) ),
    CONSTRAINT ck_partida_status CHECK ( status IN ('AGENDADA', 'EM_ANDAMENTO', 'FINALIZADA', 'CANCELADA') ),
    CONSTRAINT ck_partida_gol_de_ouro CHECK (gol_de_ouro IN (0, 1))
);

-- INDICES DE CONSULTA E CHAVES ESTRANGEIRAS

CREATE INDEX idx_participante_jogador ON participante (jogador_id);
CREATE INDEX idx_campeonato_organizacao ON campeonato (organizacao_id);
CREATE INDEX idx_participante_equipe ON participante (equipe_id);
CREATE INDEX idx_camp_part_partic ON campeonato_participante (participante_id);
CREATE INDEX idx_fase_campeonato_ordem ON fase (campeonato_id, ordem);
CREATE INDEX idx_rodada_fase_numero ON rodada (fase_id, numero);
CREATE INDEX idx_rodada_folga ON rodada (folga_id);
CREATE INDEX idx_confronto_fase_ordem ON confronto (fase_id, ordem);
CREATE INDEX idx_confronto_participante_um ON confronto (participante_um_id);
CREATE INDEX idx_confronto_partic_dois ON confronto (participante_dois_id);
CREATE INDEX idx_confronto_origem_um ON confronto (origem_um_id);
CREATE INDEX idx_confronto_origem_dois ON confronto (origem_dois_id);
CREATE INDEX idx_partida_rodada_ordem ON partida (rodada_id, ordem);
CREATE INDEX idx_partida_confronto_ordem ON partida (confronto_id, ordem);
CREATE INDEX idx_partida_mandante ON partida (mandante_id);
CREATE INDEX idx_partida_visitante ON partida (visitante_id);
CREATE INDEX idx_classif_camp_rodada ON classificacao (campeonato_id, rodada);
CREATE INDEX idx_class_part_posicao ON classificacao_participante (classificacao_id, posicao_atual);
CREATE INDEX idx_class_part_partic ON classificacao_participante (participante_id);
CREATE INDEX idx_grupo_fase ON grupo (fase_id);
CREATE INDEX idx_grupo_partic_partic ON grupo_participante (participante_id);
CREATE INDEX idx_classificacao_grupo_rodada ON classificacao (grupo_id, rodada);
CREATE INDEX idx_x1_partic_um ON x1 (participante_um_id);
CREATE INDEX idx_x1_partic_dois ON x1 (participante_dois_id);
