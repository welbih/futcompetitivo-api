package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.TipoFormato;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record IniciarCampeonatoRequest(
        @NotNull(message = "formato é obrigatório.") TipoFormato formato,
        Boolean turnoEreturno,
        @Positive Integer jogosPorParticipante,
        @Positive Integer totalRodadas) {
}
