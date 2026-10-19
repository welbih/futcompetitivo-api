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
de qualquer formato. A primeira rodada fica `EM_ANDAMENTO` e as demais ficam
`PENDENTE`. Novos resultados só podem ser adicionados na rodada em andamento.
Ao finalizar todas as partidas dessa rodada, a próxima rodada já gerada entra
automaticamente em andamento. No Swiss, a próxima rodada entra em andamento
quando é gerada pelo endpoint correspondente. A correção de placares já registrados
continua permitida, inclusive em rodadas finalizadas.

A ordem sorteada fica persistida no campeonato. No Swiss,
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

### Eliminatório em jogo único

Inicie com `POST /campeonatos/{id}/iniciar`:

```json
{ "formato": "ELIMINATORIO" }
```

São necessários no mínimo 4 participantes. Com 4, a primeira rodada representa
as semifinais e a segunda a final. Qualquer quantidade a partir de 4 é aceita.
A API sorteia os participantes e ajusta a chave à próxima potência de 2,
concedendo folgas somente na primeira etapa. Com 5, há 1 jogo preliminar e 3
avanços diretos; com 6, 2 jogos e 2 avanços; com 7, 3 jogos e 1 avanço; com 8,
4 jogos nas quartas. Folgas não geram partidas ou resultados fictícios.

O campo `confrontos` da fase contém toda a chave, inclusive os avanços diretos
(um participante e nenhuma partida) e as referências `origemUmId`/`origemDoisId`
dos confrontos seguintes. As rodadas futuras ficam pendentes e recebem suas
partidas com os vencedores ao finalizar a etapa anterior. A agenda tem exatamente
`quantidade de participantes - 1` partidas até definir o campeão.

Cada item de `fases[].rodadas` e `fases[].confrontos` expõe `etapa`, com um
`codigo` estável para integração e um `nome` para apresentação. Por exemplo:

```json
"etapa": { "codigo": "SEMIFINAL", "nome": "Semifinal" }
```

Os nomes são definidos desde a geração da chave, inclusive nos confrontos ainda
sem participantes: Fase preliminar, Trinta e dois avos de final, Dezesseis avos
de final, Oitavas de final, Quartas de final, Semifinal e Final. Para chaves ainda
maiores, usa-se "Eliminatória de N participantes". Havendo folgas, a primeira
etapa é "Fase preliminar". Com 4 participantes, as etapas são Semifinal e Final;
com 5 a 7, Fase preliminar, Semifinal e Final; com 8, Quartas de final,
Semifinal e Final. Nas ligas e no Swiss, `etapa` é `null`.

O envio individual mantém a finalização automática da rodada. O endpoint de
resultados em lote permite salvar parcialmente sem finalizar, seguido do endpoint
de finalização manual. Ao encerrar a final, o campeonato fica `FINALIZADO`.
Consulte o campeão em `GET /campeonatos/{id}/vencedor`. O eliminatório não gera
tabela de pontos; o progresso é apresentado pelo chaveamento.

Empates exigem pênaltis com placares não negativos e diferentes:

```json
{ "golsMandante": 1, "golsVisitante": 1, "penaltisMandante": 5, "penaltisVisitante": 4 }
```

Pênaltis não são aceitos em jogos sem empate. Gol de ouro e ida e volta não são
suportados nesse formato. Correções que mantêm o vencedor são permitidas.
Se uma correção muda o vencedor, o adversário na próxima etapa é atualizado
somente se esse confronto ainda não tiver resultado; caso contrário, a alteração
é rejeitada para preservar os jogos posteriores. A final pode ser corrigida,
atualizando o campeão, sem reabrir o campeonato.

`MISTA` e os confrontos melhor de três/cinco continuam indisponíveis.

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

## Resultados em lote sem finalizar a rodada

O envio individual de resultado continua finalizando automaticamente a rodada
quando todas as partidas têm placar. Como alternativa, envie um ou mais resultados
pela rota `PUT /campeonatos/{id}/rodadas/{rodadaId}/resultados`:

```json
{
  "resultados": [
    {"partidaId": 10, "resultado": {"golsMandante": 2, "golsVisitante": 1}},
    {"partidaId": 11, "resultado": {"golsMandante": 0, "golsVisitante": 0}}
  ]
}
```

Use o ID da rodada e os IDs das partidas que deseja preencher ou corrigir.
A rodada deve estar `EM_ANDAMENTO`; partidas duplicadas, de outras rodadas ou
uma lista vazia são rejeitadas. A operação é transacional. Partidas não enviadas
mantêm seus placares. É possível enviar uma única partida por vez e reenviar
placares para corrigi-los enquanto a rodada está em andamento.

As partidas ficam finalizadas, mas a rodada permanece `EM_ANDAMENTO`, sem atualizar
sua classificação, liberar a próxima rodada ou encerrar o campeonato.
Para concluir, envie `POST /campeonatos/{id}/rodadas/{rodadaId}/finalizar`, sem corpo.
Todas as partidas devem ter resultado. A finalização atualiza a classificação
e avança a próxima rodada da liga; no Swiss, a próxima continua sendo gerada
pelo endpoint próprio. Na última rodada, o campeonato é finalizado.
