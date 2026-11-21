# Futcompetitivo API

API Java 25 com Spring Boot, Spring MVC e Spring Data JPA.

## Arquitetura em camadas

Os pacotes ficam em `src/main/java/com/datadynecode/futcompetitivo/api`:

```text
api/
|-- FutcompetitivoApiApplication.java
|-- controller/   # Endpoints REST e coordenacao das requisicoes
|-- service/      # Regras de negocio e transacoes
|-- repository/   # Consultas e persistencia com Spring Data JPA
|-- entity/       # Entidades de dominio mapeadas com JPA
|-- dto/          # Contratos de entrada e saida da API
|-- mapper/       # Conversao entre DTOs e entidades
|-- exception/    # Excecoes e tratamento centralizado de erros
`-- config/       # Configuracao do Spring e infraestrutura
```

Fluxo principal: `controller -> service -> repository -> banco de dados`.

- Controllers recebem e validam DTOs e delegam as operacoes aos services.
- Services concentram regras de negocio e limites transacionais, utilizando repositories.
- Repositories manipulam entidades e encapsulam o acesso ao banco.
- Entidades nao devem ser expostas diretamente nas respostas HTTP; use DTOs e mappers.
- Mappers apenas convertem dados, sem consultar repositories ou executar regras de negocio.
- As camadas service e repository nao devem depender de controllers ou de tipos HTTP.
- Classes concretas de service sao suficientes inicialmente; interfaces podem ser adicionadas quando houver necessidade de contratos ou implementacoes alternativas.

Cada pacote possui um `package-info.java` com sua responsabilidade. Adicione as classes de cada funcionalidade nesses pacotes conforme o dominio for implementado. A classe principal permanece no pacote raiz para permitir a descoberta automatica dos componentes pelo Spring.

## Avanco e desistencias

`PATCH /campeonatos/{id}/identificacao` permite ao ADMIN corrigir organizacao e
edicao em qualquer status, preservando a competicao. Veja
[contrato e regras](docs/identificacao-campeonato.md).

`GET /campeonatos/{id}/eventos` disponibiliza SSE publico para notificacoes
apos commit. Veja [eventos, EventSource, proxy e limitacao a uma instancia](docs/eventos-campeonatos.md).

O formato `MISTA_PARCIAL` combina liga parcial com mata-mata. O admin tambem
pode informar a agenda completa de jogos por rodada para campeonatos antigos.
Veja [configuracao, agenda manual e exemplos](docs/liga-parcial-mata-mata.md).

O inicio do mata-mata apos grupos/liga exige solicitacao explicita de `ADMIN`.
Depois disso, as rodadas eliminatorias avancam automaticamente. Desistencias
em qualquer fase aplicam W.O. de 3x0 nos jogos pendentes; dois
desistentes empatam em 0x0. Vagas de classificados desistentes sao preenchidas por
escolha manual do admin. Veja [rotas, payloads e regras](docs/mata-mata-desistencias.md).

## Confrontos definidos pelo admin

Os formatos com mata-mata aceitam `formaGeracaoMataMata: AUTOMATICA` ou
`ALEATORIA`, mantendo a prioridade dos confrontos manuais do admin.
Veja [configuracao, padroes e exemplos](docs/geracao-mata-mata.md).

O admin tambem pode definir a primeira rodada do mata-mata, incluindo mando e
folgas, no eliminatorio puro ou apos grupos/liga. Veja
[confrontos manuais, payloads e validacoes](docs/confrontos-manuais.md).

## Grupos definidos pelo admin

O formato `GRUPOS` aceita `quantidadeGrupos` (padrao 2), com pelo menos tres
participantes por grupo. A composicao manual tambem aceita varios grupos.
Veja [configuracao e criterios de classificacao](docs/quantidade-grupos.md).

`POST /campeonatos/{id}/iniciar/grupos` permite a um `ADMIN` definir manualmente
os grupos A e B de um campeonato ainda nao iniciado, preservando a composicao e
gerando a agenda sem registrar resultados. Veja [payload e regras](docs/grupos-manuais.md).

## Estatisticas individuais

Os formatos com tabela disponibilizam `classificacaoAtual`, com `parcial: true`
durante a rodada. O historico continua separado em `classificacaoPorRodada`.
Veja [contrato e regras da classificacao parcial](docs/classificacao-parcial.md).

Endpoint publico: `GET /jogadores/{id}/estatisticas?origem=TODOS`, com filtros
`CAMPEONATO`, `X1` ou `TODOS`. Inclui resumo geral, recordes de goleadas e
adversarios com mais vitorias/derrotas, preservando todos os empates.
Veja [contrato e exemplos JSON](docs/estatisticas-jogadores.md).

## Testes

Mantenha os testes em `src/test/java/com/datadynecode/futcompetitivo/api`, espelhando os pacotes das classes testadas.

Com JDK 25 configurado e Docker Desktop em execução (containers Linux), execute:

```powershell
.\mvnw.cmd test
```

Os testes de migração usam PostgreSQL real com Testcontainers, que gerencia
automaticamente os bancos temporários. Os demais testes continuam em H2.
Veja [Testcontainers](docs/testcontainers.md) para detalhes.

## Trofeu da organizacao

`POST /organizacoes` e `PUT /organizacoes/{id}` aceitam `multipart/form-data`:

- `dados`: parte JSON com Content-Type `application/json`, por exemplo `{"nome":"Liga","ativo":true}`.
- `trofeu`: arquivo PNG (`image/png`) ou SVG (`image/svg+xml`), de ate 2 MB.

O arquivo e seu tipo sao salvos no banco, na organizacao. A resposta inclui
`trofeuUrl`, por exemplo `/organizacoes/1/trofeu`. Um GET nesse caminho retorna
a imagem com seu Content-Type; organizacoes sem trofeu retornam 404.
SVGs podem incluir imagens PNG embutidas em Base64 (`data:image/png;base64,...`),
validadas como PNG. O arquivo original e preservado no banco.
SVGs devem conter apenas elementos graficos estaticos, sem scripts, atributos
de evento, estilos inline ou referencias externas.

Os endpoints JSON existentes continuam disponiveis sem imagem. Ao editar por
JSON, o trofeu salvo e preservado; o PUT multipart substitui a imagem.

Exemplo no PowerShell (ajuste o caminho do arquivo):

```powershell
curl.exe -X POST http://localhost:8080/organizacoes -F 'dados={"nome":"Liga","ativo":true};type=application/json' -F 'trofeu=@C:/imagens/trofeu.png;type=image/png'
```

O frontend pode exibir a previa do arquivo selecionado com `URL.createObjectURL`
antes de enviar o formulario. Nenhuma imagem e persistida ate o envio do cadastro.

## Partidas no retorno do mata-mata

Em fases `ELIMINATORIA`, os dados completos das partidas aparecem apenas em
`fases[].rodadas[].partidas`. Cada confronto retorna `partidaIds` em vez de
`partidas`, preservando a ordem de jogo unico ou ida e volta. Confrontos ainda
sem partidas retornam `partidaIds: []`. A regra inclui terceiro lugar e etapas
eliminatorias de campeonatos mistos/com grupos; os demais tipos de fase e o X1
mantem seu contrato anterior. O frontend deve resolver cada ID nas rodadas da fase.

## Video da partida

Os PUTs `/campeonatos/{id}/partidas/{partidaId}/resultado` e
`/x1/{id}/partidas/{partidaId}/resultado` aceitam o campo opcional `youtubeUrl`
junto ao placar, por exemplo:

```json
{"golsMandante":2,"golsVisitante":0,"youtubeUrl":"https://www.youtube.com/watch?v=video"}
```

O link e retornado no campo `youtubeUrl` da partida. Enviar `null` ou omitir
o campo no PUT limpa o link. A edicao em lote de resultados tambem aceita
`youtubeUrl` dentro de cada `resultado`.

## Remocao de X1

`DELETE /x1/{id}` remove o confronto e suas partidas, incluindo os resultados.
Retorna `204 No Content` ou `404 Not Found` quando o X1 nao existe. Jogadores,
equipes e participantes permanecem cadastrados. A exclusao de X1 e campeonato
(`DELETE /x1/{id}` e `DELETE /campeonatos/{id}`) exige Bearer JWT com `ROLE_ADMIN`.
Sem token retorna `401`; com usuario sem a role ADMIN retorna `403`.

A ativacao e inativacao de jogadores e equipes (`PUT /jogadores/{id}/ativar`,
`PUT /equipes/{id}/ativar`, `DELETE /jogadores/{id}` e
`DELETE /equipes/{id}`) tambem exigem `ROLE_ADMIN`, com os mesmos retornos
`401` e `403`. Essas operacoes preservam os registros no banco e seu historico.

A edicao de jogadores e equipes (`PUT /jogadores/{id}` e `PUT /equipes/{id}`)
exige um usuario autenticado, permitindo `USER` e `ADMIN`. Sem token retorna `401`.

Organizacoes: criar (`POST /organizacoes`) exige autenticacao (`USER` ou `ADMIN`).
Editar (`PUT /organizacoes/{id}`) e remover/inativar (`DELETE /organizacoes/{id}`)
exigem `ROLE_ADMIN`. As regras valem tanto para JSON quanto para multipart com trofeu.
Consultas de organizacoes e imagens continuam abertas.

## Autenticacao

### Usuarios de desenvolvimento

Com o profile `dev` (por `APP_PROFILE=dev` ou pelos Active profiles do IntelliJ),
a API usa o PostgreSQL do container `futcompetitivoDB`, porta local `5433`.
O Flyway cria/atualiza o schema e executa os scripts exclusivos de desenvolvimento,
incluindo o catalogo de jogadores/equipes e os usuarios:

| Email | Senha | Role |
| --- | --- | --- |
| admin@futcompetitivo.local | 123456 | ROLE_ADMIN |
| user@futcompetitivo.local | 123456 | ROLE_USER |

O script salva as senhas em BCrypt e nao duplica usuarios ou roles existentes.
Nao altera senhas de usuarios ja cadastrados. O banco fica salvo em volume Docker
e nao e recriado a cada inicializacao. O profile `prod` nao inclui esses seeds.
Veja [ambiente local e populacao dos formatos](docs/ambiente-desenvolvimento.md).

O login segue o contrato do financeiro-api: `POST /oauth2/token`, Basic Auth
com `CLIENT_ID`/`CLIENT_SECRET` e corpo `application/x-www-form-urlencoded`:

```text
grant_type=urn:futcompetitivo:params:oauth:grant-type:password
username=email-do-usuario
password=senha-do-usuario
```

O grant utiliza o identificador proprio do Futcompetitivo.
A resposta contem `access_token`, `token_type` e `expires_in`. O JWT inclui
`authorities`, `nome` e `sobrenome`.

`POST /usuarios` cadastra `{ "nome", "sobrenome", "email", "senha" }`, com
senha BCrypt e role `ROLE_USER`. As roles `ROLE_ADMIN` e `ROLE_USER` sao criadas
pela migracao V10 (PostgreSQL) e pelo seed local do profile `test` (H2).
Nenhum usuario ou senha fixa e criado em producao.

As demais rotas de negocio e de administracao de usuarios permanecem abertas, incluindo
`GET /usuarios` e `PATCH /usuarios/{id}/ativar` ou `/inativar`, sem exigir token
ou role. Se um Bearer for enviado, ele sera validado. As operacoes da propria
sessao `POST /auth/logout` e `PUT /usuarios/senha` usam o Bearer para identificar
a sessao/usuario. Logout revoga o token e usuarios inativos nao podem fazer login.

No ambiente local (`APP_PROFILE=dev` ou `test`), as chaves RSA sao temporarias e as
Autorizacoes ficam em memoria. Defaults locais: client `futcompetitivo`, secret
`desenvolvimento-local`, duracao de 3600 segundos. Em producao configure
`CLIENT_ID`, `CLIENT_SECRET`, `JWT_ISSUER`, `JWT_JWK` (RSA privada, 2048 bits,
com `kid`) e `CORS_ORIGINS`; as autorizacoes sao persistidas no PostgreSQL.
