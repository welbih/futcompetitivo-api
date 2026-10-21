package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.StatusPartida;

public record PartidaPessoalResponse(Long id, int numero, ParticipanteResponse mandante,
        ParticipanteResponse visitante, StatusPartida status, ResultadoRequest resultado) {
}
