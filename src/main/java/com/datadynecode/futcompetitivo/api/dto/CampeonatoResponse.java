package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.CampeonatoStatus;
import java.util.List;

public record CampeonatoResponse(Long id, OrganizacaoResponse organizacao, Integer edicao,
        CampeonatoStatus status, List<ParticipanteResponse> participantes,
        List<FaseResponse> fases, List<ClassificacaoResponse> classificacaoPorRodada,
        List<Long> participantesDesistentes, boolean aguardandoMataMata, java.util.Map<Long, Long> substituicoesMataMata) {
    public CampeonatoResponse(Long id, OrganizacaoResponse organizacao, Integer edicao,
            CampeonatoStatus status, List<ParticipanteResponse> participantes, List<FaseResponse> fases,
            List<ClassificacaoResponse> classificacaoPorRodada) {
        this(id, organizacao, edicao, status, participantes, fases, classificacaoPorRodada, List.of(), false, java.util.Map.of());
    }
}
