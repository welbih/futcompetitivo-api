package com.datadynecode.futcompetitivo.api.repository;

import com.datadynecode.futcompetitivo.api.entity.Jogador;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JogadorRepository extends JpaRepository<Jogador, Long> {
    List<Jogador> findAllByAtivoTrueOrderByIdAsc();

    List<Jogador> findAllByAtivoOrderByIdAsc(boolean ativo);

    List<Jogador> findAllByOrderByIdAsc();

    Optional<Jogador> findByIdAndAtivoTrue(Long id);
}
