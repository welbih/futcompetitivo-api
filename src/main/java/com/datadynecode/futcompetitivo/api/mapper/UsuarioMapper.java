package com.datadynecode.futcompetitivo.api.mapper;
import com.datadynecode.futcompetitivo.api.dto.UsuarioAdministrativoResponse;

import java.util.stream.Collectors;

import com.datadynecode.futcompetitivo.api.dto.UsuarioResponse;
import com.datadynecode.futcompetitivo.api.entity.Role;
import com.datadynecode.futcompetitivo.api.entity.Usuario;

public final class UsuarioMapper {
    private UsuarioMapper() {}

    public static UsuarioAdministrativoResponse toAdministrativoResponse(Usuario usuario) {
        UsuarioResponse resposta = toResponse(usuario);
        return new UsuarioAdministrativoResponse(resposta.id(), resposta.nome(),
            resposta.sobrenome(), resposta.email(), resposta.roles(), usuario.isAtivo());
    }

    public static UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getSobrenome(), usuario.getEmail(),
            usuario.getRoles().comoColecao().stream().map(Role::getAuthority).collect(Collectors.toUnmodifiableSet()));
    }
}
