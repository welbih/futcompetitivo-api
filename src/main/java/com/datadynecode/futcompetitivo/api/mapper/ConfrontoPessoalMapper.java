package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConfrontoPessoalMapper {
    private final ParticipanteMapper participantes;

    public ConfrontoPessoalResponse toResponse(ConfrontoPessoal confronto) {
        Participante vencedor = confronto.vencedor();
        return new ConfrontoPessoalResponse(confronto.getId(), participantes.toResponse(confronto.getParticipanteUm()),
                participantes.toResponse(confronto.getParticipanteDois()), confronto.getTipo(),
                confronto.isEncerrarAntecipadamente(), confronto.getStatus(),
                confronto.vitorias(confronto.getParticipanteUm()), confronto.vitorias(confronto.getParticipanteDois()),
                vencedor == null ? null : participantes.toResponse(vencedor),
                confronto.getPartidas().stream().map(this::toResponse).toList());
    }

    private PartidaPessoalResponse toResponse(PartidaPessoal partida) {
        Resultado r = partida.getResultado();
        return new PartidaPessoalResponse(partida.getId(), partida.getNumero(), participantes.toResponse(partida.getMandante()),
                participantes.toResponse(partida.getVisitante()), partida.getStatus(), r == null ? null :
                new ResultadoRequest(r.getGolsMandante(), r.getGolsVisitante(), r.getPenaltisMandante(),
                        r.getPenaltisVisitante(), r.isGolDeOuro()));
    }
}
