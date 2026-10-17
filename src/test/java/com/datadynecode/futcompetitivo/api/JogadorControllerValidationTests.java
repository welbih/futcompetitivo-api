package com.datadynecode.futcompetitivo.api;

import com.datadynecode.futcompetitivo.api.controller.JogadorController;
import com.datadynecode.futcompetitivo.api.dto.JogadorResponse;
import com.datadynecode.futcompetitivo.api.exception.ApiExceptionHandler;
import com.datadynecode.futcompetitivo.api.service.JogadorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JogadorControllerValidationTests {

    private JogadorService service;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        service = mock(JogadorService.class);
        mvc = MockMvcBuilders.standaloneSetup(new JogadorController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nome\":null}", "{\"nome\":\"\"}", "{\"nome\":\"   \"}"})
    void rejeitaNomeInvalidoNoCadastroEAtualizacao(String corpo) throws Exception {
        mvc.perform(post("/jogadores").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").value("nome é obrigatório."));
        mvc.perform(put("/jogadores/1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").value("nome é obrigatório."));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"nome\":\"Osmar\"}", "{\"nome\":\"Osmar\",\"apelido\":null}"})
    void aceitaJogadorSemApelido(String corpo) throws Exception {
        when(service.criar(any())).thenReturn(new JogadorResponse(1L, "Osmar", null, true));
        mvc.perform(post("/jogadores").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
    }
}
