package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ResultadoRequest(
        @NotNull(message = "golsMandante é obrigatório.") @PositiveOrZero Integer golsMandante,
        @NotNull(message = "golsVisitante é obrigatório.") @PositiveOrZero Integer golsVisitante,
        @PositiveOrZero Integer penaltisMandante,
        @PositiveOrZero Integer penaltisVisitante,
        Boolean golDeOuro) {
}
