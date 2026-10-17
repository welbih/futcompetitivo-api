package com.datadynecode.futcompetitivo.api.service.classificacao;

import com.datadynecode.futcompetitivo.api.entity.ClassificacaoParticipante;

public interface CriterioDesempate {

    int comparar(ClassificacaoParticipante a, ClassificacaoParticipante b);

}
