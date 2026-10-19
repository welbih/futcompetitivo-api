package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.TipoConfronto;
import java.util.List;

public record ConfrontoResponse(Long id, Long participanteUmId, Long participanteDoisId,
        Long origemUmId, Long origemDoisId, TipoConfronto tipo, List<PartidaResponse> partidas, EtapaResponse etapa) {
}
