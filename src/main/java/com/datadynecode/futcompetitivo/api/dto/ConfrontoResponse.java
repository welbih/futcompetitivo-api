package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.TipoConfronto;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ConfrontoResponse(Long id, Long participanteUmId, Long participanteDoisId,
        Long origemUmId, Long origemDoisId, TipoConfronto tipo,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<PartidaResponse> partidas, EtapaResponse etapa,
        boolean terceiroLugar, @JsonInclude(JsonInclude.Include.NON_NULL) List<Long> partidaIds) {

    public ConfrontoResponse(Long id, Long participanteUmId, Long participanteDoisId,
            Long origemUmId, Long origemDoisId, TipoConfronto tipo, List<PartidaResponse> partidas,
            EtapaResponse etapa, boolean terceiroLugar) {
        this(id, participanteUmId, participanteDoisId, origemUmId, origemDoisId, tipo,
                partidas, etapa, terceiroLugar, null);
    }
}
