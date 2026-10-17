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
public class Fase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer ordem;
    @Enumerated(EnumType.STRING)
    private TipoFase tipo;
    private boolean turnoEreturno;
    private Integer jogosPorParticipante;
    private Integer totalRodadas;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fase_id")
    @OrderBy("numero ASC")
    private List<Rodada> rodadas = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "fase_id")
    @OrderColumn(name = "ordem")
    private List<Confronto> confrontos = new ArrayList<>();
}
