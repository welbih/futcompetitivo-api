# Classificacao parcial

Disponivel em `LIGA`, `LIGA_PARCIAL`, `SWISS`, `MISTA`, `MISTA_PARCIAL` e em cada grupo de `GRUPOS`.
O formato puramente eliminatorio e X1 nao possuem classificacao em tabela.

## Contrato para o frontend

Os endpoints existentes retornam a tabela atual, incluindo os resultados
finalizados da rodada em andamento:

```http
GET /campeonatos/{id}/classificacao
GET /campeonatos/{id}/grupos/{grupoId}/classificacao
```

Exemplo ilustrativo (o participante usa a mesma estrutura completa ja existente):

```json
{
  "rodada": 1,
  "parcial": true,
  "tabela": [
    {
      "participante": {
        "id": 252,
        "jogador": {"id": 1, "nome": "Joao", "apelido": "J", "ativo": true},
        "equipe": {"id": 2, "nome": "Equipe A", "logoUrl": "https://example.com/logo.png", "ativo": true},
        "ativo": true
      },
      "pontos": 3,
      "jogos": 1,
      "vitorias": 1,
      "empates": 0,
      "derrotas": 0,
      "golsPro": 2,
      "golsContra": 1,
      "saldoGols": 1,
      "posicaoAtual": 1,
      "posicaoAnterior": 1,
      "variacao": 0,
      "movimentacao": "MANTEVE"
    }
  ]
}
```

A tabela real inclui todos os participantes do campeonato/grupo, inclusive
quem ainda nao jogou. O exemplo omite as demais linhas.

O retorno completo de `GET /campeonatos/{id}` e das operacoes de resultados
tambem disponibiliza:

- `classificacaoAtual`: tabela atual de liga, liga parcial, misto ou suico.
  E `null` em campeonato nao iniciado, eliminatorio puro e formato de grupos.
- `fases[].grupos[].classificacaoAtual`: tabela atual de cada grupo. Tambem
  aparece nos itens de `GET /campeonatos/{id}/grupos`.
- `classificacaoPorRodada`: continua sendo o historico de rodadas finalizadas,
  com `parcial: false`. O mesmo vale para o historico dentro de cada grupo.

Use `classificacaoAtual` para a tabela ao vivo e o historico para evolucao por
rodada. Nao use o ultimo item do historico como tabela parcial.

## Regras de atualizacao

- A inclusao ou edicao de resultado ja aparece na tabela retornada pela API;
  consultas posteriores tambem refletem o resultado salvo.
- So entram partidas `FINALIZADA` de rodadas encerradas ou da rodada atual.
  Canceladas, pendentes e rodadas futuras nao contam, mesmo que tenham W.O.
  antecipado por desistencia.
- Os placares sao recalculados a partir dos jogos, sem somar novamente um jogo
  editado. Penaltis nao sao somados aos gols; folga nao conta como jogo ou ponto.
- A parcial nao e gravada como um novo snapshot historico: e uma projecao dos
  resultados atuais. Consultas nao alteram dados e nao geram rodadas.
- `parcial: true` indica rodada em andamento, inclusive com zero resultados ou
  com todos os placares preenchidos mas ainda aguardando finalizacao manual.
- Ao encerrar uma rodada, o snapshot dela fica definitivo no historico. Se a
  proxima rodada ja estiver aberta, `classificacaoAtual` passa a identifica-la
  como parcial, inicialmente carregando apenas os resultados anteriores.
- As movimentacoes de posicao sao comparadas com a rodada anterior encerrada,
  nunca com o resultado parcial da consulta anterior.
- Pontos, vitorias, saldo e gols marcados continuam sendo os criterios. Empates
  completos usam a ordem inicial dos participantes, ja definida ao iniciar o
  campeonato/grupo; recalcular nao realiza novo sorteio. Snapshots antigos nao
  sao alterados por uma simples consulta.

## Cuidados por formato

- **Liga e liga parcial:** a tabela atualiza a cada jogo; a abertura da proxima
  rodada continua seguindo as regras existentes de encerramento.
- **Suico:** gerar a proxima rodada continua exigindo a anterior finalizada.
  Os pareamentos usam a classificacao encerrada, nunca a parcial. Corrigir um
  resultado antigo recalcula as tabelas afetadas, preservando os pares ja gerados.
- **Misto e grupos:** ver uma tabela parcial nao libera o mata-mata nem os
  classificados definitivos. Todos os jogos/rodadas classificatorias devem ser
  finalizados, e a abertura do mata-mata continua dependendo de solicitacao.
- **Grupos:** cada tabela inclui apenas os jogos dos membros daquele grupo.
  O indicador acompanha a rodada conjunta da fase; um grupo pode estar sem
  novos jogos nessa rodada enquanto outros ainda jogam.
- Desistencias, jogadores/equipes inativos e substituicoes continuam respeitando
  o historico e as regras existentes. Correcoes apos a criacao do mata-mata
  mantem as restricoes de alteracao de classificados e de resultados ja disputados.

As rotas de consulta continuam publicas e as permissoes de alteracao de
resultados permanecem. Nao ha nova migration: apenas snapshots encerrados
continuam sendo persistidos.
