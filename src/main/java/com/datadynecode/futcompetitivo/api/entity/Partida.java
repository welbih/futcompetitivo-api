package com.datadynecode.futcompetitivo.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Partida {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
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
}
