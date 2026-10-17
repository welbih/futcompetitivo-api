package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrganizacaoRequest(
        @NotBlank(message = "nome é obrigatório.") @Size(max = 255) String nome,
        @NotNull(message = "ativo é obrigatório.") Boolean ativo) {
}
