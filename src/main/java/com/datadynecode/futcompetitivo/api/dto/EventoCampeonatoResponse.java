package com.datadynecode.futcompetitivo.api.dto;

import com.datadynecode.futcompetitivo.api.entity.MotivoAtualizacaoCampeonato;
import java.time.Instant;

public record EventoCampeonatoResponse(Long campeonatoId, MotivoAtualizacaoCampeonato motivo,
        @com.fasterxml.jackson.annotation.JsonFormat(shape=com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)
        Instant ocorridoEm, Long rodadaId, Long partidaId) {}
