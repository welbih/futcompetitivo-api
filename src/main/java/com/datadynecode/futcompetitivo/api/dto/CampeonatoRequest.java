package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record CampeonatoRequest(
        @NotNull(message = "organizacao é obrigatório.") @Positive Long organizacaoId,
        @NotNull(message = "edicao é obrigatório.") @Positive Integer edicao,
        @NotEmpty(message = "participantes é obrigatório.")
        List<@NotNull @Positive Long> participanteIds) {
}
