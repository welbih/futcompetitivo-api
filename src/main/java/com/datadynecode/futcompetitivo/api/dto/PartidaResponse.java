package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.StatusPartida;

public record PartidaResponse(Long id, ParticipanteResponse mandante,
        ParticipanteResponse visitante, StatusPartida status, ResultadoRequest resultado, String youtubeUrl, boolean wo) {
    public PartidaResponse(Long id, ParticipanteResponse mandante, ParticipanteResponse visitante,
            StatusPartida status, ResultadoRequest resultado, String youtubeUrl) {
        this(id, mandante, visitante, status, resultado, youtubeUrl, false);
    }
}
