# Criação do banco Oracle

O arquivo [`criar_schema.sql`](../src/main/resources/db/oracle/criar_schema.sql)
cria o modelo completo do sistema em um **schema Oracle vazio**, usando sintaxe
do Oracle 19c ou superior. Contém 15 tabelas, 13 sequences e 24 índices explícitos,
além dos índices criados pelas chaves primárias e restrições únicas.

Conecte-se com o usuário proprietário do schema, com permissões para criar
tabelas e sequences e quota no tablespace. No SQL Developer, abra o arquivo e
execute como script (F5). No SQL*Plus ou SQLcl, a partir da raiz do projeto:

```sql
WHENEVER SQLERROR EXIT SQL.SQLCODE
@src/main/resources/db/oracle/criar_schema.sql
```

O script cria diretamente o estado final das migrações PostgreSQL V1 a V9.
Inclui `x1`, a tabela compartilhada `partida`, campeonatos, grupos, classificações
e cadastros. Não cria as tabelas antigas `confronto_pessoal` e `partida_pessoal`.
Não apaga objetos, não insere dados iniciais e não cria o histórico interno do
Flyway. Destina-se à primeira execução em schema vazio.

Os IDs usam `NUMBER(19)` e sequences com incremento 50, conforme o
`allocationSize` das entidades. A sequence do X1 mantém o nome
`confronto_pessoal_seq`, pois esse é o nome usado no mapeamento JPA atual.
Os booleanos usam `NUMBER(1)`, com `0` e `1` e restrições de domínio.
Os textos usam `VARCHAR2(255 CHAR)`. Os relacionamentos e campos opcionais
mantêm a nulabilidade do modelo atual, inclusive os vínculos de coleções que
o Hibernate preenche após inserir as entidades filhas.

O uso de [`VARCHAR2` e `NUMBER`](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Data-Types.html)
e de [`sequence.NEXTVAL` em valores padrão](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/CREATE-TABLE.html)
segue a sintaxe Oracle. Os identificadores do script têm no máximo 30 caracteres.

Foi feita uma execução de verificação em H2 no modo Oracle, comparando todas as
tabelas e colunas com o resultado das migrações PostgreSQL, além de inserir um X1
com partida e verificar sequences e restrições booleanas. Essa verificação não
substitui execução em uma instância Oracle real.

Este arquivo é um script independente. O profile de produção atual continua
configurado para PostgreSQL. Para executar a API sobre Oracle, ainda é necessário
configurar o driver JDBC, a conexão e o fluxo de migrações específico desse banco.
