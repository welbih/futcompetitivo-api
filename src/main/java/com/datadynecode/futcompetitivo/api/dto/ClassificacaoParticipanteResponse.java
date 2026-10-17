package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.MovimentacaoPosicao;

public record ClassificacaoParticipanteResponse(ParticipanteResponse participante,
        int pontos, int jogos, int vitorias, int empates, int derrotas,
        int golsPro, int golsContra, int saldoGols, int posicaoAtual,
        int posicaoAnterior, int variacao, MovimentacaoPosicao movimentacao) {
}
