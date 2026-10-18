package com.datadynecode.futcompetitivo.api.repository;

import com.datadynecode.futcompetitivo.api.entity.Equipe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipeRepository extends JpaRepository<Equipe, Long> {
    List<Equipe> findAllByAtivoTrueOrderByIdAsc();

    List<Equipe> findAllByAtivoOrderByIdAsc(boolean ativo);

    List<Equipe> findAllByOrderByIdAsc();

    Optional<Equipe> findByIdAndAtivoTrue(Long id);
}
