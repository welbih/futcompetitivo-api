package com.datadynecode.futcompetitivo.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_partida_x1_numero", columnNames = {"x1_id", "numero"}))
@Getter
@Setter
@NoArgsConstructor
public class Partida {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "partida_seq")
    @SequenceGenerator(name = "partida_seq", sequenceName = "partida_seq", allocationSize = 50)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "x1_id")
    private ConfrontoPessoal x1;
    private Integer numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Participante mandante;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Participante visitante;
    @Embedded
    private Resultado resultado;
    @Enumerated(EnumType.STRING)
    private StatusPartida status = StatusPartida.AGENDADA;

    public Partida(Participante mandante, Participante visitante) {
        this.mandante = mandante;
        this.visitante = visitante;
    }

    public boolean isFinalizada() {
        return status == StatusPartida.FINALIZADA && resultado != null;
    }

    public Participante vencedor() {
        if (!isFinalizada()) return null;
        if (!resultado.getGolsMandante().equals(resultado.getGolsVisitante()))
            return resultado.getGolsMandante() > resultado.getGolsVisitante() ? mandante : visitante;
        if (resultado.getPenaltisMandante() == null || resultado.getPenaltisVisitante() == null
                || resultado.getPenaltisMandante().equals(resultado.getPenaltisVisitante())) return null;
        return resultado.getPenaltisMandante() > resultado.getPenaltisVisitante() ? mandante : visitante;
    }
}
