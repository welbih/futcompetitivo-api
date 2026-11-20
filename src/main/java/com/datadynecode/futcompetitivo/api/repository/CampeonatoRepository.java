package com.datadynecode.futcompetitivo.api.repository;

import com.datadynecode.futcompetitivo.api.entity.Campeonato;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampeonatoRepository extends JpaRepository<Campeonato, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Campeonato c where c.id = :id")
    java.util.Optional<Campeonato> buscarParaAtualizar(@org.springframework.data.repository.query.Param("id") Long id);
}
