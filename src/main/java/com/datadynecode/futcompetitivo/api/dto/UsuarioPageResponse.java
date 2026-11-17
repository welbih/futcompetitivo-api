package com.datadynecode.futcompetitivo.api.dto;

import java.util.List;

public record UsuarioPageResponse(List<UsuarioAdministrativoResponse> content, int page, int size,
    long totalElements, int totalPages) {}
