package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.StatusConfrontoPessoal;
import com.datadynecode.futcompetitivo.api.entity.TipoSeriePessoal;
import java.util.List;

public record ConfrontoPessoalResponse(Long id, ParticipanteResponse participanteUm, ParticipanteResponse participanteDois,
        TipoSeriePessoal tipo, boolean encerrarAntecipadamente, StatusConfrontoPessoal status,
        int vitoriasParticipanteUm, int vitoriasParticipanteDois, ParticipanteResponse vencedor,
        List<PartidaPessoalResponse> partidas) {
}
