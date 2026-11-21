-- PostgreSQL. Executar inteiro, uma unica vez, no banco que contem o campeonato 202.
-- Grupo A: participantes 252..258; Grupo B: 259..265; quatro vagas por grupo.
-- Turno unico: 7 rodadas, 42 jogos, sem resultados. Nao altera o backend.
-- Reexecucao ou divergencia no cadastro abortam a transacao sem apagar dados.
BEGIN;
SET LOCAL lock_timeout = '10s';
SET LOCAL statement_timeout = '60s';

DO $iniciar_campeonato_202$
DECLARE
    v_campeonato CONSTANT BIGINT := 202;
    v_esperados CONSTANT BIGINT[] := ARRAY[252,253,254,255,256,257,258,259,260,261,262,263,264,265];
    v_status TEXT;
    v_encontrados BIGINT[];
    v_ordens INTEGER[];
    v_fase BIGINT;
    v_grupo BIGINT;
    v_rodadas BIGINT[] := ARRAY[]::BIGINT[];
    v_lista BIGINT[];
    v_rodada BIGINT;
    v_mandante BIGINT;
    v_visitante BIGINT;
    v_troca BIGINT;
    v_indice_grupo INTEGER;
    v_numero INTEGER;
    v_par INTEGER;
    v_ordem INTEGER;
BEGIN
    SELECT status INTO v_status FROM campeonato WHERE id = v_campeonato FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Campeonato 202 nao encontrado neste banco.';
    END IF;
    IF v_status <> 'CRIADO' THEN
        RAISE EXCEPTION 'Campeonato 202 precisa estar CRIADO. Status atual: %. Nenhuma alteracao realizada.', v_status;
    END IF;
    IF EXISTS (SELECT 1 FROM fase WHERE campeonato_id = v_campeonato)
       OR EXISTS (SELECT 1 FROM classificacao WHERE campeonato_id = v_campeonato) THEN
        RAISE EXCEPTION 'Campeonato 202 ja possui fases ou classificacoes. Nenhuma alteracao realizada.';
    END IF;
    PERFORM 1 FROM campeonato_participante WHERE campeonato_id = v_campeonato ORDER BY ordem FOR UPDATE;
    SELECT array_agg(participante_id ORDER BY ordem), array_agg(ordem ORDER BY ordem)
      INTO v_encontrados, v_ordens FROM campeonato_participante WHERE campeonato_id = v_campeonato;
    IF v_encontrados IS DISTINCT FROM v_esperados
       OR v_ordens IS DISTINCT FROM ARRAY[0,1,2,3,4,5,6,7,8,9,10,11,12,13] THEN
        RAISE EXCEPTION 'Participantes/ordem diferentes do CSV informado. Esperado: %. Encontrado: %.', v_esperados, v_encontrados;
    END IF;
    IF EXISTS (
        SELECT 1 FROM campeonato c JOIN organizacao o ON o.id = c.organizacao_id
        WHERE c.id = v_campeonato AND NOT o.ativo
    ) THEN
        RAISE EXCEPTION 'A organizacao do campeonato deve estar ativa.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM participante p JOIN jogador j ON j.id = p.jogador_id JOIN equipe e ON e.id = p.equipe_id
        WHERE p.id = ANY(v_esperados) AND (NOT p.ativo OR NOT j.ativo OR NOT e.ativo)
    ) THEN
        RAISE EXCEPTION 'Existem participantes, jogadores ou equipes inativos no campeonato 202.';
    END IF;
    IF (SELECT count(DISTINCT jogador_id) FROM participante WHERE id = ANY(v_esperados)) <> 14 THEN
        RAISE EXCEPTION 'O campeonato deve conter 14 jogadores diferentes.';
    END IF;

    INSERT INTO fase (id, campeonato_id, ordem, tipo, turno_ereturno, total_rodadas,
                      quantidade_classificados, formato_eliminatorio, disputa_terceiro_lugar)
    VALUES (nextval('fase_seq'), v_campeonato, 1, 'GRUPOS', FALSE, 7, 8, NULL, FALSE)
    RETURNING id INTO v_fase;

    FOR v_numero IN 1..7 LOOP
        INSERT INTO rodada (id, fase_id, numero, status, folga_id)
        VALUES (nextval('rodada_seq'), v_fase, v_numero,
                CASE WHEN v_numero = 1 THEN 'EM_ANDAMENTO' ELSE 'PENDENTE' END, NULL)
        RETURNING id INTO v_rodada;
        v_rodadas := array_append(v_rodadas, v_rodada);
    END LOOP;

    FOR v_indice_grupo IN 1..2 LOOP
        INSERT INTO grupo (id, fase_id, nome)
        VALUES (nextval('grupo_seq'), v_fase, CASE WHEN v_indice_grupo = 1 THEN 'Grupo A' ELSE 'Grupo B' END)
        RETURNING id INTO v_grupo;

        v_lista := v_esperados[((v_indice_grupo - 1) * 7 + 1):(v_indice_grupo * 7)];
        FOR v_par IN 1..7 LOOP
            INSERT INTO grupo_participante (grupo_id, participante_id, ordem)
            VALUES (v_grupo, v_lista[v_par], v_par - 1);
        END LOOP;

        -- Metodo circular usado pelo gerador de liga: o oitavo lugar e uma folga.
        v_lista := array_append(v_lista, NULL::BIGINT);
        FOR v_numero IN 1..7 LOOP
            v_ordem := (v_indice_grupo - 1) * 3;
            FOR v_par IN 1..4 LOOP
                v_mandante := v_lista[v_par];
                v_visitante := v_lista[9 - v_par];
                IF v_mandante IS NULL OR v_visitante IS NULL THEN
                    CONTINUE;
                END IF;
                IF mod(v_numero - 1, 2) = 1 THEN
                    v_troca := v_mandante; v_mandante := v_visitante; v_visitante := v_troca;
                END IF;
                INSERT INTO partida (id, rodada_id, confronto_id, ordem, mandante_id, visitante_id,
                                     status, gol_de_ouro, gols_mandante, gols_visitante,
                                     penaltis_mandante, penaltis_visitante)
                VALUES (nextval('partida_seq'), v_rodadas[v_numero], NULL, v_ordem,
                        v_mandante, v_visitante, 'AGENDADA', NULL, NULL, NULL, NULL, NULL);
                v_ordem := v_ordem + 1;
            END LOOP;
            v_lista := ARRAY[v_lista[1], v_lista[8]] || v_lista[2:7];
        END LOOP;
    END LOOP;

    IF (SELECT count(*) FROM partida p JOIN rodada r ON r.id = p.rodada_id WHERE r.fase_id = v_fase) <> 42 THEN
        RAISE EXCEPTION 'Falha na verificacao da agenda: esperado 42 jogos. Transacao cancelada.';
    END IF;
    IF EXISTS (
        SELECT LEAST(p.mandante_id, p.visitante_id), GREATEST(p.mandante_id, p.visitante_id)
        FROM partida p JOIN rodada r ON r.id = p.rodada_id WHERE r.fase_id = v_fase
        GROUP BY LEAST(p.mandante_id, p.visitante_id), GREATEST(p.mandante_id, p.visitante_id)
        HAVING count(*) <> 1
    ) THEN
        RAISE EXCEPTION 'A agenda contem confrontos repetidos. Transacao cancelada.';
    END IF;
    UPDATE campeonato SET status = 'EM_ANDAMENTO' WHERE id = v_campeonato;
    RAISE NOTICE 'Campeonato 202 iniciado: Grupo A 252..258, Grupo B 259..265, 7 rodadas, 42 partidas, 8 classificados.';
END;
$iniciar_campeonato_202$;

-- Agenda gerada. IDs abaixo sao de PARTICIPANTES; nomes e equipes sao consultados nos cadastros reais.
SELECT r.numero AS rodada, g.nome AS grupo, p.id AS partida_id,
       p.mandante_id, jm.nome AS mandante, em.nome AS equipe_mandante,
       p.visitante_id, jv.nome AS visitante, ev.nome AS equipe_visitante, p.status
FROM partida p
JOIN rodada r ON r.id = p.rodada_id
JOIN fase f ON f.id = r.fase_id
JOIN grupo_participante gp ON gp.participante_id = p.mandante_id
JOIN grupo g ON g.id = gp.grupo_id AND g.fase_id = f.id
JOIN participante pm ON pm.id = p.mandante_id JOIN jogador jm ON jm.id = pm.jogador_id JOIN equipe em ON em.id = pm.equipe_id
JOIN participante pv ON pv.id = p.visitante_id JOIN jogador jv ON jv.id = pv.jogador_id JOIN equipe ev ON ev.id = pv.equipe_id
WHERE f.campeonato_id = 202
ORDER BY r.numero, g.nome, p.ordem;

-- Duas folgas por rodada (uma em cada grupo); nenhuma partida ficticia e criada.
SELECT r.numero AS rodada, g.nome AS grupo, gp.participante_id AS participante_folga
FROM fase f JOIN rodada r ON r.fase_id = f.id JOIN grupo g ON g.fase_id = f.id
JOIN grupo_participante gp ON gp.grupo_id = g.id
WHERE f.campeonato_id = 202 AND NOT EXISTS (
    SELECT 1 FROM partida p WHERE p.rodada_id = r.id
      AND (p.mandante_id = gp.participante_id OR p.visitante_id = gp.participante_id)
)
ORDER BY r.numero, g.nome;

COMMIT;
