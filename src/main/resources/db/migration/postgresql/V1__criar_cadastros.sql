CREATE SEQUENCE organizacao_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE organizacao (
    id BIGINT DEFAULT nextval('organizacao_seq') PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE SEQUENCE jogador_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE jogador (
    id BIGINT DEFAULT nextval('jogador_seq') PRIMARY KEY,
    nome VARCHAR(255),
    apelido VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE SEQUENCE equipe_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE equipe (
    id BIGINT DEFAULT nextval('equipe_seq') PRIMARY KEY,
    nome VARCHAR(255),
    logo_url VARCHAR(255),
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE SEQUENCE participante_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE participante (
    id BIGINT DEFAULT nextval('participante_seq') PRIMARY KEY,
    jogador_id BIGINT NOT NULL,
    equipe_id BIGINT NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_participante_jogador FOREIGN KEY (jogador_id) REFERENCES jogador (id),
    CONSTRAINT fk_participante_equipe FOREIGN KEY (equipe_id) REFERENCES equipe (id)
);
