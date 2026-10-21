package com.datadynecode.futcompetitivo.api.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "x1")
@Getter
@Setter
@NoArgsConstructor
public class ConfrontoPessoal {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "confronto_pessoal_seq")
    @SequenceGenerator(name = "confronto_pessoal_seq", sequenceName = "confronto_pessoal_seq", allocationSize = 50)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Participante participanteUm;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(nullable = false)
    private Participante participanteDois;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoSeriePessoal tipo;
    @Column(nullable = false)
    private boolean encerrarAntecipadamente;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConfrontoPessoal status = StatusConfrontoPessoal.EM_ANDAMENTO;
    @OneToMany(mappedBy = "x1", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero ASC")
    private List<Partida> partidas = new ArrayList<>();

    public int vitorias(Participante participante) {
        return (int) partidas.stream().map(Partida::vencedor)
                .filter(v -> v != null && v.getId().equals(participante.getId())).count();
    }

    public Participante vencedor() {
        if (status != StatusConfrontoPessoal.FINALIZADO || vitorias(participanteUm) == vitorias(participanteDois)) return null;
        return vitorias(participanteUm) > vitorias(participanteDois) ? participanteUm : participanteDois;
    }
}

