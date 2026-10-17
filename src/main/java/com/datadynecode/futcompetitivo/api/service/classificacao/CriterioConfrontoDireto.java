package com.datadynecode.futcompetitivo.api.service.classificacao;

import com.datadynecode.futcompetitivo.api.entity.ClassificacaoParticipante;
import com.datadynecode.futcompetitivo.api.entity.Partida;
import com.datadynecode.futcompetitivo.api.entity.Resultado;

import java.util.List;

public class CriterioConfrontoDireto implements CriterioDesempate {

    private List<Partida> partidas;

    public CriterioConfrontoDireto(List<Partida> partidas) {
        this.partidas = partidas;
    }

    @Override
    public int comparar(ClassificacaoParticipante a, ClassificacaoParticipante b) {

        int pontosA = 0;
        int pontosB = 0;

        for (Partida partida : partidas) {

            if (!partida.isFinalizada()) continue;

            boolean envolve =
                    (partida.getMandante().equals(a.getParticipante()) &&
                            partida.getVisitante().equals(b.getParticipante()))
                            || (partida.getMandante().equals(b.getParticipante()) &&
                            partida.getVisitante().equals(a.getParticipante()));

            if (!envolve) continue;

            Resultado r = partida.getResultado();

            if (partida.getMandante().equals(a.getParticipante())) {

                pontosA += calcularPontos(
                        r.getGolsMandante(),
                        r.getGolsVisitante());

                pontosB += calcularPontos(
                        r.getGolsVisitante(),
                        r.getGolsMandante());

            } else {

                pontosA += calcularPontos(
                        r.getGolsVisitante(),
                        r.getGolsMandante());

                pontosB += calcularPontos(
                        r.getGolsMandante(),
                        r.getGolsVisitante());
            }
        }

        return Integer.compare(pontosB, pontosA);
    }

    private int calcularPontos(int golsPro, int golsContra) {

        if (golsPro > golsContra) return 3;
        if (golsPro == golsContra) return 1;
        return 0;
    }

}
