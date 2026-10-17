package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.dto.ParticipanteRequest;
import com.datadynecode.futcompetitivo.api.dto.ParticipanteResponse;
import com.datadynecode.futcompetitivo.api.service.ParticipanteService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/participantes")
@RequiredArgsConstructor
public class ParticipanteController {

    private final ParticipanteService service;

    @GetMapping
    public List<ParticipanteResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public ParticipanteResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<ParticipanteResponse> criar(@Valid @RequestBody ParticipanteRequest request) {
        ParticipanteResponse response = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public ParticipanteResponse atualizar(@PathVariable Long id,
            @Valid @RequestBody ParticipanteRequest request) {
        return service.atualizar(id, request);
    }

}
