package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ResultadoPartidaRequest(
        @NotNull(message = "partidaId é obrigatório.") @Positive Long partidaId,
        @NotNull(message = "resultado é obrigatório.") @Valid ResultadoRequest resultado) {
}
