package com.datadynecode.futcompetitivo.api.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Rodada {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "rodada_seq")
    @SequenceGenerator(name = "rodada_seq", sequenceName = "rodada_seq", allocationSize = 50)
    private Long id;
    private Integer numero;
    @Enumerated(EnumType.STRING)
    private RodadaStatus status = RodadaStatus.PENDENTE;
    @ManyToOne(fetch = FetchType.LAZY)
    private Participante folga;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "rodada_id")
    @OrderColumn(name = "ordem")
    private List<Partida> partidas = new ArrayList<>();

    public Rodada(Integer numero) { this.numero = numero; }
}
