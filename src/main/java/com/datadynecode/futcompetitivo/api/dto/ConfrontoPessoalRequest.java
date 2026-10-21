package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.TipoSeriePessoal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

public record ConfrontoPessoalRequest(
        @NotNull(message = "participanteUm é obrigatório.") @Valid ParticipanteRequest participanteUm,
        @NotNull(message = "participanteDois é obrigatório.") @Valid ParticipanteRequest participanteDois,
        @NotNull(message = "tipo é obrigatório.") TipoSeriePessoal tipo,
        Boolean encerrarAntecipadamente) {
}
