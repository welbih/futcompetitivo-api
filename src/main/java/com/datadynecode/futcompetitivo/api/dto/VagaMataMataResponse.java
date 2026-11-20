package com.datadynecode.futcompetitivo.api.dto;

import java.util.List;

public record VagaMataMataResponse(Long desistenteId, Long grupoId, List<ParticipanteResponse> candidatos) {}
