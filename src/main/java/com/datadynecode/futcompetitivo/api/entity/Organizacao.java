package com.datadynecode.futcompetitivo.api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Organizacao {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "organizacao_seq")
    @SequenceGenerator(name = "organizacao_seq", sequenceName = "organizacao_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;
}
