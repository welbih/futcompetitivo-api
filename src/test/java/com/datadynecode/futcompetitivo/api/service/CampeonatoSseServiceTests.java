package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.EventoCampeonatoResponse;
import com.datadynecode.futcompetitivo.api.entity.MotivoAtualizacaoCampeonato;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CampeonatoSseServiceTests {
    @Test
    void isolaCampeonatosEEncerraSomenteConexoesDoExcluido() throws Exception {
        var um=mock(SseEmitter.class); var dois=mock(SseEmitter.class);
        var emissores=new ConcurrentLinkedQueue<>(java.util.List.of(um,dois));
        var sse=new CampeonatoSseService(60000,120000,10000,32,10,t -> emissores.remove());
        try {
            sse.conectar(1L); sse.conectar(2L);
            verify(um,timeout(1500)).send(any(SseEmitter.SseEventBuilder.class));
            verify(dois,timeout(1500)).send(any(SseEmitter.SseEventBuilder.class));
            sse.publicar(evento(1L,MotivoAtualizacaoCampeonato.RESULTADO_ALTERADO));
            verify(um,timeout(1500).times(2)).send(any(SseEmitter.SseEventBuilder.class));
            verify(dois,times(1)).send(any(SseEmitter.SseEventBuilder.class));
            sse.publicar(evento(1L,MotivoAtualizacaoCampeonato.CAMPEONATO_EXCLUIDO));
            verify(um,timeout(1500)).complete();
            assertEquals(1,sse.quantidadeConexoes());
            verify(dois,never()).complete();
            var captor=ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
            verify(um,times(3)).send(captor.capture());
            assertTrue(captor.getAllValues().getLast().build().stream().anyMatch(d -> d.getData().toString().contains("campeonato-excluido")));
        } finally {sse.encerrar();}
    }

    @Test
    void removeConclusaoExpiracaoEErro() throws Exception {
        for(String modo:java.util.List.of("conclusao","timeout","erro")) {
            var emitter=mock(SseEmitter.class);
            var conclusao=new AtomicReference<Runnable>(); var expiracao=new AtomicReference<Runnable>();
            var erro=new AtomicReference<java.util.function.Consumer<Throwable>>();
            doAnswer(i -> {conclusao.set(i.getArgument(0));return null;}).when(emitter).onCompletion(any());
            doAnswer(i -> {expiracao.set(i.getArgument(0));return null;}).when(emitter).onTimeout(any());
            doAnswer(i -> {erro.set(i.getArgument(0));return null;}).when(emitter).onError(any());
            var sse=new CampeonatoSseService(60000,120000,10000,4,1,t -> emitter);
            try {
                sse.conectar(1L);
                assertEquals(1,sse.quantidadeConexoes());
                assertThrows(org.springframework.web.server.ResponseStatusException.class,()->sse.conectar(2L));
                switch(modo) {
                    case "conclusao" -> conclusao.get().run();
                    case "timeout" -> expiracao.get().run();
                    default -> erro.get().accept(new IOException("desconectado"));
                }
                assertEquals(0,sse.quantidadeConexoes());
                conclusao.get().run(); // callback repetido nao devolve duas vagas
                sse.conectar(2L);
                assertEquals(1,sse.quantidadeConexoes());
            } finally {sse.encerrar();}
        }
    }

    @Test
    void falhaDeEnvioRemoveCliente() throws Exception {
        var emitter=mock(SseEmitter.class);
        doThrow(new IOException("cliente desconectado")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        var sse=new CampeonatoSseService(60000,120000,10000,4,2,t -> emitter);
        try {
            sse.conectar(1L);
            verify(emitter,timeout(1500)).complete();
            assertEquals(0,sse.quantidadeConexoes());
        } finally {sse.encerrar();}
    }

    @Test
    void clienteLentoNaoBloqueiaPublicacaoOuOutroClienteEFilaELimitada() throws Exception {
        var lento=mock(SseEmitter.class); var rapido=mock(SseEmitter.class);
        var entrou=new CountDownLatch(1); var liberar=new CountDownLatch(1);
        var chamadas=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(i -> {if(chamadas.incrementAndGet()>1) {entrou.countDown(); liberar.await();} return null;})
                .when(lento).send(any(SseEmitter.SseEventBuilder.class));
        var emissores=new ConcurrentLinkedQueue<>(java.util.List.of(lento,rapido));
        var sse=new CampeonatoSseService(60000,120000,10000,1,2,t -> emissores.remove());
        try {
            sse.conectar(1L); sse.conectar(1L);
            verify(lento,timeout(1500)).send(any(SseEmitter.SseEventBuilder.class));
            verify(rapido,timeout(1500)).send(any(SseEmitter.SseEventBuilder.class));
            assertTimeout(Duration.ofSeconds(1),()->sse.publicar(evento(1L,MotivoAtualizacaoCampeonato.RESULTADO_ALTERADO)));
            assertTrue(entrou.await(1,TimeUnit.SECONDS));
            verify(rapido,timeout(1500).times(2)).send(any(SseEmitter.SseEventBuilder.class));
            sse.publicar(evento(1L,MotivoAtualizacaoCampeonato.RESULTADO_ALTERADO));
            verify(rapido,timeout(1500).times(3)).send(any(SseEmitter.SseEventBuilder.class));
            sse.publicar(evento(1L,MotivoAtualizacaoCampeonato.RESULTADO_ALTERADO));
            verify(rapido,timeout(1500).times(4)).send(any(SseEmitter.SseEventBuilder.class));
            verify(lento,timeout(1500)).complete();
            assertEquals(1,sse.quantidadeConexoes());
        } finally {liberar.countDown();sse.encerrar();}
    }

    @Test
    void timeoutDeEscritaRemoveConexaoMesmoSemFilaCheia() throws Exception {
        var emitter=mock(SseEmitter.class); var liberar=new CountDownLatch(1);
        doAnswer(i -> {liberar.await();return null;}).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        var sse=new CampeonatoSseService(60000,120000,30,32,1,t -> emitter);
        try {
            sse.conectar(1L);
            verify(emitter,timeout(1500)).complete();
            assertEquals(0,sse.quantidadeConexoes());
        } finally {liberar.countDown();sse.encerrar();}
    }

    @Test
    void encerraConexoesAoAtingirTempoDeVida() throws Exception {
        var emitter=mock(SseEmitter.class);
        var sse=new CampeonatoSseService(10,40,1000,32,1,t -> emitter);
        try {
            sse.conectar(1L);
            verify(emitter,timeout(1500)).complete();
            assertEquals(0,sse.quantidadeConexoes());
        } finally {sse.encerrar();}
    }

    private EventoCampeonatoResponse evento(Long id,MotivoAtualizacaoCampeonato motivo) {
        return new EventoCampeonatoResponse(id,motivo,Instant.now(),null,null);
    }
}
