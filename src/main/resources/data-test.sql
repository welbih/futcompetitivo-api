INSERT INTO jogador (id, nome, apelido, ativo) VALUES (1, 'Welber', 'Sniper', true);
INSERT INTO jogador (id, nome, apelido, ativo) VALUES (2, 'Geilson', 'Kirito', true);
INSERT INTO jogador (id, nome, apelido, ativo) VALUES (3, 'Osmar', NULL, true);
INSERT INTO jogador (id, nome, apelido, ativo) VALUES (4, 'Caliton', NULL, true);
INSERT INTO jogador (id, nome, apelido, ativo) VALUES (5, 'Graciel', NULL, true);
INSERT INTO jogador (id, nome, apelido, ativo) VALUES (6, 'Luciano', NULL, true);
INSERT INTO jogador (id, nome, apelido, ativo) VALUES (7, 'Mateus', NULL, true);


INSERT INTO equipe(id, nome, logo_url, ativo) VALUES (1, 'Real Madrid', 'https://logodownload.org/wp-content/uploads/2016/03/real-madrid-logo-2-1.png', true);
INSERT INTO equipe(id, nome, logo_url, ativo) VALUES (2, 'Barcelona', 'https://logodownload.org/wp-content/uploads/2015/05/Barcelona-logo-escudo.png', true);
INSERT INTO equipe(id, nome, logo_url, ativo) VALUES (3, 'Manchester City', 'https://logodownload.org/wp-content/uploads/2017/02/manchester-city-fc-logo-escudo-badge.png', true);
INSERT INTO equipe(id, nome, logo_url, ativo) VALUES (4, 'Atlético de Madrid', 'https://logodownload.org/wp-content/uploads/2017/02/atletico-madrid-logo-4-1.png', true);
INSERT INTO equipe(id, nome, logo_url, ativo) VALUES (5, 'Chelsea', 'https://logodownload.org/wp-content/uploads/2017/02/chelsea-fc-logo-escudo.png', true);
INSERT INTO equipe(id, nome, logo_url, ativo) VALUES (6, 'PSG', 'https://logodownload.org/wp-content/uploads/2017/02/psg-logo-escudo-paris-saint-germain.png', true);
INSERT INTO equipe(id, nome, logo_url, ativo) VALUES (7, 'Arsenal', 'https://raw.githubusercontent.com/luukhopman/football-logos/refs/heads/master/logos/England%20-%20Premier%20League/Arsenal%20FC.png', true);
INSERT INTO equipe(id, nome, logo_url, ativo) VALUES (8, 'Milan', 'https://logodownload.org/wp-content/uploads/2016/09/milan-logo-1.png', true);

INSERT INTO participante (id, jogador_id, equipe_id, ativo)
SELECT 1, j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Welber' AND e.nome = 'Real Madrid';

INSERT INTO participante (id, jogador_id, equipe_id, ativo)
SELECT 2, j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Geilson' AND e.nome = 'Barcelona';

INSERT INTO participante (id, jogador_id, equipe_id, ativo)
SELECT 3, j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Osmar' AND e.nome = 'Manchester City';

INSERT INTO participante (id, jogador_id, equipe_id, ativo)
SELECT 4, j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Caliton' AND e.nome = 'Atlético de Madrid';

INSERT INTO participante (id, jogador_id, equipe_id, ativo)
SELECT 5, j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Graciel' AND e.nome = 'Chelsea';

INSERT INTO participante (id, jogador_id, equipe_id, ativo)
SELECT 6, j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Luciano' AND e.nome = 'PSG';

INSERT INTO participante (id, jogador_id, equipe_id, ativo)
SELECT 7, j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Mateus' AND e.nome = 'Arsenal';

-- Alinha o primeiro bloco de 50 IDs acima dos registros iniciais.
ALTER SEQUENCE jogador_seq RESTART WITH 57;
ALTER SEQUENCE equipe_seq RESTART WITH 58;
ALTER SEQUENCE participante_seq RESTART WITH 57;
