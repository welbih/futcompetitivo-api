package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.EventoCampeonatoResponse;
import com.datadynecode.futcompetitivo.api.entity.MotivoAtualizacaoCampeonato;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Transporte local: nenhuma escrita em socket acontece na thread da transacao. */
@Service
public class CampeonatoSseService {
    private final Map<UUID, Conexao> conexoes = new ConcurrentHashMap<>();
    private final Semaphore vagas;
    private final long timeoutMs;
    private final long timeoutEnvioNanos;
    private final int capacidadeFila;
    private final LongFunction<SseEmitter> criarEmitter;
    private final ScheduledExecutorService manutencao;
    private final AtomicBoolean encerrado = new AtomicBoolean();

    @Autowired
    public CampeonatoSseService(@Value("${campeonatos.sse.heartbeat-ms:15000}") long heartbeatMs,
            @Value("${campeonatos.sse.timeout-ms:1800000}") long timeoutMs,
            @Value("${campeonatos.sse.timeout-envio-ms:10000}") long timeoutEnvioMs,
            @Value("${campeonatos.sse.capacidade-fila:32}") int capacidadeFila,
            @Value("${campeonatos.sse.max-conexoes:1000}") int maxConexoes) {
        this(heartbeatMs, timeoutMs, timeoutEnvioMs, capacidadeFila, maxConexoes, SseEmitter::new);
    }

    CampeonatoSseService(long heartbeatMs, long timeoutMs, long timeoutEnvioMs, int capacidadeFila,
            int maxConexoes, LongFunction<SseEmitter> criarEmitter) {
        if (heartbeatMs <= 0 || timeoutMs <= 0 || timeoutEnvioMs <= 0 || capacidadeFila <= 0 || maxConexoes <= 0)
            throw new IllegalArgumentException("Os limites SSE devem ser positivos.");
        this.timeoutMs = timeoutMs;
        this.timeoutEnvioNanos = TimeUnit.MILLISECONDS.toNanos(timeoutEnvioMs);
        this.capacidadeFila = capacidadeFila;
        this.vagas = new Semaphore(maxConexoes);
        this.criarEmitter = criarEmitter;
        this.manutencao = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("sse-heartbeat").factory());
        long intervalo = Math.min(heartbeatMs, timeoutEnvioMs);
        manutencao.scheduleWithFixedDelay(() -> manterConexoes(heartbeatMs), intervalo, intervalo, TimeUnit.MILLISECONDS);
    }

    public SseEmitter conectar(Long campeonatoId) {
        if (encerrado.get() || !vagas.tryAcquire())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Limite de conexões SSE atingido. Tente novamente.");
        Conexao conexao;
        try { conexao = new Conexao(campeonatoId, criarEmitter.apply(timeoutMs)); }
        catch (RuntimeException e) { vagas.release(); throw e; }
        conexoes.put(conexao.id, conexao);
        conexao.emitter.onCompletion(conexao::remover);
        conexao.emitter.onTimeout(conexao::encerrar);
        conexao.emitter.onError(erro -> conexao.remover());
        conexao.enfileirar(new Entrega(SseEmitter.event().name("conectado").id(UUID.randomUUID().toString())
                .reconnectTime(3000).data(new Conectado(campeonatoId, Instant.now()), MediaType.APPLICATION_JSON), false));
        conexao.escritor = Thread.ofVirtual().name("sse-campeonato-" + campeonatoId).unstarted(conexao::escrever);
        conexao.escritor.start();
        if (encerrado.get()) conexao.encerrar();
        return conexao.emitter;
    }

    public void publicar(EventoCampeonatoResponse evento) {
        boolean excluido = evento.motivo() == MotivoAtualizacaoCampeonato.CAMPEONATO_EXCLUIDO;
        String nome = excluido ? "campeonato-excluido" : "campeonato-atualizado";
        String id = UUID.randomUUID().toString();
        for (Conexao conexao : conexoes.values()) {
            if (conexao.campeonatoId.equals(evento.campeonatoId()) && !conexao.terminando.get()) {
                if (excluido) conexao.terminando.set(true);
                conexao.enfileirar(new Entrega(SseEmitter.event().name(nome).id(id)
                        .data(evento, MediaType.APPLICATION_JSON), excluido));
            }
        }
    }

    void manterConexoes(long heartbeatMs) {
        long agora = System.nanoTime();
        for (Conexao conexao : conexoes.values()) {
            if (agora - conexao.criadaEm >= TimeUnit.MILLISECONDS.toNanos(timeoutMs)
                    || (conexao.inicioEnvio != 0 && agora - conexao.inicioEnvio >= timeoutEnvioNanos)) {
                conexao.encerrar();
            } else if (!conexao.terminando.get()
                    && agora - conexao.ultimoHeartbeat >= TimeUnit.MILLISECONDS.toNanos(heartbeatMs)) {
                conexao.ultimoHeartbeat = agora;
                conexao.enfileirar(new Entrega(SseEmitter.event().comment("heartbeat"), false));
            }
        }
    }

    int quantidadeConexoes() { return conexoes.size(); }

    @PreDestroy
    public void encerrar() {
        encerrado.set(true);
        manutencao.shutdownNow();
        conexoes.values().forEach(Conexao::encerrar);
    }

    private record Conectado(Long campeonatoId,
            @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
            Instant ocorridoEm) {}
    private record Entrega(SseEmitter.SseEventBuilder evento, boolean finalizar) {}

    private final class Conexao {
        final UUID id = UUID.randomUUID();
        final Long campeonatoId;
        final SseEmitter emitter;
        final BlockingQueue<Entrega> fila = new ArrayBlockingQueue<>(capacidadeFila);
        final AtomicBoolean fechada = new AtomicBoolean();
        final AtomicBoolean terminando = new AtomicBoolean();
        final long criadaEm = System.nanoTime();
        volatile long ultimoHeartbeat = criadaEm;
        volatile long inicioEnvio;
        volatile Thread escritor;

        Conexao(Long campeonatoId, SseEmitter emitter) { this.campeonatoId = campeonatoId; this.emitter = emitter; }

        void enfileirar(Entrega entrega) {
            if (!fechada.get() && !fila.offer(entrega)) encerrar();
        }

        void escrever() {
            try {
                while (!fechada.get()) {
                    Entrega entrega = fila.take();
                    inicioEnvio = System.nanoTime();
                    emitter.send(entrega.evento());
                    inicioEnvio = 0;
                    if (entrega.finalizar()) { encerrar(); return; }
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (IOException | RuntimeException e) { encerrar(); }
            finally { remover(); }
        }

        boolean remover() {
            if (fechada.compareAndSet(false, true)) {
                conexoes.remove(id, this);
                vagas.release();
                fila.clear();
                if (escritor != null && escritor != Thread.currentThread()) escritor.interrupt();
                return true;
            }
            return false;
        }

        void encerrar() {
            // complete tambem pode interagir com a resposta servlet; isola do commit e do heartbeat.
            if (remover()) Thread.ofVirtual().name("sse-encerramento").start(() -> {
                try { emitter.complete(); } catch (RuntimeException ignored) { /* resposta ja encerrada */ }
            });
        }
    }
}
