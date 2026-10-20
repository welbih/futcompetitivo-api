package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.service.CampeonatoService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/campeonatos")
@RequiredArgsConstructor
public class CampeonatoController {
    private final CampeonatoService service;

    @PostMapping
    public ResponseEntity<CampeonatoResponse> criar(@Valid @RequestBody CampeonatoRequest request) {
        CampeonatoResponse response = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<CampeonatoResponse> listar() { return service.listar(); }

    @GetMapping("/{id}")
    public CampeonatoResponse buscar(@PathVariable Long id) { return service.buscar(id); }

    @PutMapping("/{id}")
    public CampeonatoResponse atualizar(@PathVariable Long id, @Valid @RequestBody CampeonatoRequest request) {
        return service.atualizar(id, request);
    }

    @PostMapping("/{id}/iniciar")
    public CampeonatoResponse iniciar(@PathVariable Long id, @Valid @RequestBody IniciarCampeonatoRequest request) {
        return service.iniciar(id, request);
    }

    @PostMapping("/{id}/rodadas/swiss")
    public RodadaResponse proximaRodadaSwiss(@PathVariable Long id) {
        return service.gerarProximaRodadaSwiss(id);
    }

    @PutMapping("/{id}/partidas/{partidaId}/resultado")
    public CampeonatoResponse registrarResultado(@PathVariable Long id, @PathVariable Long partidaId,
            @Valid @RequestBody ResultadoRequest request) {
        return service.registrarResultado(id, partidaId, request);
    }

    @PutMapping("/{id}/rodadas/{rodadaId}/resultados")
    public CampeonatoResponse registrarResultadosSemFinalizar(@PathVariable Long id, @PathVariable Long rodadaId,
            @Valid @RequestBody ResultadosRodadaRequest request) {
        return service.registrarResultadosSemFinalizar(id, rodadaId, request.resultados());
    }

    @PostMapping("/{id}/rodadas/{rodadaId}/finalizar")
    public CampeonatoResponse finalizarRodada(@PathVariable Long id, @PathVariable Long rodadaId) {
        return service.finalizarRodada(id, rodadaId);
    }

    @GetMapping("/{id}/vencedor")
    public ParticipanteResponse vencedor(@PathVariable Long id) { return service.vencedor(id); }

    @GetMapping("/{id}/terceiro-lugar")
    public ParticipanteResponse terceiroLugar(@PathVariable Long id) { return service.terceiroLugar(id); }

    @GetMapping("/{id}/classificacao")
    public ClassificacaoResponse classificacao(@PathVariable Long id) { return service.classificacao(id); }

    @GetMapping("/{id}/grupos")
    public List<GrupoResponse> grupos(@PathVariable Long id) { return service.grupos(id); }

    @GetMapping("/{id}/grupos/{grupoId}/classificacao")
    public ClassificacaoResponse classificacaoGrupo(@PathVariable Long id, @PathVariable Long grupoId) {
        return service.classificacaoGrupo(id, grupoId);
    }

    @GetMapping("/{id}/classificados")
    public List<ParticipanteResponse> classificados(@PathVariable Long id, @RequestParam int quantidade) {
        return service.classificados(id, quantidade);
    }
}
