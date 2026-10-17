# Profile de produção

O profile `prod` usa PostgreSQL e lê sua configuração do arquivo `.env` na raiz
do diretório em que a aplicação é executada, sem dependência adicional.
O driver já está incluído nas dependências do projeto. A configuração H2 e os
dados de demonstração continuam restritos ao profile `test`.

## Variáveis

| Variável | Valor |
| --- | --- |
| `APP_PROFILE` | `prod` para produção ou `test` para H2 local |
| `DB_URL` | URL JDBC, por exemplo `jdbc:postgresql://host:5432/futcompetitivo?sslmode=require` |
| `DB_USERNAME` | Usuário do banco |
| `DB_PASSWORD` | Senha do banco |
| `PORT` | Porta HTTP fornecida pela hospedagem; padrão `8080` |

O `.env` já foi criado com valores locais de referência e senha vazia. Preencha
com os dados reais do PostgreSQL antes de executar em produção. Para recriá-lo:

```powershell
Copy-Item .env.example .env
```

O arquivo utiliza sintaxe de propriedades `CHAVE=valor`, sem `export` e sem aspas
ao redor dos valores. `.env` é ignorado pelo Git; apenas `.env.example` deve ser
versionado. O arquivo também não é incluído no JAR: disponibilize-o no diretório
de execução da aplicação, com acesso restrito às credenciais.

Railway e Heroku não recebem o `.env` pelo Git. Nessas plataformas, configure
as mesmas chaves no painel de variáveis ou disponibilize o arquivo no runtime.
Variáveis do ambiente têm prioridade sobre os valores do `.env`.
`SPRING_PROFILES_ACTIVE=prod` também pode selecionar o profile diretamente,
com prioridade sobre `APP_PROFILE`.

Uma URL fornecida
como `postgres://...` ou `postgresql://...` não é diretamente uma URL JDBC:
configure `DB_URL` no formato `jdbc:postgresql://host:porta/banco` e informe
usuário e senha nas variáveis separadas. Ajuste os parâmetros SSL conforme
as instruções do provedor. Não versione credenciais reais.

## Schema do banco

`spring.jpa.hibernate.ddl-auto=validate` verifica se o schema existente corresponde
às entidades. O Flyway executa automaticamente as migrações pendentes de
`src/main/resources/db/migration/postgresql` antes dessa validação:

- `V1`: jogadores, equipes e participantes.
- `V2`: campeonatos, inscrições, fases, rodadas, confrontos, partidas e classificações.
- `V3`: índices dos relacionamentos e das consultas ordenadas.

Cada entidade possui sua própria sequência `<tabela>_seq`, com incremento 50,
alinhado ao `allocationSize=50` do JPA. As migrações V1 e V2 criam as sequências
diretamente, com início em 1 e default `nextval` para inserções SQL diretas.
Não há migração de conversão: esses scripts iniciais são destinados a um banco vazio.

No primeiro deploy, use um banco/schema vazio. As migrações não inserem dados
de demonstração. A conta PostgreSQL precisa de permissão para criar tabelas,
índices e sequências no schema de destino. O Flyway registra as versões na
tabela `flyway_schema_history`; não altere scripts já aplicados, crie uma nova
versão para mudanças futuras. O comando de limpeza está desabilitado.

Se o banco já tiver tabelas criadas manualmente, faça a conciliação do schema
antes de adotar o Flyway; o baseline automático não está habilitado.
No profile `test`, o Flyway fica desabilitado, mantendo o H2 criado pelo Hibernate.
Os dados locais usam IDs explícitos e ajustam as sequências após os inserts.
O teste de migrações executa os mesmos scripts em H2 no modo PostgreSQL e valida
o schema com o JPA. Ele não substitui a validação em um PostgreSQL real.

## Comportamento

- Não carrega `data-test.sql` ou outros scripts de inicialização automática.
- Desabilita o console H2 e a exibição das queries SQL.
- Desabilita Open Session in View; os serviços acessam os dados nas transações.
- Desabilita Swagger UI e OpenAPI quando springdoc estiver instalado.
- Omite detalhes internos das respostas de erro padrão do Spring Boot.
  As respostas específicas de validação e regras de negócio continuam disponíveis.

Para executar um JAR já compilado, após configurar as variáveis e provisionar o banco:

```powershell
java -jar target/futcompetitivo-api-0.0.1-SNAPSHOT.jar
```

Use Java 25. O profile `prod` é uma configuração de runtime do Spring, sem
necessidade de um profile Maven separado. A configuração não adiciona autenticação
ou autorização aos endpoints da API.

## Docker e Railway

O Dockerfile compila o projeto com o Maven Wrapper e JDK 25, depois copia apenas
o JAR executável para uma imagem com JRE 25. O processo usa um usuário sem
privilégios e ativa `prod` por padrão. As credenciais não entram na imagem.
Os testes não são executados durante o build Docker; execute a suíte antes do deploy.

```powershell
docker build -t futcompetitivo-api .
docker run --rm --env-file .env -p 8080:8080 futcompetitivo-api
```

No comando Docker, mantenha a sintaxe `CHAVE=valor` do `.env`; o Docker injeta
os valores no ambiente do container. O host `localhost` dentro do container se
refere ao próprio container, portanto `DB_URL` deve apontar para um PostgreSQL
acessível a partir dele. A porta externa deve corresponder ao `PORT` configurado.

No Railway, conecte o repositório e o Dockerfile na raiz será utilizado no build.
Configure `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` nas variáveis do serviço da API,
usando o PostgreSQL do projeto. A porta dinâmica é lida da variável `PORT`.
`APP_PROFILE=prod` já é o padrão da imagem e pode ser configurado explicitamente.
