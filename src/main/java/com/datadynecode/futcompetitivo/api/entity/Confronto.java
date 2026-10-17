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
public class Confronto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    private Participante participanteUm;
    @ManyToOne(fetch = FetchType.LAZY)
    private Participante participanteDois;
    @ManyToOne(fetch = FetchType.LAZY)
    private Confronto origemUm;
    @ManyToOne(fetch = FetchType.LAZY)
    private Confronto origemDois;
    @Enumerated(EnumType.STRING)
    private TipoConfronto tipo = TipoConfronto.JOGO_UNICO;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "confronto_id")
    @OrderColumn(name = "ordem")
    private List<Partida> partidas = new ArrayList<>();
}
