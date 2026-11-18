package com.datadynecode.futcompetitivo.api.dto;

public record OrganizacaoResponse(Long id, String nome, boolean ativo, String trofeuUrl) {
    public OrganizacaoResponse(Long id, String nome, boolean ativo) {
        this(id, nome, ativo, null);
    }
}
