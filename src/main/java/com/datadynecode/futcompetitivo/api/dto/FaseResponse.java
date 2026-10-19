package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.TipoFase;
import java.util.List;

public record FaseResponse(Long id, Integer ordem, TipoFase tipo,
        List<RodadaResponse> rodadas, List<ConfrontoResponse> confrontos, Integer quantidadeClassificados) {
}
