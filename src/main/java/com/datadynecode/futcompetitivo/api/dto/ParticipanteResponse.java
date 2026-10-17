package com.datadynecode.futcompetitivo.api.dto;

public record ParticipanteResponse(Long id, JogadorResponse jogador,
        EquipeResponse equipe, boolean ativo) {
}
