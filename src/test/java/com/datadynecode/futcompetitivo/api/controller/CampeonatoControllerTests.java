package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.controller.CampeonatoController;
import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.CampeonatoStatus;
import com.datadynecode.futcompetitivo.api.exception.ApiExceptionHandler;
import com.datadynecode.futcompetitivo.api.service.CampeonatoService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CampeonatoControllerTests {
    private CampeonatoService service;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        service = mock(CampeonatoService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CampeonatoController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void cadastroValidaCamposObrigatorios() throws Exception {
        mvc.perform(post("/campeonatos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.organizacaoId").value("organizacao é obrigatório."))
                .andExpect(jsonPath("$.erros.edicao").value("edicao é obrigatório."))
                .andExpect(jsonPath("$.erros.participantes").value("participantes é obrigatório."));
        verifyNoInteractions(service);
    }

    @Test
    void validaJogadorEEquipeDeCadaParticipante() throws Exception {
        mvc.perform(post("/campeonatos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizacaoId\":1,\"edicao\":2026,\"participantes\":[{},{},{},{}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['participantes[0].jogadorId']").value("jogador é obrigatório."))
                .andExpect(jsonPath("$.erros['participantes[0].equipeId']").value("equipe é obrigatório."));
        verifyNoInteractions(service);
    }

    @Test
    void rejeitaMenosDeQuatroParticipantesNaCriacaoEAtualizacao() throws Exception {
        for (int quantidade = 0; quantidade < 4; quantidade++) {
            String participantes = java.util.stream.IntStream.range(0, quantidade)
                    .mapToObj(i -> "{\"jogadorId\":" + (i + 1) + ",\"equipeId\":1}")
                    .collect(java.util.stream.Collectors.joining(","));
            String payload = "{\"organizacaoId\":1,\"edicao\":2026,\"participantes\":[" + participantes + "]}";
            mvc.perform(post("/campeonatos").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.participantes").value("O campeonato deve ter no mínimo 4 participantes."));
            mvc.perform(put("/campeonatos/1").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.participantes").value("O campeonato deve ter no mínimo 4 participantes."));
        }
        verifyNoInteractions(service);
    }

    @Test
    void formatoMistoRecebeQuantidadeDeClassificadosEValidaMinimo() throws Exception {
        mvc.perform(post("/campeonatos/1/iniciar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formato\":\"MISTA\",\"turnoEreturno\":true,\"quantidadeClassificados\":4}"))
                .andExpect(status().isOk());
        verify(service).iniciar(1L, new IniciarCampeonatoRequest(
                com.datadynecode.futcompetitivo.api.entity.TipoFormato.MISTA, true, null, null, 4));
        clearInvocations(service);
        mvc.perform(post("/campeonatos/1/iniciar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formato\":\"MISTA\",\"quantidadeClassificados\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.quantidadeClassificados").value("O mata-mata deve ter no mínimo 4 classificados."));
        verifyNoInteractions(service);
    }

    @Test
    void cadastroRetorna201ELocation() throws Exception {
        when(service.criar(any())).thenReturn(new CampeonatoResponse(1L, new OrganizacaoResponse(1L, "Liga", true), 2026,
                CampeonatoStatus.CRIADO, List.of(), List.of(), List.of()));
        mvc.perform(post("/campeonatos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizacaoId\":1,\"edicao\":2026,\"participantes\":[{\"jogadorId\":1,\"equipeId\":2},{\"jogadorId\":2,\"equipeId\":2},{\"jogadorId\":3,\"equipeId\":2},{\"jogadorId\":4,\"equipeId\":2}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/campeonatos/1"))
                .andExpect(jsonPath("$.status").value("CRIADO"));
    }

    @Test
    void resultadosEmLoteValidamPlacaresEDelegamSemFinalizar() throws Exception {
        mvc.perform(put("/campeonatos/1/rodadas/2/resultados").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultados\":[{\"partidaId\":3,\"resultado\":{\"golsMandante\":2,\"golsVisitante\":1}}]}"))
                .andExpect(status().isOk());
        verify(service).registrarResultadosSemFinalizar(1L, 2L,
                List.of(new ResultadoPartidaRequest(3L, new ResultadoRequest(2, 1, null, null, null))));
        clearInvocations(service);
        mvc.perform(put("/campeonatos/1/rodadas/2/resultados").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/campeonatos/1/rodadas/2/resultados").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultados\":[{\"partidaId\":3,\"resultado\":{\"golsMandante\":-1}}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void finalizacaoManualDelegaAoServico() throws Exception {
        mvc.perform(post("/campeonatos/1/rodadas/2/finalizar")).andExpect(status().isOk());
        verify(service).finalizarRodada(1L, 2L);
    }

    @Test
    void rejeitaResultadoSemGolsOuComGolsNegativos() throws Exception {
        mvc.perform(put("/campeonatos/1/partidas/1/resultado")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.golsMandante").exists())
                .andExpect(jsonPath("$.erros.golsVisitante").exists());
        mvc.perform(put("/campeonatos/1/partidas/1/resultado")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"golsMandante\":-1,\"golsVisitante\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
