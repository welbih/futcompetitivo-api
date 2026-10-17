package com.datadynecode.futcompetitivo.api.simulacao.suporte;

import com.datadynecode.futcompetitivo.api.entity.TipoFormato;

public enum CenarioSimulacao {
    LIGA_TURNO_UNICO("Liga - turno unico", 7, TipoFormato.LIGA, false, null, null, 21, 6),
    LIGA_TURNO_RETURNO("Liga - turno e returno", 7, TipoFormato.LIGA, true, null, null, 42, 12),
    LIGA_PARCIAL_IMPAR("Liga parcial - quatro jogos por participante", 7, TipoFormato.LIGA_PARCIAL, false, 4, null, 14, 4),
    LIGA_PARCIAL_PAR("Liga parcial - participantes pares", 6, TipoFormato.LIGA_PARCIAL, false, 2, null, 6, 2),
    SWISS_COM_FOLGAS("Swiss - com folgas", 7, TipoFormato.SWISS, false, null, 3, 9, null),
    SWISS_COM_REVANCHES("Swiss - com revanches e inversao de mando", 4, TipoFormato.SWISS, false, null, 5, 10, 5);

    final String nome;
    final int quantidade;
    final TipoFormato formato;
    final boolean returno;
    final Integer jogosPorParticipante;
    final Integer totalRodadas;
    final int totalPartidasEsperado;
    final Integer jogosEsperadosPorParticipante;

    CenarioSimulacao(String nome, int quantidade, TipoFormato formato, boolean returno,
            Integer jogosPorParticipante, Integer totalRodadas, int totalPartidasEsperado,
            Integer jogosEsperadosPorParticipante) {
        this.nome = nome;
        this.quantidade = quantidade;
        this.formato = formato;
        this.returno = returno;
        this.jogosPorParticipante = jogosPorParticipante;
        this.totalRodadas = totalRodadas;
        this.totalPartidasEsperado = totalPartidasEsperado;
        this.jogosEsperadosPorParticipante = jogosEsperadosPorParticipante;
    }

}
