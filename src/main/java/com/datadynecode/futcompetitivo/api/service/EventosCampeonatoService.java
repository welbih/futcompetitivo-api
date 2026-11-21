package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.EventoCampeonatoResponse;
import com.datadynecode.futcompetitivo.api.entity.MotivoAtualizacaoCampeonato;
import com.datadynecode.futcompetitivo.api.repository.CampeonatoRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class EventosCampeonatoService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventosCampeonatoService.class);
    private final CampeonatoSseService sse;
    private final CampeonatoRepository repository;
    private final Object chave = new Object();

    @Transactional
    public SseEmitter conectar(Long campeonatoId) {
        // Serializa com exclusao: nao abre uma conexao depois de perder a notificacao de exclusao.
        repository.buscarParaAtualizar(campeonatoId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Campeonato " + campeonatoId + " não encontrado."));
        return sse.conectar(campeonatoId);
    }

    public void registrar(Long campeonatoId, MotivoAtualizacaoCampeonato motivo) {
        registrar(campeonatoId, motivo, null, null);
    }

    public void registrar(Long campeonatoId, MotivoAtualizacaoCampeonato motivo, Long rodadaId, Long partidaId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("Eventos de campeonato exigem uma transação ativa.");
        Pendentes pendentes = (Pendentes) TransactionSynchronizationManager.getResource(chave);
        if (pendentes == null) {
            pendentes = new Pendentes();
            TransactionSynchronizationManager.bindResource(chave, pendentes);
            TransactionSynchronizationManager.registerSynchronization(pendentes);
        }
        var anterior = pendentes.eventos.get(campeonatoId);
        if (anterior == null || anterior.motivo() != MotivoAtualizacaoCampeonato.CAMPEONATO_EXCLUIDO)
            pendentes.eventos.put(campeonatoId, new Pendente(campeonatoId, motivo, rodadaId, partidaId));
    }

    private record Pendente(Long campeonatoId, MotivoAtualizacaoCampeonato motivo, Long rodadaId, Long partidaId) {}

    private final class Pendentes implements TransactionSynchronization {
        private final Map<Long, Pendente> eventos = new LinkedHashMap<>();
        @Override public void suspend() { TransactionSynchronizationManager.unbindResource(chave); }
        @Override public void resume() { TransactionSynchronizationManager.bindResource(chave, this); }
        @Override public void afterCommit() {
            eventos.values().forEach(evento -> {
                try {
                    sse.publicar(new EventoCampeonatoResponse(evento.campeonatoId(), evento.motivo(),
                            Instant.now(), evento.rodadaId(), evento.partidaId()));
                } catch (RuntimeException e) {
                    LOGGER.warn("Falha ao notificar campeonato {} após commit", evento.campeonatoId(), e);
                }
            });
        }
        @Override public void afterCompletion(int status) {
            if (TransactionSynchronizationManager.getResource(chave) == this)
                TransactionSynchronizationManager.unbindResource(chave);
        }
    }
}
