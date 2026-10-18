# Campeonatos

Adaptação das classes de campeonato do projeto `futebol-competitivo` para as
camadas desta API. O projeto de origem permanece intacto.

## Organização

- `entity`: `Campeonato`, `Fase`, `Rodada`, `Partida`, `Resultado`, `Confronto`,
  `Classificacao`, `ClassificacaoParticipante` e seus enums, com persistência JPA.
- `repository`: `CampeonatoRepository` substitui o armazenamento em memória.
- `service`: cadastro, início, geração das fases, resultados e classificação.
  As estratégias de liga completa/parcial e Swiss ficam nos serviços, em vez de
  subclasses de entidades. `ConfrontoService` reúne jogo único e ida e volta.
- `service/classificacao`: critérios de desempate adaptados para participantes.
- `dto` e `mapper`: dados de entrada/saída, sem expor entidades JPA diretamente.
- `controller`: endpoints HTTP que delegam as operações aos serviços.

Na criação e atualização, o campeonato recebe uma lista de seleções com
`jogadorId` e `equipeId`. A API sempre cria novos participantes na mesma transação
do campeonato, mesmo que a combinação já exista em outro campeonato ou cadastro.
Jogadores e equipes devem estar previamente cadastrados.
Na atualização de um campeonato ainda não iniciado, a lista também recebe novos participantes.
Trocar a seleção no campeonato não modifica o participante de outros campeonatos.

## Endpoints

| Método | Caminho | Operação |
| --- | --- | --- |
| POST | `/campeonatos` | Criar |
| GET | `/campeonatos` | Listar |
| GET | `/campeonatos/{id}` | Consultar com calendário e histórico |
| PUT | `/campeonatos/{id}` | Atualizar enquanto estiver `CRIADO` |
| POST | `/campeonatos/{id}/iniciar` | Gerar a estrutura e iniciar |
| POST | `/campeonatos/{id}/rodadas/swiss` | Gerar a próxima rodada suíça |
| PUT | `/campeonatos/{id}/partidas/{partidaId}/resultado` | Registrar resultado |
| GET | `/campeonatos/{id}/classificacao` | Consultar a última classificação |
| GET | `/campeonatos/{id}/classificados?quantidade=2` | Consultar os primeiros colocados |

### Criar

```json
{
  "organizacaoId": 1,
  "edicao": 2026,
  "participantes": [
    {"jogadorId": 1, "equipeId": 1},
    {"jogadorId": 2, "equipeId": 2},
    {"jogadorId": 3, "equipeId": 3},
    {"jogadorId": 4, "equipeId": 4}
  ]
}
```

Organização ativa, edição positiva e lista com no mínimo 4 participantes são obrigatórios.
O mesmo jogador não pode aparecer mais de uma vez no campeonato, mesmo com equipes diferentes.
Essa regra vale para criação e atualização. Participante, jogador e equipe precisam estar
ativos na criação, atualização e início do campeonato.

O campeonato não possui nome próprio: pertence a uma organização por `ManyToOne`.
Uma mesma organização pode ter vários campeonatos. Cadastre a organização em
`POST /organizacoes` com `{"nome":"Liga Local","ativo":true}` e use o ID retornado.
O CRUD inclui `GET /organizacoes`, `GET /organizacoes/{id}`, `PUT /organizacoes/{id}`
e `DELETE /organizacoes/{id}` (desativação lógica). A listagem e a consulta incluem
organizações inativas; `PUT` permite atualizar o nome e reativá-las. A desativação
preserva campeonatos existentes, mas impede novos vínculos e novos inícios.

### Iniciar

Ao iniciar, a lista de participantes é embaralhada antes de gerar a estrutura
de qualquer formato. A ordem sorteada fica persistida no campeonato. No Swiss,
isso define a primeira rodada; as seguintes utilizam a classificação atual.

Liga completa, com no mínimo quatro participantes:

```json
{ "formato": "LIGA", "turnoEreturno": true }
```

Liga parcial: a quantidade de jogos deve ser no mínimo 3, menor que o número de
participantes e produzir um total par quando multiplicada por esse número.

```json
{ "formato": "LIGA_PARCIAL", "jogosPorParticipante": 3 }
```

Suíço: a primeira rodada é criada ao iniciar. As seguintes são geradas após o
encerramento da rodada anterior, usando a classificação. A folga fica registrada
na rodada e não concede pontos, seguindo o comportamento da origem. O serviço
prioriza confrontos inéditos. A escolha da folga
prioriza quem teve menos folgas e, entre esses, os piores colocados, tentando
outros candidatos quando necessário para viabilizar o pareamento. Somente após
esgotar as combinações sem repetição permite revanche, minimizando a quantidade
de confrontos repetidos na rodada. Toda revanche inverte o mando do último
encontro entre os participantes, inclusive quando já houve várias revanches.

```json
{ "formato": "SWISS", "totalRodadas": 3 }
```

`ELIMINATORIO` e `MISTA` ainda não possuem geração completa no projeto de origem.
Por isso, iniciar esses formatos retorna 400 e mantém o campeonato em `CRIADO`.
Os modelos de confronto e os serviços de jogo único/ida e volta foram adaptados,
mas a geração das chaves e os formatos melhor de três/cinco continuam indisponíveis.

### Registrar resultado

```json
{ "golsMandante": 2, "golsVisitante": 1 }
```

Os gols são obrigatórios e não negativos. Liga e Swiss não utilizam pênaltis ou
gol de ouro. É necessário encerrar as rodadas em ordem. O mesmo endpoint permite
corrigir placares já registrados, inclusive após o encerramento do campeonato.
Uma partida de outro campeonato retorna 404.

Uma correção recalcula a classificação da rodada afetada e de todas as rodadas
concluídas seguintes, incluindo posições e movimentação, sem duplicar jogos ou
alterar as classificações anteriores à correção. Se a rodada ainda estiver em
andamento, sua classificação só é gerada ao encerrar todas as partidas. No Swiss,
os confrontos já gerados são preservados; próximas rodadas utilizam a classificação
corrigida. Reenviar o mesmo placar não altera os dados nem refaz os sorteios de desempate.

A classificação é gerada ao concluir cada rodada, com estatísticas acumuladas:
vitória vale três pontos, empate um e derrota zero. A ordem de desempate padrão
é pontos, vitórias, saldo, gols pró e sorteio. O critério de confronto direto está
disponível como no projeto de origem, mas não integra a sequência padrão.
O sorteio usa chaves fixas durante cada ordenação para respeitar o contrato do
comparador. Cada rodada recebe registros próprios, preservando o histórico e
evitando a contagem duplicada de partidas. Ao concluir todas as rodadas, o
campeonato passa para `FINALIZADO`.

## Validação

Os testes cobrem persistência em H2, vínculos com participantes, ida e returno,
folgas, liga parcial, Swiss, classificação histórica, confrontos e validação HTTP.
Execute com JDK 25:

```powershell
.\mvnw.cmd test "-Dspring.profiles.active=test"
```

Para acompanhar campeonatos completos com agenda, resultados simulados e
classificação após cada rodada, execute apenas a classe de simulação:

```powershell
.\mvnw.cmd test "-Dtest=CampeonatoSimulacaoTests"
```

Ela cobre liga de turno único, turno e returno, liga parcial com participantes
pares e ímpares, Swiss com folgas e Swiss com revanches. Nas ligas, a agenda
completa aparece antes dos resultados; no Swiss, aparece conforme cada rodada
é gerada. As tabelas incluem pontos, jogos, vitórias, empates, derrotas, gols,
saldo e movimentação. Os placares utilizam uma semente fixa; empates totais na
classificação continuam sujeitos ao sorteio do serviço, e a agenda inicial varia
conforme o embaralhamento dos participantes. Cada cenário cria seus
próprios participantes em H2 e desfaz os dados ao terminar. As queries SQL ficam
desabilitadas apenas nesta classe para facilitar a leitura da simulação.

### Executar cada cenário por um main

Em `src/test/java/com/datadynecode/futcompetitivo/api/simulacao`, execute o método
`public static void main(String[] args)` da classe desejada na IDE, usando JDK 25
e o classpath de testes:

| Classe | Cenário |
| --- | --- |
| `liga.LigaTurnoUnicoMain` | Sete participantes, turno único |
| `liga.LigaTurnoReturnoMain` | Sete participantes, turno e returno |
| `liga.LigaParcialImparMain` | Sete participantes, quatro jogos por participante |
| `liga.LigaParcialParMain` | Seis participantes, dois jogos por participante |
| `swiss.SwissComFolgasMain` | Sete participantes, três rodadas |
| `swiss.SwissComRevanchesMain` | Quatro participantes, cinco rodadas e inversão de mando nas revanches |

Cada main inicia o Spring sem servidor HTTP, com H2 em memória exclusivo para a
execução. Reutiliza a mesma simulação e as mesmas verificações dos testes,
imprime agenda, resultados, classificações e campeão, desfaz os dados simulados
e fecha o contexto ao terminar. Não é necessário iniciar a API previamente.

Os testes estão organizados em `controller` (contratos e validação HTTP),
`service` (regras e persistência) e `simulacao` (cenários completos).
`simulacao/suporte` contém o simulador, a definição dos cenários e o executor dos
mains, compartilhados sem instanciar classes de teste. Somente o teste de
inicialização da aplicação permanece no pacote raiz.
