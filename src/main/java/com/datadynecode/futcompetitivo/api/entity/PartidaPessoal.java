package com.datadynecode.futcompetitivo.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_partida_pessoal_numero", columnNames = {"confronto_id", "numero"}))
@Getter
@Setter
@NoArgsConstructor
public class PartidaPessoal {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "partida_pessoal_seq")
    @SequenceGenerator(name = "partida_pessoal_seq", sequenceName = "partida_pessoal_seq", allocationSize = 50)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private ConfrontoPessoal confronto;
    @Column(nullable = false)
    private int numero;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Participante mandante;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Participante visitante;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPartidaPessoal status = StatusPartidaPessoal.AGENDADA;
    @Embedded
    private Resultado resultado;

    public Participante vencedor() {
        if (status != StatusPartidaPessoal.FINALIZADA || resultado == null) return null;
        if (!resultado.getGolsMandante().equals(resultado.getGolsVisitante()))
            return resultado.getGolsMandante() > resultado.getGolsVisitante() ? mandante : visitante;
        if (resultado.getPenaltisMandante() == null || resultado.getPenaltisVisitante() == null
                || resultado.getPenaltisMandante().equals(resultado.getPenaltisVisitante())) return null;
        return resultado.getPenaltisMandante() > resultado.getPenaltisVisitante() ? mandante : visitante;
    }
}

