package com.datadynecode.futcompetitivo.api.controller;

import org.junit.jupiter.api.Test;

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

    @Test
    void listagemAceitaSituacaoEPreservaPadraoAtivos() throws Exception {
        for (var situacao : com.datadynecode.futcompetitivo.api.dto.SituacaoCadastro.values()) {
            mvc.perform(get("/jogadores").param("situacao", situacao.name())).andExpect(status().isOk());
            verify(service).listar(situacao);
        }
        clearInvocations(service);
        mvc.perform(get("/jogadores")).andExpect(status().isOk());
        verify(service).listar(com.datadynecode.futcompetitivo.api.dto.SituacaoCadastro.ATIVOS);
        clearInvocations(service);
        mvc.perform(get("/jogadores").param("situacao", "INVALIDO")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void ativacaoRetornaRegistroAtivo() throws Exception {
        when(service.ativar(1L)).thenReturn(new JogadorResponse(1L, "Ativo", "valor", true));
        mvc.perform(put("/jogadores/1/ativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ativo").value(true));
        verify(service).ativar(1L);
    }
}
