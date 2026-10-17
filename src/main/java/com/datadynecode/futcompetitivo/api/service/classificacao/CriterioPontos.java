package com.datadynecode.futcompetitivo.api.service.classificacao;

import com.datadynecode.futcompetitivo.api.entity.ClassificacaoParticipante;

public class CriterioPontos implements CriterioDesempate {

    @Override
    public int comparar(ClassificacaoParticipante a, ClassificacaoParticipante b) {
        return Integer.compare(b.getPontos(), a.getPontos());
    }

}
