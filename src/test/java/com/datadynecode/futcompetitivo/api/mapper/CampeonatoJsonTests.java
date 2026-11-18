package com.datadynecode.futcompetitivo.api.mapper;

import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.service.ConfrontoService;
import com.datadynecode.futcompetitivo.api.service.GeradorEliminatorioService;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CampeonatoJsonTests {
    private final CampeonatoMapper mapper = new CampeonatoMapper(mock(ParticipanteMapper.class), mock(OrganizacaoMapper.class));
    private final JsonMapper json = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource({"JOGO_UNICO,false", "JOGO_UNICO,true", "IDA_E_VOLTA,false", "IDA_E_VOLTA,true",
            "IDA_E_VOLTA_FINAL_UNICA,false", "IDA_E_VOLTA_FINAL_UNICA,true"})
    void eliminatoriaRetornaApenasIdsNosConfrontosEDadosCompletosNasRodadas(FormatoEliminatorio formato, boolean terceiro) {
        var fase = eliminatoria(formato, terceiro);
        var resposta = mapper.toResponse(fase);
        var body = json.readTree(json.writeValueAsString(resposta));
        var jogosDasRodadas = resposta.rodadas().stream().flatMap(r -> r.partidas().stream())
                .map(p -> p.id()).toList();
        var jogosDosConfrontos = resposta.confrontos().stream().flatMap(c -> c.partidaIds().stream()).toList();
        assertFalse(jogosDasRodadas.isEmpty());
        assertEquals(jogosDasRodadas.size(), new java.util.HashSet<>(jogosDasRodadas).size());
        assertEquals(new java.util.HashSet<>(jogosDasRodadas), new java.util.HashSet<>(jogosDosConfrontos));
        for (int i = 0; i < resposta.confrontos().size(); i++) {
            var confronto = resposta.confrontos().get(i);
            assertNull(confronto.partidas());
            assertNotNull(confronto.partidaIds());
            assertFalse(body.get("confrontos").get(i).has("partidas"));
            assertTrue(body.get("confrontos").get(i).get("partidaIds").isArray());
            var original = fase.getConfrontos().stream().filter(c -> c.getId().equals(confronto.id())).findFirst().orElseThrow();
            assertEquals(original.getPartidas().stream().map(Partida::getId).toList(), confronto.partidaIds());
        }
        var partida = body.get("rodadas").get(0).get("partidas").get(0);
        assertTrue(partida.has("status"));
        assertTrue(partida.has("resultado"));
        assertEquals("https://youtube.com/watch?v=teste", partida.get("youtubeUrl").asString());
    }

    @ParameterizedTest
    @EnumSource(value = TipoFase.class, names = {"LIGA", "SWISS", "GRUPOS"})
    void outrosFormatosPreservamContratoAnterior(TipoFase tipo) {
        var fase = new Fase();
        fase.setTipo(tipo);
        var rodada = new Rodada(1);
        var partida = new Partida();
        partida.setId(10L);
        rodada.getPartidas().add(partida);
        fase.getRodadas().add(rodada);
        var confronto = new Confronto();
        confronto.getPartidas().add(partida);
        fase.getConfrontos().add(confronto);
        var body = json.readTree(json.writeValueAsString(mapper.toResponse(fase)));
        assertTrue(body.get("confrontos").get(0).has("partidas"));
        assertFalse(body.get("confrontos").get(0).has("partidaIds"));
        assertEquals(10L, body.get("rodadas").get(0).get("partidas").get(0).get("id").asLong());
    }

    @Test
    void campeonatoMistoAlteraSomenteEtapaEliminatoria() {
        var campeonato = new Campeonato();
        var liga = new Fase();
        liga.setTipo(TipoFase.LIGA);
        campeonato.getFases().add(liga);
        campeonato.getFases().add(eliminatoria(FormatoEliminatorio.JOGO_UNICO, false));
        var body = json.readTree(json.writeValueAsString(mapper.toResponse(campeonato)));
        assertEquals("LIGA", body.get("fases").get(0).get("tipo").asString());
        assertTrue(body.get("fases").get(0).get("confrontos").isEmpty());
        assertFalse(body.get("fases").get(1).get("confrontos").get(0).has("partidas"));
        assertTrue(body.get("fases").get(1).get("confrontos").get(0).has("partidaIds"));
    }

    private Fase eliminatoria(FormatoEliminatorio formato, boolean terceiro) {
        var participantes = new ArrayList<Participante>();
        for (long i = 1; i <= 5; i++) {
            var participante = new Participante();
            participante.setId(i);
            participantes.add(participante);
        }
        var fase = new Fase();
        fase.setFormatoEliminatorio(formato);
        fase.setDisputaTerceiroLugar(terceiro);
        new GeradorEliminatorioService(new ConfrontoService()).gerar(fase, participantes);
        long id = 1;
        for (var confronto : fase.getConfrontos()) confronto.setId(id++);
        for (var rodada : fase.getRodadas()) {
            for (var partida : rodada.getPartidas()) {
                partida.setId(id++);
                partida.setYoutubeUrl("https://youtube.com/watch?v=teste");
            }
        }
        return fase;
    }
}
