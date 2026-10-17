package com.datadynecode.futcompetitivo.api.simulacao;

import com.datadynecode.futcompetitivo.api.simulacao.suporte.CenarioSimulacao;
import com.datadynecode.futcompetitivo.api.simulacao.suporte.SimuladorCampeonato;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("test")
@Import(SimuladorCampeonato.class)
@Transactional
class CampeonatoSimulacaoTests {
    @Autowired private SimuladorCampeonato simulador;

    @ParameterizedTest(name = "{0}")
    @EnumSource(CenarioSimulacao.class)
    void simulaCampeonatoCompletoExibindoAgendaResultadosEClassificacao(CenarioSimulacao cenario) {
        simulador.executar(cenario);
    }
}
