package com.datadynecode.futcompetitivo.api.service.classificacao;

import com.datadynecode.futcompetitivo.api.entity.ClassificacaoParticipante;

import java.util.Comparator;
import java.util.List;

public class ComparadorClassificacao implements Comparator<ClassificacaoParticipante> {

    private List<CriterioDesempate> criterios;

    public ComparadorClassificacao(List<CriterioDesempate> criterios) {
        this.criterios = criterios;
    }

    @Override
    public int compare(ClassificacaoParticipante a, ClassificacaoParticipante b) {

        for (CriterioDesempate criterio : criterios) {
            int resultado = criterio.comparar(a, b);
            if (resultado != 0) {
                return resultado;
            }
        }

        return 0;
    }

}
