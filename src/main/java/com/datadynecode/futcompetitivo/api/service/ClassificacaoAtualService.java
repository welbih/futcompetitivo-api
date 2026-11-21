package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.entity.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClassificacaoAtualService {
    private final GeradorClassificacaoService gerador;

    public Classificacao atual(Campeonato campeonato) {
        if(campeonato.getFases().isEmpty()) return null;
        Fase fase=campeonato.getFases().getFirst();
        if(fase.getTipo()!=TipoFase.LIGA && fase.getTipo()!=TipoFase.SWISS) return null;
        return atual(fase,campeonato.getParticipantes(),campeonato.getClassificacaoPorRodada());
    }

    public Classificacao atual(Fase fase,Grupo grupo) {
        return atual(fase,grupo.getParticipantes(),grupo.getClassificacaoPorRodada());
    }

    private Classificacao atual(Fase fase,List<Participante> participantes,List<Classificacao> historico) {
        var ativa=fase.getRodadas().stream().filter(r -> r.getStatus()==RodadaStatus.EM_ANDAMENTO)
                .min(Comparator.comparingInt(Rodada::getNumero)).orElse(null);
        if(ativa==null) return historico.stream().max(Comparator.comparingInt(Classificacao::getRodada)).orElse(null);
        Set<Long> membros=new HashSet<>(participantes.stream().map(Participante::getId).toList());
        var partidas=fase.getRodadas().stream()
                .filter(r -> r.getNumero()<=ativa.getNumero() && r.getStatus()!=RodadaStatus.PENDENTE)
                .flatMap(r -> r.getPartidas().stream()).filter(Partida::isFinalizada)
                .filter(p -> membros.contains(p.getMandante().getId()) && membros.contains(p.getVisitante().getId())).toList();
        var anterior=historico.stream().filter(c -> c.getRodada()<ativa.getNumero())
                .max(Comparator.comparingInt(Classificacao::getRodada)).orElse(null);
        var parcial=gerador.gerarComSorteioInicial(participantes,partidas,ativa.getNumero(),anterior);
        parcial.setParcial(true);
        return parcial;
    }
}
