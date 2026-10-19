package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampeonatoMapper {
    private final ParticipanteMapper participanteMapper;
    private final OrganizacaoMapper organizacaoMapper;

    public CampeonatoResponse toResponse(Campeonato campeonato) {
        return new CampeonatoResponse(campeonato.getId(), organizacaoMapper.toResponse(campeonato.getOrganizacao()), campeonato.getEdicao(),
                campeonato.getStatus(), campeonato.getParticipantes().stream()
                        .map(participanteMapper::toResponse).toList(),
                campeonato.getFases().stream().map(this::toResponse).toList(),
                campeonato.getClassificacaoPorRodada().stream().map(this::toResponse).toList());
    }

    public FaseResponse toResponse(Fase fase) {
        if (fase.getTipo() == TipoFase.ELIMINATORIA) {
            boolean preliminar = fase.getConfrontos().stream()
                    .anyMatch(c -> c.getOrigemUm() == null && c.getParticipanteDois() == null);
            var rodadas = new ArrayList<RodadaResponse>();
            var confrontos = new ArrayList<ConfrontoResponse>();
            for (Rodada rodada : fase.getRodadas()) {
                int quantidade = 1 << (fase.getTotalRodadas() - rodada.getNumero());
                int inicio = (1 << fase.getTotalRodadas()) - 2 * quantidade;
                EtapaResponse etapa = etapa(quantidade, preliminar && rodada.getNumero() == 1);
                rodadas.add(toResponse(rodada, etapa));
                fase.getConfrontos().subList(inicio, inicio + quantidade)
                        .forEach(c -> confrontos.add(toResponse(c, etapa)));
            }
            return new FaseResponse(fase.getId(), fase.getOrdem(), fase.getTipo(), rodadas, confrontos);
        }
        return new FaseResponse(fase.getId(), fase.getOrdem(), fase.getTipo(),
                fase.getRodadas().stream().map(this::toResponse).toList(),
                fase.getConfrontos().stream().map(this::toResponse).toList());
    }

    public RodadaResponse toResponse(Rodada rodada) {
        return toResponse(rodada, null);
    }

    private RodadaResponse toResponse(Rodada rodada, EtapaResponse etapa) {
        return new RodadaResponse(rodada.getId(), rodada.getNumero(), rodada.getStatus(),
                rodada.getFolga() == null ? null : rodada.getFolga().getId(),
                rodada.getPartidas().stream().map(this::toResponse).toList(), etapa);
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
        return toResponse(confronto, null);
    }

    private ConfrontoResponse toResponse(Confronto confronto, EtapaResponse etapa) {
        return new ConfrontoResponse(confronto.getId(),
                confronto.getParticipanteUm() == null ? null : confronto.getParticipanteUm().getId(),
                confronto.getParticipanteDois() == null ? null : confronto.getParticipanteDois().getId(),
                confronto.getOrigemUm() == null ? null : confronto.getOrigemUm().getId(),
                confronto.getOrigemDois() == null ? null : confronto.getOrigemDois().getId(),
                confronto.getTipo(), confronto.getPartidas().stream().map(this::toResponse).toList(), etapa);
    }

    private EtapaResponse etapa(int confrontos, boolean preliminar) {
        if (preliminar) return new EtapaResponse("PRELIMINAR", "Fase preliminar");
        return switch (confrontos) {
            case 1 -> new EtapaResponse("FINAL", "Final");
            case 2 -> new EtapaResponse("SEMIFINAL", "Semifinal");
            case 4 -> new EtapaResponse("QUARTAS_DE_FINAL", "Quartas de final");
            case 8 -> new EtapaResponse("OITAVAS_DE_FINAL", "Oitavas de final");
            case 16 -> new EtapaResponse("DEZESSEIS_AVOS_DE_FINAL", "Dezesseis avos de final");
            case 32 -> new EtapaResponse("TRINTA_E_DOIS_AVOS_DE_FINAL", "Trinta e dois avos de final");
            default -> new EtapaResponse("ELIMINATORIA_" + (2 * confrontos),
                    "Eliminatória de " + (2 * confrontos) + " participantes");
        };
    }

    public ClassificacaoResponse toResponse(Classificacao classificacao) {
        return new ClassificacaoResponse(classificacao.getRodada(), classificacao.getTabela().stream()
                .map(c -> new ClassificacaoParticipanteResponse(participanteMapper.toResponse(c.getParticipante()),
                        c.getPontos(), c.getJogos(), c.getVitorias(), c.getEmpates(), c.getDerrotas(),
                        c.getGolsPro(), c.getGolsContra(), c.getSaldoGols(), c.getPosicaoAtual(),
                        c.getPosicaoAnterior(), c.getVariacao(), c.getMovimentacao())).toList());
    }
}
