package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.TipoFormato;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;

public record IniciarCampeonatoRequest(
        @NotNull(message = "formato é obrigatório.") TipoFormato formato,
        Boolean turnoEreturno,
        @Positive Integer jogosPorParticipante,
        @Positive Integer totalRodadas,
        @Min(value = 4, message = "O mata-mata deve ter no mínimo 4 classificados.") Integer quantidadeClassificados) {

    public IniciarCampeonatoRequest(TipoFormato formato, Boolean turnoEreturno,
            Integer jogosPorParticipante, Integer totalRodadas) {
        this(formato, turnoEreturno, jogosPorParticipante, totalRodadas, null);
    }
}
