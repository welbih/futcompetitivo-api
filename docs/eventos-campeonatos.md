# Atualizacoes de campeonatos por SSE

```http
GET /campeonatos/202/eventos
Accept: text/event-stream
```

Consulta publica, sem token, assim como os dados do campeonato. O campeonato e
validado antes da abertura: ID inexistente retorna `404` no formato de erro da
API, sem abrir stream. A assinatura e serializada com a exclusao para evitar
abrir uma conexao depois de perder a notificacao de exclusao.

Cada conexao recebe somente eventos do campeonato da URL. O stream nao aceita
operacoes de escrita. Resultados, agendas, inicio e operacoes administrativas
continuam usando as rotas HTTP e permissoes existentes.

## Eventos e contrato

Logo apos conectar, inclusive em toda reconexao:

```text
id: a138c455-ab37-42eb-bda1-531620948526
event: conectado
retry: 3000
data: {"campeonatoId":202,"ocorridoEm":"2026-11-20T23:00:00Z"}

```

O frontend deve consultar `GET /campeonatos/202` ao receber `conectado`.

Uma alteracao confirmada gera:

```text
id: 19bfa317-5757-4297-b83e-b02bc87d8aaa
event: campeonato-atualizado
data: {"campeonatoId":202,"motivo":"RESULTADO_ALTERADO","ocorridoEm":"2026-11-20T23:00:10Z","rodadaId":301,"partidaId":801}

```

`rodadaId` e `partidaId` sao `null` quando nao se aplicam. `ocorridoEm` e uma
data UTC em ISO-8601, gerada apos o commit. O ID e um UUID opaco, nao uma versao
do campeonato, numero sequencial ou cursor de consulta.

O evento e uma notificacao leve. Nao contem tabela, placar, chave completa,
credenciais ou dados privados. Ao recebe-lo, consulte novamente o campeonato
para obter o estado autoritativo, incluindo `classificacaoAtual`, resultados
e chaveamento. Nao tente aplicar diferencas usando apenas `motivo`.

### Valores de motivo

| Motivo | Operacao |
| --- | --- |
| `CAMPEONATO_CRIADO` | Cadastro do campeonato. Normalmente ainda nao existem assinantes. |
| `CAMPEONATO_ALTERADO` | Edicao de participantes/configuracoes permitidas enquanto `CRIADO`, ou correcao efetiva de organizacao/edicao pelo PATCH de identificacao em qualquer status. |
| `CAMPEONATO_INICIADO` | Inicio normal ou com grupos, liga parcial ou confrontos eliminatorios manuais. |
| `RESULTADO_ALTERADO` | Inclusao/correcao individual de resultado ou alteracao da URL de video. Inclui avanco automatico decorrente desse resultado. |
| `RESULTADOS_ALTERADOS` | Registro de resultados em lote, com `rodadaId` e `partidaId: null`. |
| `RODADA_ABERTA` | Geracao de rodada Swiss ou abertura explicita de rodada eliminatoria. |
| `RODADA_FINALIZADA` | Encerramento explicitamente solicitado de uma rodada, incluindo suas consequencias automaticas. |
| `MATA_MATA_INICIADO` | Criacao da fase eliminatoria apos a classificatoria, incluindo substituicoes de classificados e confrontos manuais enviados nessa operacao. |
| `DESISTENCIA_REGISTRADA` | Desistencia e aplicacao/ajuste de W.O., inclusive empate de dois desistentes e eventuais finalizacoes/avancos automaticos. |
| `DESISTENCIA_CANCELADA` | Retirada da desistencia, incluindo reversao/ajuste de W.O., reabertura de rodadas e atualizacao das classificacoes. |
| `CAMPEONATO_EXCLUIDO` | Exclusao confirmada; usa o evento especifico abaixo. |

Para exclusao:

```text
id: 5670a036-7180-4ca0-9df0-bd9628d7d2d9
event: campeonato-excluido
data: {"campeonatoId":202,"motivo":"CAMPEONATO_EXCLUIDO","ocorridoEm":"2026-11-20T23:05:00Z","rodadaId":null,"partidaId":null}

```

O backend envia esse evento e encerra as conexoes desse campeonato. O frontend
deve chamar `EventSource.close()` e retirar a tela do campeonato. As conexoes
de outros campeonatos permanecem abertas.

## Commit, agregacao e historico

Notificacoes de alteracao/exclusao so sao publicadas **apos commit bem-sucedido**.
Rejeicoes de validacao, erros de persistencia e rollback nao publicam eventos.

Uma transacao agrupa suas alteracoes em uma notificacao por campeonato. Uma
operacao em lote nao gera eventos individuais adicionais; a resolucao de varios
W.O.s ou a criacao automatica de jogos nao gera uma notificacao por partida.
Se uma transacao externa agrupar mais de uma operacao no mesmo campeonato, o
ultimo motivo registrado prevalece; exclusao sempre prevalece.

Transacoes HTTP diferentes podem produzir notificacoes diferentes, mesmo que
ocorram muito proximas. A ordem de entrega nao representa uma versao dos dados;
o GET e sempre a referencia. Repetir um resultado individual identico sem outra
alteracao ou repetir uma desistencia ja registrada nao gera nova notificacao.

O stream cobre as operacoes de `CampeonatoService`. Alteracoes feitas diretamente
por SQL e edicoes de cadastros globais de jogador/equipe/organizacao por outros
servicos nao sao detectadas por este mecanismo.

## Heartbeat e ciclo de vida

Por padrao, envia um comentario de heartbeat a cada 15 segundos:

```text
:heartbeat

```

Comentarios nao disparam `message` no EventSource, nao possuem ID e nao exigem
buscar o campeonato. O envio permite manter o stream ativo e detectar sockets
fechados. Conclusao, timeout, erro e falha de envio removem a assinatura.

Cada cliente tem uma fila limitada e uma thread virtual propria de escrita.
A thread da transacao apenas enfileira notificacoes, sem escrever no socket.
Fila cheia ou envio demorado fecha a conexao afetada; nao bloqueia os registros
de resultados nem a entrega aos demais clientes. Um monitor verifica os prazos
periodicamente, portanto o corte nao e um prazo exato de rede.

Configuracoes opcionais:

| Variavel | Padrao | Efeito |
| --- | --- | --- |
| `SSE_HEARTBEAT_MS` | `15000` | Intervalo dos comentarios de heartbeat. |
| `SSE_TIMEOUT_MS` | `1800000` | Duracao maxima de uma conexao (30 minutos); depois o cliente reconecta. |
| `SSE_TIMEOUT_ENVIO_MS` | `10000` | Prazo aproximado de uma escrita antes de remover o cliente lento. |
| `SSE_CAPACIDADE_FILA` | `32` | Quantidade maxima de entregas pendentes por cliente. |
| `SSE_MAX_CONEXOES` | `1000` | Limite de conexoes simultaneas nesta instancia; exceder retorna `503`. |

Todos os limites devem ser positivos. Uma tela deve compartilhar uma conexao
entre seus componentes e fecha-la ao trocar de campeonato/desmontar a pagina.

## Reconexao e Last-Event-ID

O EventSource tenta reconectar automaticamente quando perde a conexao. O campo
`retry: 3000` sugere intervalo de tres segundos. A cada nova conexao, o servidor
envia outro `conectado`, e o frontend deve executar um GET completo novamente.
Falhas HTTP como recurso inexistente ou limite de conexoes tambem precisam ser
tratadas na interface; nao assuma que qualquer resposta HTTP sera retentada.

O navegador pode enviar `Last-Event-ID`. O header e permitido pelo CORS, mas
**nao existe armazenamento nem replay**: o backend ignora esse cursor e nao
reproduz eventos perdidos. Eventos tambem nao sao duraveis entre reinicios ou
falhas apos commit. Sao notificacoes de melhor esforco, nao um log de auditoria.

Por isso, sempre sincronize em `conectado`. Se um cliente lento perder a exclusao,
o GET da reconexao retornara `404`; trate esse caso como campeonato removido.

## Exemplo com EventSource

```javascript
function acompanharCampeonato(apiUrl, campeonatoId, renderizar, removido) {
  const base = `${apiUrl}/campeonatos/${campeonatoId}`;
  const stream = new EventSource(`${base}/eventos`); // consulta publica, sem JWT
  const abort = new AbortController();
  let encerrado = false;
  let consultando = false;
  let pendente = false;
  let tentarNovamente;

  function fechar() {
    encerrado = true;
    clearTimeout(tentarNovamente);
    stream.close();
    abort.abort();
  }

  async function sincronizar() {
    if (encerrado) return;
    pendente = true;
    if (consultando) return;
    consultando = true;
    try {
      // Serializa os GETs e preserva uma nova notificacao chegada durante a busca.
      while (pendente && !encerrado) {
        pendente = false;
        const resposta = await fetch(base, {
          cache: "no-store",
          signal: abort.signal
        });
        if (resposta.status === 404) {
          fechar();
          removido();
          return;
        }
        if (!resposta.ok) throw new Error(`HTTP ${resposta.status}`);
        const campeonato = await resposta.json();
        if (!encerrado) renderizar(campeonato);
      }
    } catch (erro) {
      if (!encerrado) {
        console.error("Falha ao sincronizar campeonato", erro);
        clearTimeout(tentarNovamente);
        tentarNovamente = setTimeout(sincronizar, 3000);
      }
    } finally {
      consultando = false;
    }
  }

  stream.addEventListener("conectado", sincronizar);
  stream.addEventListener("campeonato-atualizado", sincronizar);
  stream.addEventListener("campeonato-excluido", () => {
    fechar();
    removido();
  });
  stream.onerror = () => {
    // Mostrar estado de reconexao. Nao fechar aqui: preserva a reconexao automatica.
    console.info("Stream indisponivel; aguardando reconexao");
  };
  return fechar; // chamar ao sair da pagina
}
```

## CORS, proxy e implantacao

`CORS_ORIGINS` continua sendo a lista de origens permitidas, separadas por
virgula. Inclua a origem exata do frontend, inclusive protocolo e porta.
`GET`, `Accept` e `Last-Event-ID` estao permitidos. O stream publico nao precisa
de cookies, `withCredentials` ou cabecalho `Authorization`.

A resposta envia `Content-Type: text/event-stream;charset=UTF-8`,
`Cache-Control: no-cache, no-store, no-transform` e `X-Accel-Buffering: no`.
Nao envie Content-Length nem force o header Connection, que e incompativel
com HTTP/2. Desabilite cache, buffering e compressao do stream em qualquer
proxy/CDN; configure o timeout de leitura/ociosidade acima do heartbeat.

Exemplo caso seja usado Nginx (nao existe configuracao Nginx neste repositorio):

```nginx
location ~ ^/campeonatos/[0-9]+/eventos$ {
    proxy_pass http://backend;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    gzip off;
    proxy_read_timeout 60s;
}
```

Em Railway, verifique se a rota publicada entrega o primeiro evento imediatamente
e depois os heartbeats, sem acumular a resposta. Qualquer limite maximo de
duracao imposto pela infraestrutura provocara reconexao e nova sincronizacao.
Nenhuma alteracao no Dockerfile e necessaria: ele executa uma JVM Spring MVC.

**Limitacao de escala:** o registro de conexoes e as notificacoes ficam na
memoria de uma unica instancia. O repositorio contem Docker para uma JVM e
PostgreSQL de desenvolvimento, sem broker ou barramento de eventos compartilhado;
nao define a quantidade de replicas efetivamente configurada no Railway.
Esta implementacao exige **uma replica do backend** para distribuir todas as
notificacoes aos clientes. Confirme essa configuracao na implantacao.

Com multiplas replicas, quem grava em A nao notifica clientes conectados em B.
Sticky sessions nao resolvem isso. Antes de escalar, conecte a publicacao apos
commit a um canal compartilhado (por exemplo, Redis Pub/Sub ou um broker) e
faca cada instancia encaminhar as mensagens aos seus clientes locais. Para
entrega duravel/replay, tambem seria necessario armazenamento/outbox e cursores.

Nao ha migration adicional nem novos dados privados persistidos para SSE.
