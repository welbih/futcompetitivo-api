package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.dto.OrganizacaoRequest;
import com.datadynecode.futcompetitivo.api.dto.OrganizacaoResponse;
import com.datadynecode.futcompetitivo.api.entity.Organizacao;
import org.springframework.stereotype.Component;

@Component
public class OrganizacaoMapper {
    public void atualizar(Organizacao organizacao, OrganizacaoRequest request) {
        organizacao.setNome(request.nome());
        organizacao.setAtivo(request.ativo());
    }

    public OrganizacaoResponse toResponse(Organizacao organizacao) {
        return new OrganizacaoResponse(organizacao.getId(), organizacao.getNome(), organizacao.isAtivo());
    }
}
