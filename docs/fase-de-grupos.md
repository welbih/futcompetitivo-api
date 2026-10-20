# Fase de grupos e mata-mata

Crie o campeonato normalmente com pelo menos 4 participantes. Para iniciar,
envie `POST /campeonatos/{id}/iniciar`:

```json
{
  "formato": "GRUPOS",
  "turnoEreturno": false,
  "quantidadeClassificados": 5
}
```

`quantidadeClassificados` é obrigatório, deve ser pelo menos 4 e não pode exceder
o total de participantes. No exemplo, são necessários pelo menos 5 participantes.
Esta versão sempre gera dois grupos, A e B, sorteados ao iniciar. A distribuição
é equilibrada: grupos iguais ou com diferença de um participante. Cada grupo joga
uma liga completa, em turno único ou turno e returno.

## Agenda e classificação

`GET /campeonatos/{id}` retorna `fases[0].tipo: GRUPOS` e `fases[0].grupos`, cada
um com `id`, `nome`, `participantes` e `classificacaoPorRodada`.
`fases[0].rodadas` contém a agenda conjunta: todas as partidas são internas aos
respectivos grupos. Rodadas de mesmo número dos grupos são disputadas em conjunto;
se um grupo termina antes, as rodadas restantes contêm apenas jogos do outro.
Não há partidas fictícias para participantes que folgam.

As classificações são independentes e usam os critérios existentes: pontos,
vitórias, saldo de gols, gols marcados e a ordem do sorteio inicial, que permanece
estável em caso de empate completo. Cada rodada finalizada cria um
snapshot por grupo. Não é gerada uma classificação única misturando os grupos.

- `GET /campeonatos/{id}/grupos`: participantes e histórico dos dois grupos.
- `GET /campeonatos/{id}/grupos/{grupoId}/classificacao`: última classificação do grupo.
- `GET /campeonatos/{id}/classificados?quantidade=5`: classificados após encerrar os grupos.

Os endpoints individuais de placar continuam finalizando automaticamente a rodada
ao preencher todas as partidas. O envio parcial em lote mantém a rodada aberta,
exigindo sua finalização explícita. A primeira rodada entra em andamento ao iniciar.

## Vagas e cruzamentos

Com corte par, metade das vagas pertence a cada grupo. Com 4 classificados,
as semifinais são A1 × B2 e B1 × A2. Com 6, passam os três primeiros de cada
grupo; os líderes recebem folga e a preliminar cruza os demais grupos.

Com corte ímpar, cada grupo tem `quantidadeClassificados / 2` vagas garantidas,
desconsiderando a parte decimal. A vaga extra é disputada pelos próximos colocados.
Se somente um grupo ainda tiver um candidato, a vaga pertence a ele. Caso ambos
tenham candidatos, compara-se, nesta ordem:

1. Pontos por jogo.
2. Vitórias por jogo.
3. Saldo de gols por jogo.
4. Gols marcados por jogo.
5. A ordem do sorteio inicial do campeonato.

A comparação usa proporções exatas, sem arredondamento, para considerar grupos
de tamanhos diferentes. Com 5 classificados, passam os dois primeiros de cada
grupo e o melhor terceiro. A disposição na chave é ajustada para cruzar grupos
distintos nos jogos iniciais, inclusive na preliminar. Os líderes ficam em lados
opostos e as folgas favorecem os melhores colocados de cada grupo.

O mata-mata é gerado automaticamente somente quando todas as rodadas dos grupos
estão finalizadas. O campeonato segue em andamento até a final eliminatória.
O chaveamento completo e as etapas aparecem em `fases[1]`. Empates no mata-mata
exigem pênaltis. Consulte o campeão em `GET /campeonatos/{id}/vencedor`.

O histórico dos grupos é preservado durante o mata-mata. Correções anteriores a
qualquer resultado eliminatório recalculam as classificações, a vaga extra e a
chave. Depois de registrar resultados no mata-mata, alterações nos placares dos
grupos são rejeitadas para preservar as partidas posteriores.

A migração V5 adiciona as estruturas sem alterar migrações anteriores ou apagar dados.

É possível adicionar `disputaTerceiroLugar: true` ao início para disputar o
terceiro lugar entre os perdedores das semifinais. A opção segue para a fase
eliminatória, e o campeonato aguarda o resultado dessa disputa e da final.
