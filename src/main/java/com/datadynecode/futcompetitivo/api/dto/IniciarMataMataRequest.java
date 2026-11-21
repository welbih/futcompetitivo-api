package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record IniciarMataMataRequest(List<@NotNull @Valid Substituicao> substituicoes,
        List<@NotNull @Valid ConfrontoInicialRequest> confrontosPrimeiraRodada) {
    public IniciarMataMataRequest(List<Substituicao> substituicoes) { this(substituicoes, null); }
    public record Substituicao(@NotNull @Positive Long desistenteId, @NotNull @Positive Long substitutoId) {}
}
