package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.service.ConfrontoPessoalService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/x1")
@RequiredArgsConstructor
public class ConfrontoPessoalController {
    private final ConfrontoPessoalService service;

    @PostMapping
    public ResponseEntity<ConfrontoPessoalResponse> criar(@Valid @RequestBody ConfrontoPessoalRequest request) {
        var response = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<ConfrontoPessoalResponse> listar() { return service.listar(); }

    @GetMapping("/{id}")
    public ConfrontoPessoalResponse buscar(@PathVariable Long id) { return service.buscar(id); }

    @PutMapping("/{id}/partidas/{partidaId}/resultado")
    public ConfrontoPessoalResponse registrarResultado(@PathVariable Long id, @PathVariable Long partidaId,
            @Valid @RequestBody ResultadoRequest request) {
        return service.registrarResultado(id, partidaId, request);
    }

    @PostMapping("/{id}/encerrar")
    public ConfrontoPessoalResponse encerrar(@PathVariable Long id) { return service.encerrar(id); }
}
