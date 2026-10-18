# Filtro de situação de jogadores e equipes

As listagens `GET /jogadores` e `GET /equipes` aceitam o parâmetro opcional
`situacao`: `ATIVOS`, `INATIVOS` ou `AMBOS`. O padrão é `ATIVOS`.

| Consulta | Jogadores | Equipes |
| --- | --- | --- |
| Ativos | `/jogadores?situacao=ATIVOS` | `/equipes?situacao=ATIVOS` |
| Inativos | `/jogadores?situacao=INATIVOS` | `/equipes?situacao=INATIVOS` |
| Ambos | `/jogadores?situacao=AMBOS` | `/equipes?situacao=AMBOS` |

Os registros são ordenados pelo ID. Valores inválidos retornam HTTP 400.
O filtro aplica-se às listagens; as operações de consulta por ID e atualização
continuam exigindo registros ativos.

## Reativação

Envie `PUT /jogadores/{id}/ativar` ou `PUT /equipes/{id}/ativar`, sem corpo.
Retorna HTTP 200 com o registro e `ativo: true`. Repetir a operação em um registro
já ativo mantém o estado. Um ID inexistente retorna HTTP 404.
