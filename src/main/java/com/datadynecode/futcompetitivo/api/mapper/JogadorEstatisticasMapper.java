package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.dto.JogadorEstatisticasResponse.*;
import com.datadynecode.futcompetitivo.api.entity.TipoFase;
import com.datadynecode.futcompetitivo.api.repository.PartidaEstatisticasProjection;
import org.springframework.stereotype.Component;

@Component
public class JogadorEstatisticasMapper {
    public Goleada toResponse(PartidaEstatisticasProjection p, Long jogadorId) {
        boolean mandante = jogadorId.equals(p.getMandanteJogadorId());
        var adversario = mandante
                ? new JogadorResponse(p.getVisitanteJogadorId(), p.getVisitanteNome(), p.getVisitanteApelido(), p.getVisitanteAtivo())
                : new JogadorResponse(p.getMandanteJogadorId(), p.getMandanteNome(), p.getMandanteApelido(), p.getMandanteAtivo());
        var equipeMandante = new EquipeResponse(p.getMandanteEquipeId(), p.getMandanteEquipeNome(), p.getMandanteEquipeLogo(), p.getMandanteEquipeAtiva());
        var equipeVisitante = new EquipeResponse(p.getVisitanteEquipeId(), p.getVisitanteEquipeNome(), p.getVisitanteEquipeLogo(), p.getVisitanteEquipeAtiva());
        int marcados = mandante ? p.getGolsMandante() : p.getGolsVisitante();
        int sofridos = mandante ? p.getGolsVisitante() : p.getGolsMandante();
        Penaltis penaltis = p.getPenaltisMandante() == null && p.getPenaltisVisitante() == null ? null
                : new Penaltis(mandante ? p.getPenaltisMandante() : p.getPenaltisVisitante(),
                        mandante ? p.getPenaltisVisitante() : p.getPenaltisMandante());
        OrigemPartida origem = p.getX1Id() == null ? OrigemPartida.CAMPEONATO : OrigemPartida.X1;
        var competicao = origem == OrigemPartida.X1 ? new Competicao(p.getX1Id(), "X1 #" + p.getX1Id(), null, null)
                : new Competicao(p.getCampeonatoId(), p.getOrganizacaoNome() + " " + p.getEdicao(),
                        new Organizacao(p.getOrganizacaoId(), p.getOrganizacaoNome()), p.getEdicao());
        Rodada rodada = p.getRodadaId() == null ? null : new Rodada(p.getRodadaId(), p.getRodadaNumero());
        Fase fase = p.getFaseId() == null ? null : new Fase(p.getFaseId(), p.getFaseOrdem(),
                TipoFase.valueOf(p.getFaseTipo()), etapa(p));
        String idaOuVolta = null;
        if ("IDA_E_VOLTA".equals(p.getConfrontoTipo())) {
            idaOuVolta = p.getPartidaId().equals(p.getPrimeiraPartidaId()) ? "IDA" : "VOLTA";
        } else if ("JOGO_UNICO".equals(p.getConfrontoTipo())) {
            idaOuVolta = "JOGO_UNICO";
        }
        return new Goleada(p.getPartidaId(), adversario, mandante ? equipeMandante : equipeVisitante,
                mandante ? equipeVisitante : equipeMandante, marcados, sofridos, (long) marcados - sofridos,
                penaltis, origem, competicao, rodada, fase, idaOuVolta, p.getNumeroPartida(), null);
    }

    private EtapaResponse etapa(PartidaEstatisticasProjection p) {
        if (!TipoFase.ELIMINATORIA.name().equals(p.getFaseTipo())) return null;
        if (Boolean.TRUE.equals(p.getTerceiroLugar())) return new EtapaResponse("TERCEIRO_LUGAR", "Disputa de terceiro lugar");
        return EtapaEliminatoriaMapper.identificar(1 << (p.getTotalRodadas() - p.getRodadaNumero()),
                Boolean.TRUE.equals(p.getPreliminar()) && p.getRodadaNumero() == 1);
    }
}
