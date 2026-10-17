package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record CampeonatoRequest(
        @NotBlank(message = "nome é obrigatório.") @Size(max = 255) String nome,
        @NotNull(message = "edicao é obrigatório.") @Positive Integer edicao,
        @NotEmpty(message = "participantes é obrigatório.")
        List<@NotNull @Positive Long> participanteIds) {
}
