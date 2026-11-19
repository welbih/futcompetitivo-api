package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.TipoFase;
import java.time.OffsetDateTime;
import java.util.List;

public record JogadorEstatisticasResponse(
        JogadorResponse jogador, OrigemPartida origem, Resumo resumo,
        List<Goleada> maioresGoleadasAplicadasPorGols,
        List<Goleada> maioresGoleadasAplicadasPorSaldo,
        List<Goleada> maioresGoleadasSofridasPorGols,
        List<Goleada> maioresGoleadasSofridasPorSaldo,
        List<Adversario> maioresFregueses, List<Adversario> maioresCarrascos) {

    public record Resumo(long partidasDisputadas, long vitorias, long empates, long derrotas,
            long golsMarcados, long golsSofridos, long saldoGols) {}

    public record Goleada(Long partidaId, JogadorResponse adversario,
            EquipeResponse equipeJogador, EquipeResponse equipeAdversario,
            int golsMarcados, int golsSofridos, long saldoGols, Penaltis penaltis,
            OrigemPartida origem, Competicao competicao, Rodada rodada, Fase fase,
            String idaOuVolta, Integer numeroPartida, OffsetDateTime dataPartida) {}

    public record Penaltis(Integer jogador, Integer adversario) {}

    public record Competicao(Long id, String nome, Organizacao organizacao, Integer edicao) {}
    public record Organizacao(Long id, String nome) {}
    public record Rodada(Long id, Integer numero) {}
    public record Fase(Long id, Integer ordem, TipoFase tipo, EtapaResponse etapa) {}

    public record Adversario(JogadorResponse adversario, long totalConfrontos,
            long vitorias, long empates, long derrotas) {}
}
