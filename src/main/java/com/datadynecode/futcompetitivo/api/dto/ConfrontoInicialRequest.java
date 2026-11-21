package com.datadynecode.futcompetitivo.api.dto;

import jakarta.validation.constraints.Positive;

public record ConfrontoInicialRequest(@Positive Long mandanteId, @Positive Long visitanteId) {}
