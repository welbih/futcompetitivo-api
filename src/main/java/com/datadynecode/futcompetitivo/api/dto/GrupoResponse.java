package com.datadynecode.futcompetitivo.api.dto;

import java.util.List;

public record GrupoResponse(Long id, String nome, List<ParticipanteResponse> participantes,
        List<ClassificacaoResponse> classificacaoPorRodada) {
}
