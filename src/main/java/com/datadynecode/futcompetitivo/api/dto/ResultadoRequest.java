package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ResultadoRequest(
        @NotNull(message = "golsMandante é obrigatório.") @PositiveOrZero Integer golsMandante,
        @NotNull(message = "golsVisitante é obrigatório.") @PositiveOrZero Integer golsVisitante,
        @PositiveOrZero Integer penaltisMandante,
        @PositiveOrZero Integer penaltisVisitante,
        Boolean golDeOuro,
        @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
        String youtubeUrl) {

    public ResultadoRequest(Integer golsMandante, Integer golsVisitante, Integer penaltisMandante,
            Integer penaltisVisitante, Boolean golDeOuro) {
        this(golsMandante, golsVisitante, penaltisMandante, penaltisVisitante, golDeOuro, null);
    }
}
