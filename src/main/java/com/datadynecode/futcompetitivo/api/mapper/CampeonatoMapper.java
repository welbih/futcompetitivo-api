package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampeonatoMapper {
    private final ParticipanteMapper participanteMapper;

    public CampeonatoResponse toResponse(Campeonato campeonato) {
        return new CampeonatoResponse(campeonato.getId(), campeonato.getNome(), campeonato.getEdicao(),
                campeonato.getStatus(), campeonato.getParticipantes().stream()
                        .map(participanteMapper::toResponse).toList(),
                campeonato.getFases().stream().map(this::toResponse).toList(),
                campeonato.getClassificacaoPorRodada().stream().map(this::toResponse).toList());
    }

    public FaseResponse toResponse(Fase fase) {
        return new FaseResponse(fase.getId(), fase.getOrdem(), fase.getTipo(),
                fase.getRodadas().stream().map(this::toResponse).toList(),
                fase.getConfrontos().stream().map(this::toResponse).toList());
    }

    public RodadaResponse toResponse(Rodada rodada) {
        return new RodadaResponse(rodada.getId(), rodada.getNumero(), rodada.getStatus(),
                rodada.getFolga() == null ? null : rodada.getFolga().getId(),
                rodada.getPartidas().stream().map(this::toResponse).toList());
    }

    public PartidaResponse toResponse(Partida partida) {
        Resultado resultado = partida.getResultado();
        return new PartidaResponse(partida.getId(), participanteMapper.toResponse(partida.getMandante()),
                participanteMapper.toResponse(partida.getVisitante()), partida.getStatus(),
                resultado == null ? null : new ResultadoRequest(resultado.getGolsMandante(),
                        resultado.getGolsVisitante(), resultado.getPenaltisMandante(),
                        resultado.getPenaltisVisitante(), resultado.isGolDeOuro()));
    }

    public ConfrontoResponse toResponse(Confronto confronto) {
        return new ConfrontoResponse(confronto.getId(),
                confronto.getParticipanteUm() == null ? null : confronto.getParticipanteUm().getId(),
                confronto.getParticipanteDois() == null ? null : confronto.getParticipanteDois().getId(),
                confronto.getOrigemUm() == null ? null : confronto.getOrigemUm().getId(),
                confronto.getOrigemDois() == null ? null : confronto.getOrigemDois().getId(),
                confronto.getTipo(), confronto.getPartidas().stream().map(this::toResponse).toList());
    }

    public ClassificacaoResponse toResponse(Classificacao classificacao) {
        return new ClassificacaoResponse(classificacao.getRodada(), classificacao.getTabela().stream()
                .map(c -> new ClassificacaoParticipanteResponse(participanteMapper.toResponse(c.getParticipante()),
                        c.getPontos(), c.getJogos(), c.getVitorias(), c.getEmpates(), c.getDerrotas(),
                        c.getGolsPro(), c.getGolsContra(), c.getSaldoGols(), c.getPosicaoAtual(),
                        c.getPosicaoAnterior(), c.getVariacao(), c.getMovimentacao())).toList());
    }
}
