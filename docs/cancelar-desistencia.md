# Retirar a desistencia de um participante

O ADMIN pode cancelar uma desistencia registrada no campeonato. A operacao
remove a marca e reverte os W.O.s correspondentes quando isso nao exige
desfazer jogos posteriores, pareamentos ou uma chave ja avancada.

```http
DELETE /campeonatos/202/participantes/252/desistencia
Authorization: Bearer <token-admin>
```

Sem corpo. Os IDs sao de campeonato e **participante do campeonato**, nao do
jogador. Obtenha o participante em `GET /campeonatos/{id}`, campo
`participantes[].id`.

## Resposta e permissoes

- `200 application/json`: retorna o `CampeonatoResponse` completo e atualizado,
  no mesmo contrato do GET. O frontend pode substituir seu estado pelo retorno.
- Sem autenticacao valida: `401`. Usuario sem `ROLE_ADMIN`: `403`.
- Campeonato inexistente: `404`. Participante fora do campeonato ou reversao
  bloqueada pelas regras abaixo: `400`, no padrao `ProblemDetail`.
- Se o participante pertence ao campeonato, mas nao esta desistente, retorna
  `200` sem alterar dados nem publicar SSE. A operacao e idempotente.

Exemplo dos campos relevantes do retorno, apos remover a unica desistencia:

```json
{
  "id": 202,
  "status": "EM_ANDAMENTO",
  "participantesDesistentes": [],
  "aguardandoMataMata": false,
  "substituicoesMataMata": {}
}
```

Esse trecho nao substitui o contrato completo: organizacao, edicao,
participantes, fases, partidas, historico e classificacao atual tambem retornam.

## O que acontece com os jogos

- Resultados realmente disputados (`wo: false`) sao preservados, inclusive
  placares, penaltis, IDs dos jogos e dados de video.
- W.O. do participante contra adversario que nao esta desistente volta a
  `AGENDADA`, `wo: false` e `resultado: null`. O placar devera ser registrado
  normalmente. Links de video nao sao apagados.
- Se o adversario continua desistente, o jogo continua finalizado por W.O.,
  agora com vitoria de **3x0 para quem retornou**. Isso inclui o W.O. duplo
  anteriormente registrado como empate 0x0.
- Partidas canceladas nao sao reabertas. Outros campeonatos e X1 nao sao afetados.
- A retirada da marca nao ativa nem inativa jogador ou equipe globalmente.

O sistema considera apenas W.O.s finalizados ligados ao participante, nunca
um placar 3x0 realmente disputado. A partida reaberta nao recebe um resultado
anterior: o W.O. havia sido aplicado sobre uma partida ainda sem placar final.

## Rodadas, classificacao e status

Quando um jogo volta a ficar pendente, a rodada necessaria e reaberta. Se ja
existia uma rodada anterior em andamento, ela continua sendo a unica rodada
aberta; as seguintes ficam pendentes. Isso evita duas rodadas simultaneas.

As classificacoes encerradas afetadas sao recalculadas ou removidas a partir
da rodada reaberta. As anteriores permanecem. `classificacaoAtual` passa a
refletir apenas os resultados que continuam validos, e as estatisticas
individuais deixam de contar os W.O.s que voltaram a ficar sem resultado.

Se o campeonato foi `FINALIZADO` por um W.O. que pode ser revertido com
seguranca, volta a `EM_ANDAMENTO`. Por exemplo: e possivel retirar a
desistencia na final decidida por W.O., reabrir o jogo e lancar o placar real.
As restricoes de dependencia abaixo continuam valendo tambem em finalizados.

## Limites para preservar a competicao

A operacao e bloqueada, sem alterar a marca ou os jogos, quando:

- **Liga/grupos:** reabrir um W.O. exigiria voltar a uma rodada anterior a
  outra que ja possui jogos realmente disputados. Nao se apagam esses jogos
  para permitir o retorno. Outros placares da propria rodada reaberta sao preservados.
- **Suico:** ja foi gerada uma rodada posterior a um W.O. afetado, mesmo se
  ainda nao jogada, pois seu pareamento pode depender daquela classificacao.
- **Mata-mata:** uma etapa posterior ao W.O. afetado ja foi aberta/gerada,
  inclusive quando o avanco ocorreu por folga. A chave nao e remontada.
- **Classificatoria concluida:** os W.O.s afetados ja deram origem a uma fase
  de mata-mata. Nao se alteram retroativamente os classificados da chave existente.
- **Substituicao:** o desistente ja foi substituido por outro participante ao
  criar o mata-mata. Nao se retira a vaga do substituto automaticamente.

Se nao houve W.O. a desfazer, a marca pode ser retirada sem mudar placares.
Ainda sao verificadas as dependencias de substituicao e avancos eliminatorios
que possam ter usado a inelegibilidade do participante.

Antes de criar o mata-mata, retirar a desistencia de um classificado que nao
teve jogos reabertos restaura sua vaga, sem exigir substituto. Se jogos da
classificatoria forem reabertos, o sistema descarta a lista base congelada de
classificados para recalcular a qualificacao apos seus novos resultados. O
mata-mata continua bloqueado ate todos os jogos/rodadas serem finalizados.

No suico, um participante que retorna pode integrar os proximos pareamentos;
rodadas existentes nao sao refeitas nem recebem jogos extras.

## SSE e persistencia

A operacao usa transacao com bloqueio pessimista do campeonato. Somente uma
retirada efetiva e confirmada por commit publica:

```text
event: campeonato-atualizado
data: {"campeonatoId":202,"motivo":"DESISTENCIA_CANCELADA","ocorridoEm":"2026-11-21T15:00:00Z","rodadaId":null,"partidaId":null}
```

O frame inclui o ID UUID do contrato SSE. Uma notificacao cobre a retirada e
todas as alteracoes automaticas de W.O., rodadas e classificacoes. Rejeicoes,
rollback e repeticao sem mudanca nao notificam.

O frontend continua ouvindo `campeonato-atualizado` e consultando o GET completo.
Veja [heartbeat, reconexao e limitacao a uma instancia](eventos-campeonatos.md).

Nao e necessaria migration: sao utilizadas a marca de desistencia, a flag
`partida.wo`, as rodadas e as classificacoes existentes.
