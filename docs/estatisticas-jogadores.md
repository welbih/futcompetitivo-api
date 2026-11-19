# Estatisticas individuais de jogadores

`GET /jogadores/{id}/estatisticas?origem=TODOS`

Endpoint publico: nao exige login nem role. A regra geral de Bearer da API
continua valida: se um token for enviado, ele sera validado.

| Parametro | Valores | Padrao |
| --- | --- | --- |
| `id` (caminho) | ID do jogador, inclusive inativo | Obrigatorio |
| `origem` (query) | `CAMPEONATO`, `X1`, `TODOS` | `TODOS` |

```http
GET /jogadores/1/estatisticas
GET /jogadores/1/estatisticas?origem=CAMPEONATO
GET /jogadores/1/estatisticas?origem=X1
```

## Calculo

- Usa todo o historico do jogador na origem selecionada, sem paginacao.
- Considera apenas partidas `FINALIZADA` com resultado, dois jogadores diferentes
  e vinculo com um campeonato ou X1. Jogos cancelados, agendados/em andamento,
  folgas e avancos automaticos sem partida nao entram.
- O status da competicao/rodada nao precisa ser finalizado: uma partida ja
  finalizada entra mesmo enquanto outros jogos estao em andamento.
- Cada jogo de ida e volta conta separadamente. Nao usa placar agregado.
- Vitorias/empates/derrotas comparam exclusivamente os gols da partida. Empate
  com decisao nos penaltis continua empate para todos os calculos deste endpoint.
- Os quatro recordes incluem apenas vitorias (aplicadas) ou derrotas (sofridas).
  "Por gols" maximiza gols marcados ou sofridos; "por saldo" maximiza a diferenca
  a favor ou contra. Todos os empates na metrica sao retornados, sem desempate
  secundario por saldo, gols, adversario ou penaltis.
- Fregueses maximizam o numero de vitorias; carrascos, o numero de derrotas.
  Retorna todos os adversarios empatados. Se nao houver vitoria/derrota, a lista
  correspondente fica vazia. `totalConfrontos` e a quantidade de partidas entre
  os dois jogadores, incluindo empates, no filtro selecionado.
- A agregacao e por jogador, mesmo que ele use diferentes equipes ou registros
  de participante. Inativar jogador, equipe, participante ou organizacao nao
  remove partidas das estatisticas.
- Retorna os metadados atuais dos cadastros associados; nao cria snapshots de
  nomes/logos. Inativacao nao altera o placar nem os totais historicos.
- Recordes sao ordenados por `partidaId`; adversarios empatados, por ID do jogador.
- Uma correcao de resultado aparece na proxima consulta, sem cache de agregados.

## Contrato JSON

As seis categorias sempre sao arrays, inclusive quando nao possuem resultados.
Os placares, equipes e totais estao sempre na perspectiva do jogador consultado,
independentemente de ser mandante ou visitante.

Exemplo completo: Ana tem apenas uma partida no historico, uma vitoria por 5 a 1.
Os IDs abaixo sao ilustrativos; a estrutura e os campos sao os retornados pela API
e verificados pelos testes HTTP.

```json
{
  "jogador": {"id": 1, "nome": "Ana", "apelido": "A10", "ativo": true},
  "origem": "TODOS",
  "resumo": {
    "partidasDisputadas": 1,
    "vitorias": 1,
    "empates": 0,
    "derrotas": 0,
    "golsMarcados": 5,
    "golsSofridos": 1,
    "saldoGols": 4
  },
  "maioresGoleadasAplicadasPorGols": [
    {
      "partidaId": 101,
      "adversario": {"id": 2, "nome": "Bruno", "apelido": "B9", "ativo": true},
      "equipeJogador": {"id": 10, "nome": "Azul", "logoUrl": "https://example.com/azul.png", "ativo": true},
      "equipeAdversario": {"id": 11, "nome": "Verde", "logoUrl": "https://example.com/verde.png", "ativo": true},
      "golsMarcados": 5,
      "golsSofridos": 1,
      "saldoGols": 4,
      "penaltis": null,
      "origem": "CAMPEONATO",
      "competicao": {
        "id": 20,
        "nome": "Liga da Vila 2026",
        "organizacao": {"id": 3, "nome": "Liga da Vila"},
        "edicao": 2026
      },
      "rodada": {"id": 40, "numero": 1},
      "fase": {"id": 30, "ordem": 1, "tipo": "LIGA", "etapa": null},
      "idaOuVolta": null,
      "numeroPartida": null,
      "dataPartida": null
    }
  ],
  "maioresGoleadasAplicadasPorSaldo": [
    {
      "partidaId": 101,
      "adversario": {"id": 2, "nome": "Bruno", "apelido": "B9", "ativo": true},
      "equipeJogador": {"id": 10, "nome": "Azul", "logoUrl": "https://example.com/azul.png", "ativo": true},
      "equipeAdversario": {"id": 11, "nome": "Verde", "logoUrl": "https://example.com/verde.png", "ativo": true},
      "golsMarcados": 5,
      "golsSofridos": 1,
      "saldoGols": 4,
      "penaltis": null,
      "origem": "CAMPEONATO",
      "competicao": {
        "id": 20,
        "nome": "Liga da Vila 2026",
        "organizacao": {"id": 3, "nome": "Liga da Vila"},
        "edicao": 2026
      },
      "rodada": {"id": 40, "numero": 1},
      "fase": {"id": 30, "ordem": 1, "tipo": "LIGA", "etapa": null},
      "idaOuVolta": null,
      "numeroPartida": null,
      "dataPartida": null
    }
  ],
  "maioresGoleadasSofridasPorGols": [],
  "maioresGoleadasSofridasPorSaldo": [],
  "maioresFregueses": [
    {
      "adversario": {"id": 2, "nome": "Bruno", "apelido": "B9", "ativo": true},
      "totalConfrontos": 1,
      "vitorias": 1,
      "empates": 0,
      "derrotas": 0
    }
  ],
  "maioresCarrascos": []
}
```

Uma mesma partida pode aparecer em categorias diferentes de recordes. Isso nao
representa contagem duplicada no resumo: o exemplo acima tem apenas uma partida.

### Mata-mata e penaltis

O recorde usa a fase/rodada da partida. A identificacao da etapa reutiliza os
mesmos codigos e nomes retornados pelo campeonato (`PRELIMINAR`, `SEMIFINAL`,
`FINAL`, etc.). Na disputa de terceiro lugar retorna especificamente:

```json
{
  "fase": {
    "id": 31,
    "ordem": 2,
    "tipo": "ELIMINATORIA",
    "etapa": {"codigo": "TERCEIRO_LUGAR", "nome": "Disputa de terceiro lugar"}
  },
  "idaOuVolta": "VOLTA",
  "penaltis": {"jogador": 4, "adversario": 5}
}
```

O trecho acima descreve os campos de contexto de um recorde. `idaOuVolta` pode
ser `IDA`, `VOLTA`, `JOGO_UNICO` ou `null` fora de confrontos eliminatorios.
Os penaltis aparecem separadamente, sem serem somados aos gols. Em ida e volta,
um jogo pode ter vencedor no placar individual e penaltis por empate no agregado;
o recorde considera exclusivamente o placar individual desse jogo.

### X1

Nos recordes de X1, `competicao` identifica a serie pessoal e `numeroPartida`
indica o jogo dentro da serie. O contexto tem esta estrutura:

```json
{
  "origem": "X1",
  "competicao": {"id": 8, "nome": "X1 #8", "organizacao": null, "edicao": null},
  "rodada": null,
  "fase": null,
  "idaOuVolta": null,
  "numeroPartida": 2,
  "dataPartida": null
}
```

### Sem historico

```json
{
  "jogador": {"id": 9, "nome": "Diego", "apelido": null, "ativo": false},
  "origem": "TODOS",
  "resumo": {
    "partidasDisputadas": 0, "vitorias": 0, "empates": 0, "derrotas": 0,
    "golsMarcados": 0, "golsSofridos": 0, "saldoGols": 0
  },
  "maioresGoleadasAplicadasPorGols": [],
  "maioresGoleadasAplicadasPorSaldo": [],
  "maioresGoleadasSofridasPorGols": [],
  "maioresGoleadasSofridasPorSaldo": [],
  "maioresFregueses": [],
  "maioresCarrascos": []
}
```

## Campos ainda nao armazenados pelo dominio

- `dataPartida` e `null`: nao existe data/hora da partida no modelo atual. Nao
  usamos data de consulta, ordem ou ID como se fossem uma data de jogo.
- O campeonato nao possui um nome proprio; `competicao.nome` e composto pelo
  nome atual da organizacao e pela edicao, como `Liga da Vila 2026`.
- O X1 nao possui titulo proprio; `competicao.nome` usa `X1 #<id>`.
- Logos ou apelidos ausentes sao retornados como `null`.
- `competicao.id` deve ser interpretado junto de `origem`, pois IDs de campeonatos
  e de series X1 pertencem a tabelas diferentes.

## Erros

- `404`: jogador inexistente, mesmo contrato `ProblemDetail` usado na API:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Jogador nao encontrado: 999",
  "instance": "/jogadores/999/estatisticas"
}
```

- `400`: `origem` diferente dos valores do enum ou ID que nao seja numerico.

## Implementacao e consultas

O controller delega ao service transacional de leitura. O repository consulta
diretamente `partida`, com joins apenas para registros escalares (participantes,
jogadores, equipes e contexto). Nao percorre colecoes de fases/confrontos para
coletar os jogos, evitando duplicar uma partida que pertence a rodada e confronto.
Usa uma busca do jogador e uma consulta de historico sem `Pageable` ou limites;
nao executa consultas adicionais por partida/adversario. Os indices de participante
por jogador e de partida por mandante/visitante/confronto ja existem nas migracoes.
Nao ha nova tabela, migration ou agregados persistidos para esta funcionalidade.
