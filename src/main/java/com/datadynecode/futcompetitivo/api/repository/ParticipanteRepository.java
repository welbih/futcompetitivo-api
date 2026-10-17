package com.datadynecode.futcompetitivo.api.repository;

import com.datadynecode.futcompetitivo.api.entity.Participante;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipanteRepository extends JpaRepository<Participante, Long> {
    List<Participante> findAllByAtivoTrueOrderByIdAsc();

    Optional<Participante> findByIdAndAtivoTrue(Long id);
}
