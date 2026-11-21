package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.service.EventosCampeonatoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/campeonatos")
@RequiredArgsConstructor
public class CampeonatoEventosController {
    private final EventosCampeonatoService eventos;

    @GetMapping(value = "/{id}/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> conectar(@PathVariable Long id) {
        SseEmitter emitter = eventos.conectar(id);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, no-transform")
                .header("X-Accel-Buffering", "no")
                .contentType(new MediaType("text", "event-stream", java.nio.charset.StandardCharsets.UTF_8))
                .body(emitter);
    }
}
