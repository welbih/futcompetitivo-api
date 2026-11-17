package com.datadynecode.futcompetitivo.api.entity;

import java.util.Collection;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "usuarios_seq_generator")
    @SequenceGenerator(name = "usuarios_seq_generator", sequenceName = "usuarios_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Column(nullable = false, length = 100)
    private String sobrenome;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    // Armazena somente o hash da senha, gerado pela camada de service.
    @Column(nullable = false, length = 255)
    private String senha;

    @Column(nullable = false)
    private boolean ativo = true;

    @Override
    public boolean isEnabled() {
        return ativo;
    }

    @Embedded
    @Setter(AccessLevel.NONE)
    private Roles roles = new Roles();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.comoColecao();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return senha;
    }
}
