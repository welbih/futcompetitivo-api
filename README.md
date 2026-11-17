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

## Testes

Mantenha os testes em `src/test/java/com/datadynecode/futcompetitivo/api`, espelhando os pacotes das classes testadas.

Com JDK 25 configurado e Docker Desktop em execução (containers Linux), execute:

```powershell
.\mvnw.cmd test
```

Os testes de migração usam PostgreSQL real com Testcontainers, que gerencia
automaticamente os bancos temporários. Os demais testes continuam em H2.
Veja [Testcontainers](docs/testcontainers.md) para detalhes.

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

## Autenticacao

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

No ambiente local (`APP_PROFILE=test`), as chaves RSA sao temporarias e as
Autorizacoes ficam em memoria. Defaults locais: client `futcompetitivo`, secret
`desenvolvimento-local`, duracao de 3600 segundos. Em producao configure
`CLIENT_ID`, `CLIENT_SECRET`, `JWT_ISSUER`, `JWT_JWK` (RSA privada, 2048 bits,
com `kid`) e `CORS_ORIGINS`; as autorizacoes sao persistidas no PostgreSQL.
