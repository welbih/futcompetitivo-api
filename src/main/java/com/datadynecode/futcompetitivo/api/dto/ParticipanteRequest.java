package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ParticipanteRequest(
        @NotNull(message = "jogador é obrigatório.")
        @Positive(message = "jogadorId deve ser positivo.") Long jogadorId,
        @NotNull(message = "equipe é obrigatório.")
        @Positive(message = "equipeId deve ser positivo.") Long equipeId) {
}
