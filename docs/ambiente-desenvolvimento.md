# Desenvolvimento com PostgreSQL

## Subir o banco

```powershell
docker compose -f compose.dev.yaml up -d --wait
```

| Configuracao | Valor padrao |
| --- | --- |
| Container | `futcompetitivoDB` |
| Imagem | `postgres:17-alpine` |
| Host | `localhost` |
| Porta no computador | `5433` |
| Porta no container | `5432` |
| Banco | `futcompetitivo` |
| Usuario | `futcompetitivo` |
| Senha local | `futcompetitivo_dev` |
| Volume | `futcompetitivo-dev_postgres-data` |

A porta externa e 5433 para coexistir com outros bancos locais em 5432.
O container publica a porta apenas no loopback do computador.
No IntelliJ, escolha `dev` em Active profiles e execute a aplicacao normalmente.
Por terminal:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

O `dev` utiliza `DEV_DB_URL`, `DEV_DB_PORT`, `DEV_DB_USERNAME` e `DEV_DB_PASSWORD`
para sobrescrever os valores padrao. Nao usa `DB_URL`, `DB_USERNAME` ou
`DB_PASSWORD` de producao. Com `DEV_DB_PORT` diferente, o Compose e a aplicacao
usam o mesmo valor definido no `.env`.

O Hibernate valida o schema; o Flyway executa as migrations PostgreSQL e os
repeatable scripts de `src/main/resources/db/seed/dev`. A inicializacao H2 e o
`data-test.sql` continuam exclusivos do profile `test`. O banco dev e persistente:
reiniciar a API/container preserva os dados.

## Catalogo e usuarios

O snapshot `tools/dev/dados-producao.json` foi obtido em 2026-11-19, apenas por
GETs publicos de `https://futcompetitivo.datadynecode.com/api/jogadores` e
`/api/equipes`: 21 jogadores e 12 equipes, preservando IDs, nomes, apelidos,
logos e situacao cadastral. Nao foram copiados usuarios, senhas ou competicoes.

`R__catalogo_producao.sql` importa esse catalogo somente em dev, sem sobrescrever
IDs ja existentes, e alinha as sequences de jogador/equipe com o bloco de 50
usado pelo Hibernate. `R__usuarios_desenvolvimento.sql` cria, sem duplicar:

| Login | Senha | Role |
| --- | --- | --- |
| admin@futcompetitivo.local | 123456 | ROLE_ADMIN |
| user@futcompetitivo.local | 123456 | ROLE_USER |

Os seeds nao sao carregados pelo profile `prod`. O Flyway os aplica na primeira
inicializacao e novamente se o conteudo do script mudar. Nao e necessario inserir
os usuarios manualmente nem perder o banco entre reinicios.

## Popular campeonatos e X1s

Com a API local rodando em `dev`:

```powershell
pwsh -File tools/dev/popular.ps1 -ApiUrl http://localhost:8080
```

O script autentica o admin local. Le `CLIENT_ID` e `CLIENT_SECRET` do ambiente ou
do `.env`, usando os mesmos defaults da API. Tambem aceita valores explicitos:

```powershell
pwsh -File tools/dev/popular.ps1 -ApiUrl http://localhost:8080 -ClientId futcompetitivo -ClientSecret desenvolvimento-local
```

As escritas sao limitadas a enderecos HTTP de loopback; o script rejeita uma URL
de producao. Todo o cadastro, geracao de rodadas e registro de placares usa os
endpoints da propria API, incluindo autenticacao/roles e validacoes de resultados.

O conjunto inclui 15 campeonatos, marcados por organizacoes com prefixo `[DEV]`:

- Liga finalizada e em andamento, mais liga com turno e returno.
- Liga parcial finalizada e em andamento.
- Eliminatorio finalizado e em andamento, mais ida e volta e final unica com
  fase preliminar. Mata-matas incluem disputa de terceiro lugar.
- Misto finalizado e em andamento.
- Swiss com tres rodadas finalizado e em andamento.
- Grupos com mata-mata finalizado e em andamento.

Os campeonatos em andamento ficam com duas partidas ja registradas. O script
distribui os jogadores/equipes do catalogo entre os cenarios. Os resultados
incluem vitorias, derrotas, empates e penaltis quando exigidos pelo mata-mata;
ida e volta respeitam o placar agregado para decidir os penaltis.

Sao criadas seis series X1: melhor de tres e melhor de cinco completas, duas com
encerramento antecipado/cancelamento das partidas restantes e duas em andamento.

O script e reexecutavel: encontra campeonatos por organizacao `[DEV]` e edicao,
e X1 por jogadores/tipo/configuracao. Retoma resultados pendentes sem duplicar
os cenarios existentes. Nao apaga nem reinicia registros. Edicao padrao: 2026
(pode ser alterada com `-Edicao`). O resumo de IDs/status e escrito em
`target/populacao-dev.json`.

## Parar e verificar

Na populacao validada em 2026-11-19, o banco ficou com 21 jogadores, 12 equipes,
15 campeonatos (9 finalizados e 6 em andamento), 6 X1s (4 finalizados e 2 em
andamento) e 279 partidas: 195 finalizadas, 81 agendadas e 3 canceladas. A segunda
execucao do script preservou esses totais. As estatisticas dos jogadores somaram
390 participacoes em jogos finalizados (358 em campeonato e 32 em X1), sem
duplicar partidas; o volume preservou os dados apos recriar o container.

```powershell
docker compose -f compose.dev.yaml ps
docker exec futcompetitivoDB pg_isready -U futcompetitivo -d futcompetitivo
docker compose -f compose.dev.yaml stop
```

Parar o container nao apaga o volume. Os testes do profile dev utilizam um
PostgreSQL isolado via Testcontainers; os demais testes H2 continuam isolados do
banco de desenvolvimento.
