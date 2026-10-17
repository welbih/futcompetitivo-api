package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.controller.ParticipanteController;
import com.datadynecode.futcompetitivo.api.exception.ApiExceptionHandler;
import com.datadynecode.futcompetitivo.api.service.ParticipanteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ParticipanteControllerValidationTests {

    private ParticipanteService service;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        service = mock(ParticipanteService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ParticipanteController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"jogadorId\":null,\"equipeId\":null}"})
    void exigeJogadorEEquipe(String corpo) throws Exception {
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                post("/participantes"), put("/participantes/1")}) {
            mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.jogadorId").value("jogador é obrigatório."))
                    .andExpect(jsonPath("$.erros.equipeId").value("equipe é obrigatório."));
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"jogadorId\":0,\"equipeId\":-1}",
            "{\"jogadorId\":-1,\"equipeId\":0}"})
    void rejeitaIdsNaoPositivos(String corpo) throws Exception {
        mvc.perform(post("/participantes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.jogadorId").value("jogadorId deve ser positivo."))
                .andExpect(jsonPath("$.erros.equipeId").value("equipeId deve ser positivo."));
        verifyNoInteractions(service);
    }
}
