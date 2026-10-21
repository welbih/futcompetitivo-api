package com.datadynecode.futcompetitivo.api.repository;

import com.datadynecode.futcompetitivo.api.entity.ConfrontoPessoal;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfrontoPessoalRepository extends JpaRepository<ConfrontoPessoal, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConfrontoPessoal c where c.id = :id")
    Optional<ConfrontoPessoal> buscarParaAtualizar(@Param("id") Long id);
}
