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
