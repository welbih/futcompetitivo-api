package com.datadynecode.futcompetitivo.api.entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

@Embeddable
@Access(AccessType.FIELD)
public class Roles implements Iterable<Role> {

    @ManyToMany
    @JoinTable(
        name = "usuarios_roles",
        joinColumns = @JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    public boolean adicionar(Role role) {
        return roles.add(Objects.requireNonNull(role, "Role nao pode ser nula"));
    }

    public boolean remover(Role role) {
        return roles.remove(role);
    }

    public boolean contem(Role role) {
        return roles.contains(role);
    }

    public boolean contemPorNome(String nome) {
        return nome != null && roles.stream()
            .anyMatch(role -> nome.equals(role.getAuthority()));
    }

    public int tamanho() {
        return roles.size();
    }

    public Set<Role> comoColecao() {
        return Set.copyOf(roles);
    }

    @Override
    public Iterator<Role> iterator() {
        return Collections.unmodifiableSet(roles).iterator();
    }
}
