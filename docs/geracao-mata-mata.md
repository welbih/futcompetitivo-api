# Forma de geracao do mata-mata

Nas configuracoes dos formatos `ELIMINATORIO`, `MISTA` e `GRUPOS`, o campo
opcional `formaGeracaoMataMata` aceita `AUTOMATICA` ou `ALEATORIA`.
Nao e aceito em formatos sem mata-mata.

## Configurar

```http
POST /campeonatos/{id}/iniciar
Content-Type: application/json
```

```json
{
  "formato": "GRUPOS",
  "turnoEreturno": false,
  "quantidadeClassificados": 8,
  "formatoEliminatorio": "IDA_E_VOLTA_FINAL_UNICA",
  "disputaTerceiroLugar": true,
  "formaGeracaoMataMata": "ALEATORIA"
}
```

O mesmo campo e aceito na configuracao de grupos manuais
`POST /campeonatos/{id}/iniciar/grupos` e no inicio eliminatorio manual
`POST /campeonatos/{id}/iniciar/eliminatorio/manual`.
As permissoes existentes dessas rotas permanecem; as rotas manuais e a abertura
do mata-mata apos classificatoria exigem `ADMIN`.

## AUTOMATICA

Usa a distribuicao por posicoes existente: com oito entradas, os confrontos sao
1 x 8, 4 x 5, 2 x 7 e 3 x 6.

- `MISTA`: usa a ordem da classificacao da liga.
- `GRUPOS`: usa a lista intercalada dos classificados de cada grupo
  (A1, B1, A2, B2...), com as regras existentes para quantidade impar de vagas.
- `ELIMINATORIO`: como nao existe classificatoria, usa a ordem dos participantes
  cadastrados no campeonato, sem embaralhar para montar a chave.

Folgas favorecem as primeiras posicoes da lista usada na montagem.

## ALEATORIA

Embaralha os participantes elegiveis uma unica vez, ao montar a chave. Nos
formatos com classificatoria, somente os classificados entram no sorteio.
Nao altera a classificacao, o numero de vagas ou as regras de substituicao de
desistentes. Pode gerar confrontos entre participantes do mesmo grupo.

A chave segue fixa depois do sorteio: nao ha sorteios entre rodadas nem ao
consultar o campeonato. Folgas e mandos seguem a ordem sorteada.
Uma correcao da classificatoria que mude o conjunto de classificados apos o
sorteio e rejeitada; correcoes nao sorteiam novamente a chave.

## Prioridade da definicao manual

As duas opcoes continuam permitindo a escolha manual do admin. Ao enviar
`confrontosPrimeiraRodada`, os pares, mandos, ordem e folgas informados prevalecem
sobre `formaGeracaoMataMata`, inclusive quando configurada como `ALEATORIA`.
O campo de configuracao permanece salvo; ele nao impede a definicao manual.

Exemplo da abertura apos grupos/liga, independentemente da preferencia salva:

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

O exemplo pressupoe quatro classificados. Sem `confrontosPrimeiraRodada`, a API
usa a preferencia configurada. Veja [validacoes e folgas manuais](confrontos-manuais.md).

## Retorno, padroes e persistencia

A resposta inclui `fases[].formaGeracaoMataMata`, tanto na classificatoria que
aguarda mata-mata quanto na fase eliminatoria criada. O formato dos jogos
(`formatoEliminatorio`) e independente dessa escolha.

Omitir o campo ou enviar `null` preserva os padroes anteriores: `AUTOMATICA` em
`MISTA`/`GRUPOS` e `ALEATORIA` no inicio eliminatorio puro pela rota normal.
Na rota exclusivamente manual, o padrao registrado e `AUTOMATICA`, mas os
confrontos enviados sempre prevalecem.

A migration `V15__adicionar_forma_geracao_mata_mata.sql` adiciona a configuracao
e marca fases existentes com mata-mata como `AUTOMATICA`, sem alterar suas
chaves ou resultados ja salvos.

Continuam valendo: todos os jogos classificatorios finalizados e solicitacao do
admin para entrar no mata-mata; depois disso, avanco automatico entre rodadas.
