package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.service.ConfrontoService;
import com.datadynecode.futcompetitivo.api.service.GeradorEliminatorioService;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EliminatorioEtapasTests {
    private final CampeonatoMapper mapper = new CampeonatoMapper(
            mock(ParticipanteMapper.class), mock(OrganizacaoMapper.class));
    private final GeradorEliminatorioService gerador = new GeradorEliminatorioService(new ConfrontoService());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "4|Semifinal/Final",
            "5|Fase preliminar/Semifinal/Final",
            "7|Fase preliminar/Semifinal/Final",
            "8|Quartas de final/Semifinal/Final",
            "9|Fase preliminar/Quartas de final/Semifinal/Final",
            "16|Oitavas de final/Quartas de final/Semifinal/Final",
            "17|Fase preliminar/Oitavas de final/Quartas de final/Semifinal/Final",
            "32|Dezesseis avos de final/Oitavas de final/Quartas de final/Semifinal/Final",
            "64|Trinta e dois avos de final/Dezesseis avos de final/Oitavas de final/Quartas de final/Semifinal/Final",
            "128|Eliminatória de 128 participantes/Trinta e dois avos de final/Dezesseis avos de final/Oitavas de final/Quartas de final/Semifinal/Final"
    })
    void identificaEtapasDeTodaAChaveAntesDeConhecerOsVencedores(int quantidade, String nomes) {
        var participantes = new ArrayList<Participante>();
        for (int i = 1; i <= quantidade; i++) {
            var participante = new Participante();
            participante.setId((long) i);
            participantes.add(participante);
        }
        var fase = new Fase();
        gerador.gerar(fase, participantes);
        var response = mapper.toResponse(fase);
        assertEquals(java.util.List.of(nomes.split("/")), response.rodadas().stream().map(r -> r.etapa().nome()).toList());
        int indice = 0;
        for (var rodada : response.rodadas()) {
            assertNotNull(rodada.etapa().codigo());
            int quantidadeConfrontos = 1 << (response.rodadas().size() - rodada.numero());
            for (var confronto : response.confrontos().subList(indice, indice + quantidadeConfrontos)) {
                assertEquals(rodada.etapa(), confronto.etapa());
            }
            indice += quantidadeConfrontos;
        }
        assertEquals(response.confrontos().size(), indice);
        assertEquals("FINAL", response.confrontos().getLast().etapa().codigo());
        assertEquals("SEMIFINAL", response.rodadas().get(response.rodadas().size() - 2).etapa().codigo());
    }

    @Test
    void ligaESwissNaoRecebemNomesDeEtapasEliminatorias() {
        for (var tipo : java.util.List.of(TipoFase.LIGA, TipoFase.SWISS)) {
            var fase = new Fase();
            fase.setTipo(tipo);
            fase.getRodadas().add(new Rodada(1));
            assertNull(mapper.toResponse(fase).rodadas().getFirst().etapa());
        }
    }
}
