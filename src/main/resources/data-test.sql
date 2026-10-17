INSERT INTO jogador (nome, apelido, ativo) VALUES ('Welber', 'Sniper', true);
INSERT INTO jogador (nome, apelido, ativo) VALUES ('Geilson', 'Kirito', true);
INSERT INTO jogador (nome, apelido, ativo) VALUES ('Osmar', NULL, true);
INSERT INTO jogador (nome, apelido, ativo) VALUES ('Caliton', NULL, true);
INSERT INTO jogador (nome, apelido, ativo) VALUES ('Graciel', NULL, true);
INSERT INTO jogador (nome, apelido, ativo) VALUES ('Luciano', NULL, true);
INSERT INTO jogador (nome, apelido, ativo) VALUES ('Mateus', NULL, true);


INSERT INTO equipe(nome, logo_url, ativo) VALUES ('Real Madrid', 'https://logodownload.org/wp-content/uploads/2016/03/real-madrid-logo-2-1.png', true);
INSERT INTO equipe(nome, logo_url, ativo) VALUES ('Barcelona', 'https://logodownload.org/wp-content/uploads/2015/05/Barcelona-logo-escudo.png', true);
INSERT INTO equipe(nome, logo_url, ativo) VALUES ('Manchester City', 'https://logodownload.org/wp-content/uploads/2017/02/manchester-city-fc-logo-escudo-badge.png', true);
INSERT INTO equipe(nome, logo_url, ativo) VALUES ('Atlético de Madrid', 'https://logodownload.org/wp-content/uploads/2017/02/atletico-madrid-logo-4-1.png', true);
INSERT INTO equipe(nome, logo_url, ativo) VALUES ('Chelsea', 'https://logodownload.org/wp-content/uploads/2017/02/chelsea-fc-logo-escudo.png', true);
INSERT INTO equipe(nome, logo_url, ativo) VALUES ('PSG', 'https://logodownload.org/wp-content/uploads/2017/02/psg-logo-escudo-paris-saint-germain.png', true);
INSERT INTO equipe(nome, logo_url, ativo) VALUES ('Arsenal', 'https://raw.githubusercontent.com/luukhopman/football-logos/refs/heads/master/logos/England%20-%20Premier%20League/Arsenal%20FC.png', true);
INSERT INTO equipe(nome, logo_url, ativo) VALUES ('Milan', 'https://logodownload.org/wp-content/uploads/2016/09/milan-logo-1.png', true);

INSERT INTO participante (jogador_id, equipe_id, ativo)
SELECT j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Welber' AND e.nome = 'Real Madrid';

INSERT INTO participante (jogador_id, equipe_id, ativo)
SELECT j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Geilson' AND e.nome = 'Barcelona';

INSERT INTO participante (jogador_id, equipe_id, ativo)
SELECT j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Osmar' AND e.nome = 'Manchester City';

INSERT INTO participante (jogador_id, equipe_id, ativo)
SELECT j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Caliton' AND e.nome = 'Atlético de Madrid';

INSERT INTO participante (jogador_id, equipe_id, ativo)
SELECT j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Graciel' AND e.nome = 'Chelsea';

INSERT INTO participante (jogador_id, equipe_id, ativo)
SELECT j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Luciano' AND e.nome = 'PSG';

INSERT INTO participante (jogador_id, equipe_id, ativo)
SELECT j.id, e.id, true FROM jogador j CROSS JOIN equipe e
WHERE j.nome = 'Mateus' AND e.nome = 'Arsenal';
