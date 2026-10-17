package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.dto.JogadorRequest;
import com.datadynecode.futcompetitivo.api.dto.JogadorResponse;
import com.datadynecode.futcompetitivo.api.entity.Jogador;
import org.springframework.stereotype.Component;

@Component
public class JogadorMapper {

    public void atualizar(Jogador jogador, JogadorRequest request) {
        jogador.setNome(request.nome());
        jogador.setApelido(request.apelido());
    }

    public JogadorResponse toResponse(Jogador jogador) {
        return new JogadorResponse(jogador.getId(), jogador.getNome(),
                jogador.getApelido(), jogador.isAtivo());
    }
}
