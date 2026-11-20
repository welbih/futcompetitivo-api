# Quantidade de grupos

No formato `GRUPOS`, a inicializacao aceita `quantidadeGrupos`, com padrao 2.
Devem existir pelo menos dois grupos, e cada grupo precisa conter no minimo
tres participantes. Por exemplo, com 14 participantes, podem ser escolhidos
2, 3 ou 4 grupos; 5 grupos sao rejeitados.

```http
POST /campeonatos/{id}/iniciar
Content-Type: application/json
```

```json
{
  "formato": "GRUPOS",
  "quantidadeGrupos": 4,
  "quantidadeClassificados": 8,
  "turnoEreturno": false,
  "formaGeracaoMataMata": "AUTOMATICA",
  "formatoEliminatorio": "IDA_E_VOLTA_FINAL_UNICA",
  "disputaTerceiroLugar": true
}
```

O sorteio distribui os participantes entre todos os grupos de forma equilibrada:
a diferenca entre o maior e o menor grupo e de no maximo um participante.
Com 14 participantes e 4 grupos, ficam 4, 4, 3 e 3 participantes. Os grupos
recebem os nomes Grupo A, Grupo B, Grupo C etc.

Cada grupo tem sua propria classificacao e jogos apenas entre seus membros.
`turnoEreturno: true` gera tambem a volta com mando invertido. Grupos com numero
impar de participantes possuem folgas, sem partidas ficticias. As agendas sao
reunidas nas rodadas da fase; grupos menores podem terminar antes dos demais.

`quantidadeGrupos` em outro formato, quantidade menor que 2 ou grupos com menos
de 3 participantes retornam `400`. Omitir o campo ou enviar `null` usa dois
grupos, mantendo a exigencia minima: um novo campeonato de grupos precisa de
pelo menos seis participantes.

## Classificacao para o mata-mata

`quantidadeClassificados` continua sendo o total de vagas, no minimo quatro e
no maximo o total de participantes:

- Divide-se o total pelo numero de grupos. Cada grupo recebe a quantidade
  inteira de vagas, ocupadas pelos primeiros colocados de sua tabela.
- As vagas restantes vao aos melhores proximos colocados entre os grupos.
- A comparacao entre grupos usa pontos por jogo, vitorias por jogo, saldo por
  jogo e gols marcados por jogo, nessa ordem. Persistindo empate, usa a ordem
  inicial dos participantes.
- Exemplo: 3 grupos e 8 vagas classificam os dois primeiros de cada grupo e os
  dois melhores terceiros. Com 4 grupos e 8 vagas, classificam dois de cada.
- Quando ha menos vagas que grupos, os primeiros colocados disputam as vagas
  pelos mesmos criterios proporcionais.

Na geracao `AUTOMATICA`, a lista e intercalada por posicao entre os grupos.
Grupos que conquistaram vaga extra recebem prioridade na intercalacao; as
vagas extras sao anexadas ao final da lista. A distribuicao da chave por
posicoes permanece. Em `ALEATORIA`, sorteiam-se apenas os classificados.
Confrontos manuais definidos pelo admin continuam tendo prioridade.

O inicio do mata-mata continua dependendo de solicitacao apos todos os jogos
classificatorios finalizados. Desistencias, vagas manuais do mesmo grupo e
avanco automatico das rodadas eliminatorias mantem as regras existentes.

## Composicao manual pelo ADMIN

A rota `POST /campeonatos/{id}/iniciar/grupos`, restrita a `ADMIN`, aceita agora
`grupos`, uma lista de listas de IDs de participantes do campeonato:

```json
{
  "grupos": [[252, 253, 254], [255, 256, 257], [258, 259, 260]],
  "quantidadeClassificados": 8,
  "turnoEreturno": false,
  "formaGeracaoMataMata": "ALEATORIA"
}
```

Todos os participantes devem aparecer uma unica vez, cada grupo com pelo menos
tres membros. A quantidade de grupos e deduzida do array, e a ordem informada
define Grupo A, B, C etc. Nao envie `quantidadeGrupos` nessa rota.
Os campos antigos `grupoA` e `grupoB` continuam aceitos para dois grupos,
tambem com no minimo tres membros em cada. Nao combine os dois contratos.

Na composicao manual, grupos podem ter tamanhos diferentes; cada um precisa
comportar suas vagas garantidas (`quantidadeClassificados / quantidadeGrupos`).

## Retorno e persistencia

`GET /campeonatos/{id}` retorna `fases[].quantidadeGrupos` e a lista completa
em `fases[].grupos`. O primeiro campo e `null` em fases que nao sao de grupos.
As rotas existentes de grupos e classificacao por grupo continuam funcionando.

O numero e persistido pela propria composicao dos grupos; nao ha migration
adicional. Campeonatos antigos ja iniciados, inclusive grupos menores que tres,
continuam consultaveis e nao sao reorganizados.
