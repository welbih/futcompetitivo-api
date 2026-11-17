package com.datadynecode.futcompetitivo.api.dto;

import java.util.Set;

public record UsuarioAdministrativoResponse(Long id, String nome, String sobrenome, String email,
    Set<String> roles, boolean ativo) {}
