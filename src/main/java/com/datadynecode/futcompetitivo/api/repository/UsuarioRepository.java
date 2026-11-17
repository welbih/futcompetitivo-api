package com.datadynecode.futcompetitivo.api.repository;

import java.util.Optional;

import com.datadynecode.futcompetitivo.api.entity.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "roles.roles")
    Optional<Usuario> findByEmail(String email);
}
