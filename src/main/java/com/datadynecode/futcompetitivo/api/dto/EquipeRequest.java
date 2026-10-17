package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EquipeRequest(
        @NotBlank(message = "nome é obrigatório.") @Size(max = 255) String nome,
        @NotBlank(message = "logoUrl é obrigatório.") @Size(max = 255) String logoUrl) {
}
