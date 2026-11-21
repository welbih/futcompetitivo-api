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
public class Campeonato {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "campeonato_seq")
    @SequenceGenerator(name = "campeonato_seq", sequenceName = "campeonato_seq", allocationSize = 50)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizacao_id", nullable = false)
    private Organizacao organizacao;
    @Column(nullable = false)
    private Integer edicao;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampeonatoStatus status = CampeonatoStatus.CRIADO;

    @ManyToMany
    @JoinTable(name = "campeonato_participante",
            joinColumns = @JoinColumn(name = "campeonato_id"),
            inverseJoinColumns = @JoinColumn(name = "participante_id"))
    @OrderColumn(name = "ordem")
    private List<Participante> participantes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "campeonato_desistencia", joinColumns = @JoinColumn(name = "campeonato_id"))
    @Column(name = "participante_id", nullable = false)
    private java.util.Set<Long> participantesDesistentes = new java.util.HashSet<>();

    @ElementCollection
    @CollectionTable(name = "campeonato_substituicao", joinColumns = @JoinColumn(name = "campeonato_id"))
    @MapKeyColumn(name = "desistente_id")
    @Column(name = "substituto_id", nullable = false)
    private java.util.Map<Long, Long> substituicoesMataMata = new java.util.HashMap<>();

    @ElementCollection
    @CollectionTable(name = "campeonato_classificado_base", joinColumns = @JoinColumn(name = "campeonato_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "participante_id", nullable = false)
    private List<Long> classificadosBase = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "campeonato_id")
    @OrderBy("ordem ASC")
    private List<Fase> fases = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "campeonato_id")
    @OrderBy("rodada ASC")
    private List<Classificacao> classificacaoPorRodada = new ArrayList<>();
}
