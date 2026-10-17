package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.dto.EquipeRequest;
import com.datadynecode.futcompetitivo.api.dto.EquipeResponse;
import com.datadynecode.futcompetitivo.api.entity.Equipe;
import org.springframework.stereotype.Component;

@Component
public class EquipeMapper {

    public void atualizar(Equipe equipe, EquipeRequest request) {
        equipe.setNome(request.nome());
        equipe.setLogoUrl(request.logoUrl());
    }

    public EquipeResponse toResponse(Equipe equipe) {
        return new EquipeResponse(equipe.getId(), equipe.getNome(),
                equipe.getLogoUrl(), equipe.isAtivo());
    }
}
