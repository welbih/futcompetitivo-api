package com.datadynecode.futcompetitivo.api.dto;

import java.util.List;

public record ClassificacaoResponse(int rodada, List<ClassificacaoParticipanteResponse> tabela) {
}
