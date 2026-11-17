package com.datadynecode.futcompetitivo.api.repository;

import java.util.Optional;

import com.datadynecode.futcompetitivo.api.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByAuthority(String authority);
}
