package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.TipoFase;
import com.datadynecode.futcompetitivo.api.entity.FormatoEliminatorio;
import java.util.List;

public record FaseResponse(Long id, Integer ordem, TipoFase tipo,
        List<RodadaResponse> rodadas, List<ConfrontoResponse> confrontos, Integer quantidadeClassificados,
        List<GrupoResponse> grupos, FormatoEliminatorio formatoEliminatorio) {
}
