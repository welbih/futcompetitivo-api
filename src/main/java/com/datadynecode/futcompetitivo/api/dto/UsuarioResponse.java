package com.datadynecode.futcompetitivo.api.dto;

import java.util.Set;

public record UsuarioResponse(Long id, String nome, String sobrenome, String email, Set<String> roles) {}
