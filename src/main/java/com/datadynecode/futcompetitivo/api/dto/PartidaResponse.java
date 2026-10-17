package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.StatusPartida;

public record PartidaResponse(Long id, ParticipanteResponse mandante,
        ParticipanteResponse visitante, StatusPartida status, ResultadoRequest resultado) {
}
