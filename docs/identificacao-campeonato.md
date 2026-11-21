# Alterar organizacao e edicao do campeonato

Permite ao admin corrigir a identificacao sem alterar a competicao, nos status
`CRIADO`, `EM_ANDAMENTO` e `FINALIZADO`.

```http
PATCH /campeonatos/202/identificacao
Authorization: Bearer <token-admin>
Content-Type: application/json
```

```json
{
  "organizacaoId": 1,
  "edicao": 5
}
```

Os dois campos sao obrigatorios, mesmo se apenas um for alterado. Para corrigir
somente a edicao, envie o ID da organizacao atual. Para trocar somente a
organizacao, envie a edicao atual.

## Regras

- Somente `ROLE_ADMIN`. Sem autenticacao valida retorna `401`; com usuario
  autenticado sem a role, `403`.
- O campeonato e a organizacao devem existir (`404` caso contrario).
- `organizacaoId` deve ser positivo. Ao mudar de organizacao, a nova deve estar
  ativa. Trocar para uma organizacao existente mas inativa retorna `400`.
- Manter a organizacao atual e permitido mesmo que ela esteja inativa,
  inclusive para corrigir somente a edicao.
- `edicao` deve ser um numero JSON inteiro entre **1 e 2147483647**, limite do
  `Integer` usado no dominio/PostgreSQL. Nulos, ausencia, zero, negativos,
  decimais (inclusive `5.0`), notacao exponencial, strings, booleanos e valores
  fora do intervalo retornam `400`. Essa validacao estrita e local ao PATCH.
- O projeto atualmente nao possui restricao de unicidade de edicao por
  organizacao. O endpoint preserva isso: nao cria nova restricao nem bloqueia
  uma edicao ja utilizada por outro campeonato.
- Altera somente os campos de organizacao e edicao. Preserva status, IDs e
  ordem dos participantes, fases, grupos, agenda, placares, classificacoes,
  desistencias, substituicoes, lista base de classificados e chaveamento.
- A operacao e transacional e usa o bloqueio pessimista do campeonato, como
  as operacoes existentes de resultados e avanco.
- Reenviar os valores atuais retorna `200` com os mesmos dados, sem salvar
  novamente ou publicar notificacao SSE. Isso tambem vale na organizacao atual inativa.

Nao e necessario enviar participantes nem dados esportivos. Nao reutilize o
`PUT /campeonatos/{id}` para essa correcao: o PUT mantem seu comportamento
anterior, inclusive a restricao ao status `CRIADO` e a atualizacao dos participantes.

## Resposta

Retorna `200 application/json` com o **CampeonatoResponse completo**, no mesmo
contrato de `GET /campeonatos/{id}`. O frontend pode substituir seu estado local
pelo retorno. Exemplo de um campeonato ainda `CRIADO`, apos a correcao:

```json
{
  "id": 202,
  "organizacao": {"id": 1, "nome": "Organizacao Vila", "ativo": true, "trofeuUrl": null},
  "edicao": 5,
  "status": "CRIADO",
  "participantes": [
    {
      "id": 252,
      "jogador": {"id": 1, "nome": "Joao", "apelido": "J", "ativo": true},
      "equipe": {"id": 1, "nome": "Equipe A", "logoUrl": "https://example.com/a.png", "ativo": true},
      "ativo": true
    },
    {
      "id": 253,
      "jogador": {"id": 2, "nome": "Pedro", "apelido": null, "ativo": true},
      "equipe": {"id": 2, "nome": "Equipe B", "logoUrl": "https://example.com/b.png", "ativo": true},
      "ativo": true
    },
    {
      "id": 254,
      "jogador": {"id": 3, "nome": "Lucas", "apelido": null, "ativo": true},
      "equipe": {"id": 3, "nome": "Equipe C", "logoUrl": "https://example.com/c.png", "ativo": true},
      "ativo": true
    },
    {
      "id": 255,
      "jogador": {"id": 4, "nome": "Marcos", "apelido": null, "ativo": true},
      "equipe": {"id": 4, "nome": "Equipe D", "logoUrl": "https://example.com/d.png", "ativo": true},
      "ativo": true
    }
  ],
  "fases": [],
  "classificacaoPorRodada": [],
  "participantesDesistentes": [],
  "aguardandoMataMata": false,
  "substituicoesMataMata": {},
  "classificacaoAtual": null
}
```

Nos demais status, os dados esportivos ja existentes continuam presentes e
inalterados. Erros de validacao/regra usam `application/problem+json`, seguindo
o padrao da API; validacoes de campos podem incluir o mapa `erros`. Valores
JSON invalidos ou fora do intervalo sao tratados como erro de leitura do JSON.

## SSE

Se houver mudanca efetiva, publica depois do commit:

```text
event: campeonato-atualizado
data: {"campeonatoId":202,"motivo":"CAMPEONATO_ALTERADO","ocorridoEm":"2026-11-21T12:00:00Z","rodadaId":null,"partidaId":null}
```

O frame tambem possui o ID de evento UUID do contrato SSE. Rollback, rejeicao e
valores identicos nao publicam eventos. A agregacao por campeonato/transacao
continua valendo. Os demais clientes podem refazer `GET /campeonatos/{id}` ao
receber a notificacao. Veja [reconexao e limites de implantacao](eventos-campeonatos.md).

Nao ha migration nem alteracao de esquema para esse endpoint.
