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
            var chave = fase.getConfrontos().stream().filter(c -> !c.isTerceiroLugar()).toList();
            for (Rodada rodada : fase.getRodadas()) {
                int quantidade = 1 << (fase.getTotalRodadas() - rodada.getNumero());
                int inicio = (1 << fase.getTotalRodadas()) - 2 * quantidade;
                EtapaResponse etapa = etapa(quantidade, preliminar && rodada.getNumero() == 1);
                boolean disputasFinais = quantidade == 1 && fase.isDisputaTerceiroLugar();
                rodadas.add(toResponse(rodada, disputasFinais
                        ? new EtapaResponse("DISPUTAS_FINAIS", "Final e disputa de terceiro lugar") : etapa));
                if (disputasFinais) fase.getConfrontos().stream().filter(Confronto::isTerceiroLugar)
                        .forEach(c -> confrontos.add(toResponse(c, new EtapaResponse("TERCEIRO_LUGAR", "Disputa de terceiro lugar"), true)));
                chave.subList(inicio, inicio + quantidade)
                        .forEach(c -> confrontos.add(toResponse(c, etapa, true)));
            }
            return new FaseResponse(fase.getId(), fase.getOrdem(), fase.getTipo(), rodadas, confrontos,
                    fase.getQuantidadeClassificados(), fase.getGrupos().stream().map(this::toResponse).toList(),
                    fase.getFormatoEliminatorio() == null ? FormatoEliminatorio.JOGO_UNICO : fase.getFormatoEliminatorio(),
                    fase.isDisputaTerceiroLugar());
        }
        return new FaseResponse(fase.getId(), fase.getOrdem(), fase.getTipo(),
                fase.getRodadas().stream().map(this::toResponse).toList(),
                fase.getConfrontos().stream().map(this::toResponse).toList(), fase.getQuantidadeClassificados(),
                fase.getGrupos().stream().map(this::toResponse).toList(), fase.getFormatoEliminatorio(), fase.isDisputaTerceiroLugar());
    }

    public GrupoResponse toResponse(Grupo grupo) {
        return new GrupoResponse(grupo.getId(), grupo.getNome(), grupo.getParticipantes().stream()
                .map(participanteMapper::toResponse).toList(), grupo.getClassificacaoPorRodada().stream()
                .map(this::toResponse).toList());
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
                        resultado.getPenaltisVisitante(), resultado.isGolDeOuro()), partida.getYoutubeUrl());
    }

    public ConfrontoResponse toResponse(Confronto confronto) {
        return toResponse(confronto, null, false);
    }

    private ConfrontoResponse toResponse(Confronto confronto, EtapaResponse etapa, boolean apenasReferencias) {
        return new ConfrontoResponse(confronto.getId(),
                confronto.getParticipanteUm() == null ? null : confronto.getParticipanteUm().getId(),
                confronto.getParticipanteDois() == null ? null : confronto.getParticipanteDois().getId(),
                confronto.getOrigemUm() == null ? null : confronto.getOrigemUm().getId(),
                confronto.getOrigemDois() == null ? null : confronto.getOrigemDois().getId(),
                confronto.getTipo(), apenasReferencias ? null : confronto.getPartidas().stream().map(this::toResponse).toList(),
                etapa, confronto.isTerceiroLugar(),
                apenasReferencias ? confronto.getPartidas().stream().map(Partida::getId).toList() : null);
    }

    private EtapaResponse etapa(int confrontos, boolean preliminar) {
        return EtapaEliminatoriaMapper.identificar(confrontos, preliminar);
    }

    public ClassificacaoResponse toResponse(Classificacao classificacao) {
        return new ClassificacaoResponse(classificacao.getRodada(), classificacao.getTabela().stream()
                .map(c -> new ClassificacaoParticipanteResponse(participanteMapper.toResponse(c.getParticipante()),
                        c.getPontos(), c.getJogos(), c.getVitorias(), c.getEmpates(), c.getDerrotas(),
                        c.getGolsPro(), c.getGolsContra(), c.getSaldoGols(), c.getPosicaoAtual(),
                        c.getPosicaoAnterior(), c.getVariacao(), c.getMovimentacao())).toList());
    }
}
