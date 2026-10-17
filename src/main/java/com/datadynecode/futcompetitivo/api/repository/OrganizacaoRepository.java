package com.datadynecode.futcompetitivo.api.repository;

import com.datadynecode.futcompetitivo.api.entity.Organizacao;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizacaoRepository extends JpaRepository<Organizacao, Long> {
    Optional<Organizacao> findByIdAndAtivoTrue(Long id);
}
