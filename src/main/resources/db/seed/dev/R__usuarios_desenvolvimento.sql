-- Exclusivo do profile dev, executado pelo Flyway apos as migrations.
-- Senha dos dois usuarios: 123456 (hash BCrypt).
INSERT INTO roles (id, authority)
SELECT nextval('roles_seq'), 'ROLE_ADMIN' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE authority = 'ROLE_ADMIN');

INSERT INTO roles (id, authority)
SELECT nextval('roles_seq'), 'ROLE_USER' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE authority = 'ROLE_USER');

INSERT INTO usuarios (id, nome, sobrenome, email, senha, ativo)
SELECT nextval('usuarios_seq'), 'Admin', 'Desenvolvimento', 'admin@futcompetitivo.local',
       '$2a$10$hmebfYHcL6GMRtfh8k5VVOWfXtXSkYEa/VeZBEmPm8m6S3vOCXdXm', TRUE
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE email = 'admin@futcompetitivo.local');

INSERT INTO usuarios (id, nome, sobrenome, email, senha, ativo)
SELECT nextval('usuarios_seq'), 'User', 'Desenvolvimento', 'user@futcompetitivo.local',
       '$2a$10$hmebfYHcL6GMRtfh8k5VVOWfXtXSkYEa/VeZBEmPm8m6S3vOCXdXm', TRUE
WHERE NOT EXISTS (SELECT 1 FROM usuarios WHERE email = 'user@futcompetitivo.local');

INSERT INTO usuarios_roles (usuario_id, role_id)
SELECT u.id, r.id FROM usuarios u CROSS JOIN roles r
WHERE ((u.email = 'admin@futcompetitivo.local' AND r.authority = 'ROLE_ADMIN')
    OR (u.email = 'user@futcompetitivo.local' AND r.authority = 'ROLE_USER'))
AND NOT EXISTS (SELECT 1 FROM usuarios_roles ur WHERE ur.usuario_id = u.id AND ur.role_id = r.id);
