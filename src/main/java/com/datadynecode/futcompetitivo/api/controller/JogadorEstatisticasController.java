package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.dto.JogadorEstatisticasResponse;
import com.datadynecode.futcompetitivo.api.dto.OrigemPartida;
import com.datadynecode.futcompetitivo.api.service.JogadorEstatisticasService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jogadores/{id}/estatisticas")
@RequiredArgsConstructor
public class JogadorEstatisticasController {
    private final JogadorEstatisticasService service;

    @GetMapping
    public JogadorEstatisticasResponse buscar(@PathVariable Long id,
            @RequestParam(defaultValue = "TODOS") OrigemPartida origem) {
        return service.buscar(id, origem);
    }
}
