# Composicao manual de grupos

O admin pode informar exatamente quem pertence aos grupos A e B ao iniciar um
campeonato, inclusive para registrar edicoes antigas sem sorteio dos grupos.

```http
POST /campeonatos/{id}/iniciar/grupos
Authorization: Bearer <token-com-ROLE_ADMIN>
Content-Type: application/json
```

Exemplo para o campeonato 202, com os IDs de participantes informados no CSV:

```json
{
  "grupoA": [252, 253, 254, 255, 256, 257, 258],
  "grupoB": [259, 260, 261, 262, 263, 264, 265],
  "quantidadeClassificados": 8,
  "turnoEreturno": false,
  "disputaTerceiroLugar": false
}
```

**Os arrays recebem IDs de participantes do campeonato, nao IDs de jogadores.**
Cada participante identifica o jogador e sua equipe naquela edicao. Obtenha os
IDs em `GET /campeonatos/{id}`, campo `participantes[].id`. O campeonato precisa
estar `CRIADO`; este endpoint inicia a fase e gera a agenda na mesma transacao.
Nao envie a requisicao se o campeonato ja tiver sido iniciado por SQL ou pela API.

## Comportamento

Para mais de dois grupos, envie `grupos` em vez de `grupoA`/`grupoB`.
Veja [quantidade de grupos, payload e classificacao](quantidade-grupos.md).

- A fase e automaticamente `GRUPOS`; nao e necessario enviar `formato`.
- `formaGeracaoMataMata` aceita `AUTOMATICA` (padrao) ou `ALEATORIA`.
  A escolha dos confrontos manualmente pelo admin continua disponivel e tem prioridade.
- `formatoEliminatorio` aceita `JOGO_UNICO`, `IDA_E_VOLTA` ou
  `IDA_E_VOLTA_FINAL_UNICA`. A ultima opcao deixa final e terceiro lugar em jogo
  unico; as demais etapas sao ida e volta. `turnoEreturno` vale para os grupos.
- Preserva a composicao e a ordem informadas nos arrays, sem embaralhamento.
- Os dois grupos devem conter todos os participantes cadastrados no campeonato,
  cada um uma unica vez. Nao aceita IDs de participantes de outros campeonatos.
- Cada grupo precisa de pelo menos tres participantes e de participantes
  suficientes para suas vagas. A quantidade de classificados e o **total**, de
  pelo menos quatro, sem exceder a quantidade de participantes.
- Com `quantidadeClassificados: 8`, classificam quatro do grupo A e quatro do B.
  Os criterios de classificacao e desempate continuam os existentes no sistema.
- `turnoEreturno` e `disputaTerceiroLugar` sao opcionais e assumem `false`.
- Com 7 participantes por grupo e turno unico: 7 rodadas, 42 jogos, uma folga por
  grupo por rodada, sem partidas ficticias para representar folgas.
- A primeira rodada fica `EM_ANDAMENTO`, as demais `PENDENTE`, e as partidas
  `AGENDADA`, com `resultado: null`.
- Nao registra placares. Os resultados devem ser informados pelas rotas atuais.
  Ao concluir os grupos, o campeonato aguarda uma solicitacao explicita do admin
  em `POST /campeonatos/{id}/mata-mata`; o mata-mata nao e gerado automaticamente.
- Segue as mesmas validacoes de participantes, jogadores, equipes e organizacao
  ativos utilizadas pela inicializacao automatica.
- A resposta `200` e o `CampeonatoResponse` completo, no mesmo contrato de
  `POST /campeonatos/{id}/iniciar`. Os membros aparecem em
  `fases[].grupos[].participantes`; os jogos, em `fases[].rodadas[].partidas`.
- Nao altera um campeonato iniciado nem transfere participantes entre grupos
  apos a geracao da agenda. Nao sobrescreve resultados existentes.

## Permissoes e erros

| Situacao | Status |
| --- | --- |
| Sem token valido | `401` |
| Usuario autenticado sem `ROLE_ADMIN` | `403` |
| Campeonato inexistente | `404` |
| Campeonato ja iniciado | `400` |
| Grupos ausentes, vazios, repetidos, incompletos ou com IDs estranhos | `400` |
| Numero de classificados invalido/incompativel com os grupos | `400` |
| Configuracao aceita | `200` |

Erros de validacao usam o `ProblemDetail` padrao da API, com `detail` e, quando
aplicavel, o mapa `erros`. Uma falha cancela a transacao e preserva o campeonato.

A inicializacao automatica `POST /campeonatos/{id}/iniciar` continua com seu
comportamento e permissoes anteriores. O frontend pode apresentar esta nova
opcao somente ao admin; a API tambem verifica a role, independentemente da tela.
