# Liga parcial + mata-mata

Novo formato `MISTA_PARCIAL`: uma liga com quantidade limitada de jogos por
participante, seguida de mata-mata. A liga pode ter agenda gerada pela API ou
definida integralmente pelo admin para registrar campeonatos antigos.

## Agenda automatica

```http
POST /campeonatos/{id}/iniciar
Content-Type: application/json
```

```json
{
  "formato": "MISTA_PARCIAL",
  "jogosPorParticipante": 3,
  "quantidadeClassificados": 4,
  "formaGeracaoMataMata": "ALEATORIA",
  "formatoEliminatorio": "IDA_E_VOLTA_FINAL_UNICA",
  "disputaTerceiroLugar": true
}
```

Cada participante joga exatamente `jogosPorParticipante` partidas, com
adversarios diferentes. Sao exigidos:

- Pelo menos tres jogos por participante, em quantidade menor que o numero
  de participantes cadastrados.
- Produto `participantes * jogosPorParticipante` par, para formar os jogos.
- Pelo menos quatro classificados, sem exceder o total de participantes.
- `turnoEreturno` ausente ou falso. A opcao de ida e volta do mata-mata e
  configurada separadamente por `formatoEliminatorio`.
- `totalRodadas` ausente: a quantidade de rodadas e determinada pela agenda,
  e nao e necessariamente igual a `jogosPorParticipante`, pois pode haver folgas.

Por exemplo, seis participantes com tres jogos cada geram nove partidas na liga.
A API distribui as partidas em rodadas sem um participante jogar duas vezes
na mesma rodada. Nao ha compromisso de minimizar a quantidade de rodadas.

## Agenda definida pelo ADMIN

Disponivel somente antes do inicio, com o campeonato `CRIADO`:

```http
POST /campeonatos/{id}/iniciar/liga-parcial/manual
Authorization: Bearer <token-admin>
Content-Type: application/json
```

```json
{
  "configuracao": {
    "formato": "MISTA_PARCIAL",
    "jogosPorParticipante": 3,
    "quantidadeClassificados": 4,
    "formaGeracaoMataMata": "AUTOMATICA",
    "formatoEliminatorio": "JOGO_UNICO",
    "disputaTerceiroLugar": false
  },
  "rodadas": [
    {
      "partidas": [
        {"mandanteId": 252, "visitanteId": 253},
        {"mandanteId": 254, "visitanteId": 255},
        {"mandanteId": 256, "visitanteId": 257}
      ]
    },
    {
      "partidas": [
        {"mandanteId": 252, "visitanteId": 254},
        {"mandanteId": 253, "visitanteId": 256},
        {"mandanteId": 255, "visitanteId": 257}
      ]
    },
    {
      "partidas": [
        {"mandanteId": 252, "visitanteId": 255},
        {"mandanteId": 253, "visitanteId": 257},
        {"mandanteId": 254, "visitanteId": 256}
      ]
    }
  ]
}
```

Os IDs sao de participantes do campeonato, obtidos em
`GET /campeonatos/{id}`, campo `participantes[].id`, nao IDs de jogadores.
A ordem do array define as rodadas 1, 2, 3 etc.; a ordem dos jogos e os mandos
sao preservados. Informe a agenda inteira, sem resultados. Os placares podem
ser registrados depois pelas rotas existentes.

Validacoes da agenda:

- Cada rodada deve ter pelo menos um jogo, com dois participantes do campeonato.
- Um participante nao pode jogar contra si mesmo ou duas vezes na mesma rodada.
- O mesmo par nao pode se repetir na liga, mesmo com mando invertido.
- Ao final da agenda, todos devem ter exatamente a quantidade de jogos configurada.
- Nao sao aceitas partidas com lados vazios. Uma folga e representada pela
  ausencia de partida daquele participante na rodada, sem pontuacao ou jogo ficticio.
- Dados invalidos ou tentativa de iniciar novamente retornam `400`, sem gravar
  uma agenda parcial. Sem token retorna `401`; com `USER`, `403`.

A rota manual tambem pode ser usada com `LIGA_PARCIAL`, caso nao seja desejada
uma fase de mata-mata. Nesse caso, nao configure opcoes eliminatorias.

Ambas as formas de inicio retornam `200` com o `CampeonatoResponse` completo.
Na fase de liga, `tipo` e `LIGA`, `jogosPorParticipante` registra a configuracao
e `totalRodadas` informa quantas rodadas foram criadas. As partidas ficam em
`fases[].rodadas[].partidas`, com apenas a primeira rodada aberta inicialmente.

## Classificacao e mata-mata

A classificacao parcial e atualizada a cada resultado e fica disponivel em
`classificacaoAtual` e `GET /campeonatos/{id}/classificacao`. O historico de
rodadas encerradas continua separado em `classificacaoPorRodada`.

Quando a liga termina, o campeonato fica `EM_ANDAMENTO` com
`aguardandoMataMata: true`. O admin precisa solicitar:

```http
POST /campeonatos/{id}/mata-mata
Authorization: Bearer <token-admin>
```

Somente os primeiros `quantidadeClassificados` da classificacao final entram:

- `AUTOMATICA` (padrao): chave distribuida pela ordem dos classificados.
- `ALEATORIA`: sorteio dos classificados uma unica vez.
- O admin pode enviar `confrontosPrimeiraRodada` para definir a chave manualmente,
  com prioridade sobre as duas preferencias. Veja [payloads](confrontos-manuais.md).

O mata-mata aceita `JOGO_UNICO`, `IDA_E_VOLTA` e `IDA_E_VOLTA_FINAL_UNICA`, com
disputa opcional de terceiro lugar. Depois de criado, avanca automaticamente
entre rodadas. Nao pode ser criado enquanto houver rodadas/jogos classificatorios
pendentes, mesmo se o corte da classificacao ja parecer definido.

As regras existentes de desistencia continuam: W.O. de 3x0, 0x0 entre dois
desistentes, preservacao de jogos disputados e escolha manual para vagas de
classificados desistentes antes de abrir o mata-mata.

Nao e necessaria nova migration: a agenda, a quantidade de jogos, o total de
rodadas e as opcoes do mata-mata usam as estruturas existentes.
