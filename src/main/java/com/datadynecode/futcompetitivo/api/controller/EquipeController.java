package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.dto.EquipeRequest;
import com.datadynecode.futcompetitivo.api.dto.EquipeResponse;
import com.datadynecode.futcompetitivo.api.service.EquipeService;
import jakarta.validation.Valid;
import com.datadynecode.futcompetitivo.api.dto.SituacaoCadastro;
import org.springframework.web.bind.annotation.RequestParam;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/equipes")
@RequiredArgsConstructor
public class EquipeController {

    private final EquipeService service;

    @GetMapping
    public List<EquipeResponse> listar(@RequestParam(defaultValue = "ATIVOS") SituacaoCadastro situacao) {
        return service.listar(situacao);
    }

    @GetMapping("/{id}")
    public EquipeResponse buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<EquipeResponse> criar(@Valid @RequestBody EquipeRequest request) {
        EquipeResponse response = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public EquipeResponse atualizar(@PathVariable Long id,
            @Valid @RequestBody EquipeRequest request) {
        return service.atualizar(id, request);
    }

    @PutMapping("/{id}/ativar")
    public EquipeResponse ativar(@PathVariable Long id) {
        return service.ativar(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desativar(@PathVariable Long id) {
        service.desativar(id);
        return ResponseEntity.noContent().build();
    }
}
