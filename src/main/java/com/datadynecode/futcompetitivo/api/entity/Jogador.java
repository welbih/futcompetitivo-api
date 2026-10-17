package com.datadynecode.futcompetitivo.api.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Jogador {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "jogador_seq")
    @SequenceGenerator(name = "jogador_seq", sequenceName = "jogador_seq", allocationSize = 50)
    private Long id;

    private String nome;

    private String apelido;

    @Column(nullable = false)
    private boolean ativo = true;
}
