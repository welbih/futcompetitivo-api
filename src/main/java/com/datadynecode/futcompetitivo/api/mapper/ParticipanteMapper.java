package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.dto.ParticipanteResponse;
import com.datadynecode.futcompetitivo.api.entity.Participante;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ParticipanteMapper {

    private final JogadorMapper jogadorMapper;
    private final EquipeMapper equipeMapper;

    public ParticipanteResponse toResponse(Participante participante) {
        return new ParticipanteResponse(participante.getId(),
                jogadorMapper.toResponse(participante.getJogador()),
                equipeMapper.toResponse(participante.getEquipe()), participante.isAtivo());
    }
}
