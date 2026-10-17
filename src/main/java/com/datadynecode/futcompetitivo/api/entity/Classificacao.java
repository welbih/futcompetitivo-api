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
public class Classificacao {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "classificacao_seq")
    @SequenceGenerator(name = "classificacao_seq", sequenceName = "classificacao_seq", allocationSize = 50)
    private Long id;
    private int rodada;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "classificacao_id")
    @OrderBy("posicaoAtual ASC")
    private List<ClassificacaoParticipante> tabela = new ArrayList<>();
}
