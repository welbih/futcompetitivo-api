package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.List;

public record CampeonatoRequest(
        @NotNull(message = "organizacao é obrigatório.") @Positive Long organizacaoId,
        @NotNull(message = "edicao é obrigatório.") @Positive Integer edicao,
        @NotNull(message = "participantes é obrigatório.")
        @Size(min = 4, message = "O campeonato deve ter no mínimo 4 participantes.")
        List<@NotNull @Valid ParticipanteRequest> participantes) {
}
