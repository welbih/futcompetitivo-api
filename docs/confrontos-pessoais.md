# Confrontos pessoais: melhor de três e melhor de cinco

Um X1 é uma série entre **exatamente dois participantes**, cada um com jogador
e equipe. Informe os IDs dos jogadores e das equipes cadastrados e ativos.
Cada X1 cria participantes próprios, sem reaproveitar participantes de outros
confrontos ou campeonatos. O mesmo jogador não pode ocupar os dois lados,
mesmo com equipes diferentes. Os dois jogadores podem usar a mesma equipe.
Não exige organização, fase, rodada ou campeonato.

## Criar

`POST /x1`

```json
{
  "participanteUm": {
    "jogadorId": 1,
    "equipeId": 3
  },
  "participanteDois": {
    "jogadorId": 2,
    "equipeId": 4
  },
  "tipo": "MELHOR_DE_TRES",
  "encerrarAntecipadamente": true
}
```

Use `MELHOR_DE_CINCO` para cinco partidas. A criação retorna `201`, o endereço
do confronto no cabeçalho `Location` e sua agenda de três ou cinco partidas.
O participante um é mandante no primeiro jogo e o mando alterna nos seguintes.

Com `encerrarAntecipadamente: true`, o confronto é finalizado automaticamente
ao alcançar duas vitórias na melhor de três (2×0 ou 2×1), ou três na melhor de
cinco (3×0, 3×1 ou 3×2). Jogos não disputados recebem status `CANCELADA`, com
resultado nulo, sem contar como jogos disputados.

Se `encerrarAntecipadamente` for `false` ou omitido, todos os jogos previstos
permanecem disponíveis e o confronto só finaliza automaticamente após completar
a agenda. Ainda é possível optar pelo encerramento manual depois de alcançar
duas ou três vitórias, conforme o tipo.

## Consultar

- `GET /x1`: lista os confrontos pessoais por ID.
- `GET /x1/{id}`: retorna `participanteUm` e `participanteDois` com seus
  respectivos jogadores e equipes, o tipo, a opção de encerramento, o status,
  os contadores `vitoriasParticipanteUm` e `vitoriasParticipanteDois`, o
  participante vencedor e as partidas com IDs, números, mandos, status e placares.
  Mandante e visitante também incluem jogador e equipe.

O confronto começa em `EM_ANDAMENTO`. O campo `vencedor` permanece nulo até
`FINALIZADO`. Vence quem tiver mais vitórias em partidas, independentemente do
total de gols. Os mesmos jogadores podem disputar outros confrontos, cada um
com agenda e resultados próprios.

## Registrar ou corrigir resultado

`PUT /x1/{id}/partidas/{partidaId}/resultado`

```json
{
  "golsMandante": 2,
  "golsVisitante": 1
}
```

Use o ID de uma partida do próprio confronto e registre os jogos na ordem da
agenda. Cada jogo precisa de vencedor; em empate, informe os pênaltis:

```json
{
  "golsMandante": 1,
  "golsVisitante": 1,
  "penaltisMandante": 5,
  "penaltisVisitante": 4
}
```

Pênaltis devem ser não negativos, diferentes e só são aceitos em partidas
empatadas. Gol de ouro não é utilizado. Jogos cancelados não aceitam placar.

Para corrigir um placar já registrado, use a mesma rota. O sistema recalcula
as vitórias e o vencedor. Se uma correção desfizer a maioria que encerrou o
confronto antecipadamente, os jogos cancelados voltam a `AGENDADA`, o confronto
reabre e o vencedor volta a nulo. Resultados de jogos efetivamente disputados
são preservados.

## Encerrar manualmente

`POST /x1/{id}/encerrar`, sem corpo.

Só é permitido após um jogador obter duas vitórias na melhor de três ou três
na melhor de cinco. A operação habilita o encerramento antecipado nesse
confronto, cancela apenas jogos não disputados e retorna o vencedor. Repetir a
operação mantém o mesmo resultado.

## Persistência e validação

A migração PostgreSQL `V9__unificar_partidas_x1.sql` renomeia a tabela do
confronto para `x1` e transfere as partidas existentes para a tabela compartilhada
`partida`, preservando participantes, ordem, status e resultados. A tabela
`partida_pessoal` e sua sequence são removidas. Os IDs das partidas transferidas
são gerados pela sequence compartilhada; consulte o X1 novamente para obter
esses IDs. O ID do X1 permanece o mesmo.

Partidas do X1 têm `x1_id` e `numero`, sem vínculo com rodada ou confronto de
campeonato. Os formatos e o fluxo dos campeonatos permanecem intactos.
Os testes estão nos pacotes `controller`, `service` e `migration`, com cobertura
dos dois formatos, combinações de vitórias, encerramento manual e automático,
correções, validações e persistência.
