# Primeira rodada do mata-mata definida pelo admin

Recurso para registrar chaves de campeonatos antigos. Todas as rotas abaixo
exigem JWT de `ADMIN`: sem token retorna `401`; com `USER`, `403`.
Os IDs sao de **participantes do campeonato**, obtidos em
`GET /campeonatos/{id}`, campo `participantes[].id`; nao sao IDs de jogadores.

A definicao manual prevalece sobre a preferencia `formaGeracaoMataMata`, seja
`AUTOMATICA` ou `ALEATORIA`. Veja [opcoes de geracao](geracao-mata-mata.md).

## Campeonato puramente eliminatorio

Antes de iniciar o campeonato (`CRIADO`):

```http
POST /campeonatos/{id}/iniciar/eliminatorio/manual
Authorization: Bearer <token-admin>
Content-Type: application/json
```

```json
{
  "formatoEliminatorio": "IDA_E_VOLTA_FINAL_UNICA",
  "disputaTerceiroLugar": true,
  "confrontosPrimeiraRodada": [
    {"mandanteId": 252, "visitanteId": 255},
    {"mandanteId": 254, "visitanteId": 253}
  ]
}
```

`confrontosPrimeiraRodada` e obrigatorio nesta rota. O formato e opcional, com
padrao `JOGO_UNICO`; tambem aceita `IDA_E_VOLTA` e `IDA_E_VOLTA_FINAL_UNICA`.
As duas ultimas opcoes mantem as regras de final/terceiro lugar ja documentadas.
A opcao automatica continua disponivel em `POST /campeonatos/{id}/iniciar`.

## Mata-mata apos grupos ou liga (GRUPOS/MISTA)

Envie o campo opcional na solicitacao existente, depois de finalizar todos os
jogos classificatorios:

```http
POST /campeonatos/{id}/mata-mata
Authorization: Bearer <token-admin>
Content-Type: application/json
```

```json
{
  "confrontosPrimeiraRodada": [
    {"mandanteId": 252, "visitanteId": 255},
    {"mandanteId": 254, "visitanteId": 253}
  ]
}
```

Sem esse campo (ou com `null`), a API usa `formaGeracaoMataMata` configurada
(`AUTOMATICA` ou `ALEATORIA`).
Uma lista vazia e invalida. A escolha manual nao altera quem se classificou,
nao ignora jogos pendentes e nao exige os cruzamentos automaticos entre grupos.
O formato eliminatorio vem da configuracao inicial do campeonato.

Se houver vagas por desistencia, envie tambem `substituicoes`. Por exemplo,
com 252 desistente e 257 escolhido para sua vaga:

```json
{
  "substituicoes": [{"desistenteId": 252, "substitutoId": 257}],
  "confrontosPrimeiraRodada": [
    {"mandanteId": 257, "visitanteId": 255},
    {"mandanteId": 254, "visitanteId": 253}
  ]
}
```

As validacoes de substituicao permanecem: o admin precisa preencher todas as
vagas com candidatos elegiveis. Os confrontos usam o substituto, nunca o desistente.

## Ordem, mando, folgas e validacoes

- Informe a primeira rodada completa: cada participante elegivel deve aparecer
  exatamente uma vez. Nao pode repetir participante, enfrentar a si mesmo ou
  incluir alguem de outro campeonato/fora dos classificados.
- A ordem do array define a chave: os vencedores dos confrontos 1 e 2 se
  enfrentam na proxima rodada, assim como os dos confrontos 3 e 4, e assim por diante.
- Em jogo unico, `mandanteId` e o mandante. Em ida e volta, e o mandante da ida;
  a volta e gerada automaticamente com os mandos invertidos.
- A quantidade de confrontos e metade da proxima potencia de dois maior ou igual
  ao total de participantes. Para 5 participantes sao 4 confrontos: um jogo e
  tres folgas. Informe `null` em um dos lados da folga, por exemplo
  `{"mandanteId": 252, "visitanteId": null}`. Nao pode deixar os dois lados vazios.
  Uma folga avanca sem criar uma partida ficticia ou registrar W.O.
- Repetir o inicio ou enviar dados invalidos retorna `400`, sem salvar uma chave parcial.
- Depois de iniciada a chave, as proximas rodadas avancam automaticamente.
  Resultados e desistencias continuam seguindo as regras existentes.
- Correcoes da classificatoria nao reordenam confrontos manuais. Se uma correcao
  mudar o conjunto de classificados da chave manual, ela e rejeitada; as
  restricoes existentes apos resultados no mata-mata tambem permanecem.

Ambas as rotas retornam `200` com o `CampeonatoResponse` completo. As partidas
aparecem em `fases[].rodadas[].partidas`; os confrontos apontam para elas por
`fases[].confrontos[].partidaIds`. Folgas retornam `partidaIds: []`.

A migration `V14__adicionar_confrontos_manuais.sql` registra se a fase foi montada
manualmente, preservando o comportamento automatico dos campeonatos existentes.
