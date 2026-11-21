# Avanco solicitado e desistencias no campeonato

As novas rotas de gestao abaixo exigem Bearer JWT com `ROLE_ADMIN`. Sem token
retornam `401`; com `USER`, `403`. IDs de participantes sao os de
`campeonato.participantes[].id`, nao os IDs de jogadores.

## Solicitar o mata-mata apos os grupos ou a liga

Para definir os confrontos da primeira rodada, use o campo opcional
`confrontosPrimeiraRodada`. Veja [contrato e exemplos](confrontos-manuais.md).

```http
POST /campeonatos/{id}/mata-mata
Authorization: Bearer <token-admin>
```

Sem vagas de desistentes, a requisicao pode ser enviada sem corpo. A API exige:

- Campeonato `EM_ANDAMENTO` com fase classificatoria configurada com vagas para
  o mata-mata (formatos `GRUPOS`, `MISTA` e `MISTA_PARCIAL`).
- Todas as rodadas da classificatoria finalizadas e todos os seus jogos com
  resultado finalizado, incluindo W.O.s.
- Mata-mata ainda nao criado. Repetir a solicitacao nao duplica a fase/jogos.

Concluir a ultima rodada classificatoria **nao cria mais a fase eliminatoria**.
O campeonato continua `EM_ANDAMENTO` e o JSON retorna `aguardandoMataMata: true`.
As classificacoes continuam disponiveis para revisao e registro de desistencias.
A solicitacao cria a chave e abre sua primeira rodada. As etapas posteriores
avancam automaticamente quando todos os jogos da rodada atual forem finalizados.

O inicio de um campeonato puramente eliminatorio continua sendo solicitado em
`POST /campeonatos/{id}/iniciar`, preservando os formatos jogo unico, ida e volta
e final unica. Essa solicitacao gera a chave e a primeira rodada.

## Avanco das rodadas eliminatorias

Depois de iniciado o mata-mata, a API gera automaticamente os jogos da proxima
rodada ao concluir a anterior, incluindo a disputa de terceiro lugar quando
configurada. Isso tambem vale para campeonatos puramente eliminatorios.
Rodadas resolvidas integralmente por W.O. ou folgas tambem avancam.

A rota abaixo permanece disponivel para uma rodada pendente, mas nao e
necessaria no fluxo normal:

```http
POST /campeonatos/{id}/rodadas/{rodadaId}/iniciar
Authorization: Bearer <token-admin>
```

Exige uma rodada eliminatoria `PENDENTE`, com todas as anteriores e todas as
fases anteriores finalizadas. Abre somente a rodada solicitada e gera suas
partidas, incluindo terceiro lugar na rodada das decisoes quando configurado.

Finalizar as semifinais gera os jogos da final e do terceiro lugar, quando houver.

As duas rotas retornam `200` com o `CampeonatoResponse` completo. Abertura
prematura, repetida ou em formato incompativel retorna `400`.

## Desistencia de um participante

```http
POST /campeonatos/{id}/participantes/{participanteId}/desistir
Authorization: Bearer <token-admin>
```

Sem corpo. Exige campeonato em andamento e participante pertencente a ele.
Retorna `200` com o campeonato atualizado:

- A desistencia e exclusiva deste campeonato: nao inativa jogador, equipe ou
  participante globalmente e nao afeta X1 ou outros campeonatos.
- Jogos ja disputados (`wo: false`) e seus resultados sao preservados.
- Nos jogos ainda pendentes do desistente, o adversario vence por `3x0`.
- Jogos futuros que forem gerados posteriormente tambem respeitam a desistencia.
- Se os dois jogadores forem desistentes, o placar e `0x0`, conforme a regra
  escolhida. Um W.O. previamente automatico entre eles e ajustado para esse
  empate; uma partida efetivamente disputada nao e reescrita.
- O jogo fica `FINALIZADA`, com `wo: true` e sem penaltis. Classificacao e
  estatisticas individuais usam esse placar: o W.O. duplo em 0x0 conta como
  empate, com um ponto para cada jogador na classificacao.
- A API nao permite editar manualmente o placar de um W.O.
- Em ida e volta, a desistencia elimina o participante do confronto mesmo que
  ele tivesse vantagem no placar agregado antes da desistencia. Resultados de
  partidas realmente disputadas permanecem registrados.
- Nas novas rodadas Swiss nao sao pareados participantes desistentes.
- Nao se pode desistir em campeonato ainda `CRIADO` ou ja `FINALIZADO`.

O JSON do campeonato inclui `participantesDesistentes`, uma lista de IDs. Cada
partida de campeonato agora inclui `wo`, falso para partidas normais/antigas.

```json
{
  "participantesDesistentes": [252],
  "aguardandoMataMata": true,
  "substituicoesMataMata": {}
}
```

O trecho acima mostra os novos campos do campeonato; os demais campos continuam
no contrato existente. Rodadas cujos jogos forem todos resolvidos por W.O. podem
ser finalizadas. Isso nao cria a fase mata-mata apos a classificatoria, mas
avanca normalmente entre etapas de um mata-mata ja iniciado.

O W.O. por desistencia pode ocorrer em qualquer rodada ou fase do campeonato
em andamento, inclusive na final e na disputa de terceiro lugar.

## Formato dos jogos eliminatorios

O campo `formatoEliminatorio` na inicializacao aceita:

- `JOGO_UNICO`: um jogo por confronto.
- `IDA_E_VOLTA`: dois jogos em todos os confrontos, incluindo final e terceiro lugar.
- `IDA_E_VOLTA_FINAL_UNICA`: dois jogos nas etapas anteriores; final e terceiro
  lugar em jogo unico.

Nos formatos `MISTA` e `GRUPOS`, essa configuracao e preservada ao solicitar o
mata-mata. `turnoEreturno` configura os jogos da fase classificatoria.
O cadastro com grupos manuais tambem aceita `formatoEliminatorio`.

Exemplo de inicio de grupos com ida e volta em todas as etapas, exceto as decisoes:

```json
{
  "formato": "GRUPOS",
  "turnoEreturno": true,
  "quantidadeClassificados": 8,
  "formatoEliminatorio": "IDA_E_VOLTA_FINAL_UNICA",
  "disputaTerceiroLugar": true
}
```

## Escolher manualmente quem ocupa a vaga

Se nao houver mais jogos classificatorios a disputar e o desistente estiver
entre os classificados, sua vaga fica aberta. **Nao ha promocao automatica.**
Na primeira desistencia apos a conclusao da classificatoria, a API salva a lista
de classificados. Essa lista preserva as vagas para escolha manual mesmo que
uma segunda desistencia transforme um W.O. anterior em 0x0 e altere a tabela.

```http
GET /campeonatos/{id}/mata-mata/vagas
Authorization: Bearer <token-admin>
```

Disponivel somente enquanto o campeonato aguarda a solicitacao do mata-mata.
Exemplo de resposta:

```json
[
  {
    "desistenteId": 252,
    "grupoId": 10,
    "candidatos": [
      {
        "id": 257,
        "jogador": {"id": 6, "nome": "Fábio", "apelido": null, "ativo": true},
        "equipe": {"id": 2, "nome": "Real Madrid", "logoUrl": "https://example.com/real.png", "ativo": true},
        "ativo": true
      }
    ]
  }
]
```

Cada vaga oferece candidatos do mesmo grupo, que nao sejam desistentes nem ja
tenham outra vaga garantida. Na classificatoria por liga, `grupoId` e `null` e
os candidatos vem dos demais participantes do campeonato. A API nao aumenta o
total de vagas configurado nem permite retirar uma vaga de outro classificado.
Se o desistente nao estiver entre os classificados, a lista de vagas nao muda.

O admin envia suas escolhas junto da solicitacao de inicio:

```http
POST /campeonatos/202/mata-mata
Authorization: Bearer <token-admin>
Content-Type: application/json
```

```json
{
  "substituicoes": [
    {"desistenteId": 252, "substitutoId": 257}
  ]
}
```

Todas as vagas abertas devem ter uma escolha. Os substitutos precisam pertencer
ao campeonato, nao podem ser desistentes, nao podem ocupar duas vagas e devem
preservar a cota do grupo. Configuracao incompleta, duplicada ou indevida retorna
`400`, sem criar a fase. As escolhas ficam salvas em `substituicoesMataMata`:

```json
{"substituicoesMataMata": {"252": 257}}
```

Para essa regra, "antes do mata-mata" significa antes da solicitacao que cria a
fase eliminatoria. Apos essa abertura, uma desistencia resolve as partidas por
W.O.; nao troca retroativamente os classificados ou jogos disputados. Se nao
houver candidato elegivel para uma vaga, o inicio sera bloqueado para revisao do
admin, sem promover automaticamente outro jogador.

## Persistencia

A migration `V13__adicionar_desistencias_campeonatos.sql` cria os registros de
desistencia/substituicao e a lista base de classificados por campeonato, e adiciona `partida.wo` com default
`false`. Nenhum resultado anterior e convertido em W.O. pela migration.
As operacoes de avanco, registro de resultados e desistencia sao transacionais
e utilizam bloqueio do campeonato para serializar alteracoes concorrentes.
