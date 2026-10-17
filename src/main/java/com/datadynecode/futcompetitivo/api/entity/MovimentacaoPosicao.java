package com.datadynecode.futcompetitivo.api.entity;

import lombok.Getter;

@Getter
public enum MovimentacaoPosicao {
    SUBIU("↑"), DESCEU("↓"), MANTEVE("-");
    private final String simbolo;
    MovimentacaoPosicao(String simbolo) { this.simbolo = simbolo; }
}
