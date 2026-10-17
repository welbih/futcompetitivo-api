package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.RodadaStatus;
import java.util.List;

public record RodadaResponse(Long id, Integer numero, RodadaStatus status,
        Long folgaParticipanteId, List<PartidaResponse> partidas) {
}
