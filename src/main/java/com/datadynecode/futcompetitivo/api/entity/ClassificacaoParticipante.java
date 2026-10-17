package com.datadynecode.futcompetitivo.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ClassificacaoParticipante {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "classificacao_participante_seq")
    @SequenceGenerator(name = "classificacao_participante_seq", sequenceName = "classificacao_participante_seq", allocationSize = 50)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Participante participante;
    private int pontos;
    private int jogos;
    private int vitorias;
    private int empates;
    private int derrotas;
    private int golsPro;
    private int golsContra;
    private int saldoGols;
    private int posicaoAtual;
    private int posicaoAnterior;
    private int variacao;
    @Enumerated(EnumType.STRING)
    private MovimentacaoPosicao movimentacao = MovimentacaoPosicao.MANTEVE;

    public ClassificacaoParticipante(Participante participante) {
        this.participante = participante;
    }
}
