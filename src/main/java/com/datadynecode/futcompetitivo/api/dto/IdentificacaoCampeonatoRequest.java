package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record IdentificacaoCampeonatoRequest(
        @NotNull(message = "organizacaoId é obrigatório.") @Positive Long organizacaoId,
        @NotNull(message = "edicao é obrigatório.") @Positive
        @JsonDeserialize(using = EdicaoInteiraDeserializer.class) Integer edicao) {

    /** Restringe apenas este contrato; nao altera as coercoes dos endpoints existentes. */
    public static class EdicaoInteiraDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT)
                return context.reportInputMismatch(Integer.class, "edicao deve ser um número inteiro.");
            return parser.getIntValue();
        }
    }
}
