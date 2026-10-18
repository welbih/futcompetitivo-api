package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ResultadosRodadaRequest(
        @NotEmpty(message = "resultados é obrigatório.")
        List<@NotNull @Valid ResultadoPartidaRequest> resultados) {
}
