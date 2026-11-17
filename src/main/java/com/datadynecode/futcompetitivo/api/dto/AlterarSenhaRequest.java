package com.datadynecode.futcompetitivo.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record AlterarSenhaRequest(
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank @Size(max = 72) String senhaAtual,
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @NotBlank @Size(min = 8, max = 72)
    @Pattern(regexp = "(?s)^(?=.*\\p{Lu})(?=.*\\p{Ll})(?=.*[0-9])(?=.*[^\\p{L}\\p{N}\\s]).*$",
        message = "Senha deve conter letra maiuscula, minuscula, numero e caractere especial") String novaSenha
) {
    @Override
    public String toString() {
        return "AlterarSenhaRequest[credenciais omitidas]";
    }
}
