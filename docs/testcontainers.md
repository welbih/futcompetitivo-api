# Testes com PostgreSQL e Testcontainers

Mantenha o Docker Desktop em execução, usando containers Linux, e execute com
JDK 25:

```powershell
.\mvnw.cmd test
```

Os testes de migração usam PostgreSQL 17 (`postgres:17-alpine`) por meio do
Testcontainers. O projeto cria o banco, escolhe uma porta disponível, configura
a conexão e remove os containers ao terminar. Não é necessário executar
`docker run`, criar volumes, definir portas ou fornecer credenciais locais.
Os dados de teste não usam o banco da aplicação.

Na primeira execução, as imagens PostgreSQL e de limpeza do Testcontainers
podem ser baixadas. Nas seguintes, o Docker utiliza as imagens já disponíveis.
Os containers e seus dados continuam temporários; seu ciclo de vida agora é
gerenciado pelos próprios testes. Não foi habilitada reutilização de containers
entre execuções.

## Cobertura

- `FlywayMigrationTests`: inicia a aplicação com PostgreSQL real, aplica todas
  as migrações, valida o schema com Hibernate e exercita campeonato e X1.
  O profile de testes `test-postgres` desabilita carga de demonstração e H2.
- `X1PartidasMigrationTests`: usa outro banco isolado, aplica até V8, insere
  dados antigos e executa V9. Verifica preservação dos resultados, transferência
  das partidas, ausência de colisão de IDs e restrições de integridade.
- Os demais testes e simulações continuam usando H2 com o profile `test`.

Para executar apenas os testes de migração:

```powershell
.\mvnw.cmd test "-Dtest=FlywayMigrationTests,X1PartidasMigrationTests"
```

Se o Docker não estiver disponível, esses testes falham para indicar que a
validação PostgreSQL não foi realizada. Não são ignorados silenciosamente.
Ambientes de integração contínua também precisam oferecer acesso ao Docker.
O build do Dockerfile continua sem executar testes; rode a suíte antes do deploy.

As dependências de teste têm suas versões gerenciadas pelo Spring Boot.
A configuração Spring usa
[`@ServiceConnection`](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html)
e um container como bean para coordenar seu ciclo de vida com a aplicação.
O teste de atualização usa a
[extensão JUnit do Testcontainers](https://java.testcontainers.org/quickstart/junit_5_quickstart/).
