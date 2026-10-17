package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JogadorRequest(
        @NotBlank(message = "nome é obrigatório.") @Size(max = 255) String nome,
        @Size(max = 255) String apelido) {
}
