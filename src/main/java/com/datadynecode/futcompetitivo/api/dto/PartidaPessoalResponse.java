package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.StatusPartidaPessoal;

public record PartidaPessoalResponse(Long id, int numero, ParticipanteResponse mandante,
        ParticipanteResponse visitante, StatusPartidaPessoal status, ResultadoRequest resultado) {
}
