package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.CampeonatoStatus;
import java.util.List;

public record CampeonatoResponse(Long id, OrganizacaoResponse organizacao, Integer edicao,
        CampeonatoStatus status, List<ParticipanteResponse> participantes,
        List<FaseResponse> fases, List<ClassificacaoResponse> classificacaoPorRodada) {
}
