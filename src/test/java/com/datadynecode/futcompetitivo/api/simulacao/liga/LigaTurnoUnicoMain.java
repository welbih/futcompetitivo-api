package com.datadynecode.futcompetitivo.api.simulacao.liga;

import com.datadynecode.futcompetitivo.api.simulacao.suporte.CenarioSimulacao;
import com.datadynecode.futcompetitivo.api.simulacao.suporte.ExecutorSimulacaoMain;

public class LigaTurnoUnicoMain {
    public static void main(String[] args) {
        ExecutorSimulacaoMain.executar(CenarioSimulacao.LIGA_TURNO_UNICO);
    }
}
