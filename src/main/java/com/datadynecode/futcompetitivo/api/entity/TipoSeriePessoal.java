package com.datadynecode.futcompetitivo.api.entity;

public enum TipoSeriePessoal {
    MELHOR_DE_TRES(3),
    MELHOR_DE_CINCO(5);

    private final int quantidadePartidas;

    TipoSeriePessoal(int quantidadePartidas) { this.quantidadePartidas = quantidadePartidas; }

    public int getQuantidadePartidas() { return quantidadePartidas; }
    public int getVitoriasNecessarias() { return quantidadePartidas / 2 + 1; }
}
