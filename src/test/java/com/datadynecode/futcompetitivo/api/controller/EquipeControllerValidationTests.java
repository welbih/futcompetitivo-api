package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.controller.EquipeController;
import com.datadynecode.futcompetitivo.api.dto.EquipeResponse;
import com.datadynecode.futcompetitivo.api.exception.ApiExceptionHandler;
import com.datadynecode.futcompetitivo.api.service.EquipeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EquipeControllerValidationTests {

    private EquipeService service;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        service = mock(EquipeService.class);
        mvc = MockMvcBuilders.standaloneSetup(new EquipeController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nome\":null}", "{\"nome\":\"\"}", "{\"nome\":\"   \"}"})
    void rejeitaNomeInvalidoNoCadastroEAtualizacao(String corpo) throws Exception {
        mvc.perform(post("/equipes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").value("nome é obrigatório."));
        mvc.perform(put("/equipes/1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").value("nome é obrigatório."));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"nome\":\"Equipe Azul\"}",
            "{\"nome\":\"Equipe Azul\",\"logoUrl\":null}",
            "{\"nome\":\"Equipe Azul\",\"logoUrl\":\"\"}",
            "{\"nome\":\"Equipe Azul\",\"logoUrl\":\"   \"}"})
    void rejeitaLogoUrlInvalidoNoCadastroEAtualizacao(String corpo) throws Exception {
        mvc.perform(post("/equipes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.logoUrl").value("logoUrl é obrigatório."));
        mvc.perform(put("/equipes/1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.logoUrl").value("logoUrl é obrigatório."));
        verifyNoInteractions(service);
    }

    @Test
    void aceitaEquipeComNomeELogoUrl() throws Exception {
        String corpo = "{\"nome\":\"Equipe Azul\",\"logoUrl\":\"https://example.com/logo.png\"}";
        EquipeResponse response = new EquipeResponse(1L, "Equipe Azul",
                "https://example.com/logo.png", true);
        when(service.criar(any())).thenReturn(response);
        when(service.atualizar(eq(1L), any())).thenReturn(response);
        mvc.perform(post("/equipes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        mvc.perform(put("/equipes/1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
    }

    @Test
    void listagemAceitaSituacaoEPreservaPadraoAtivos() throws Exception {
        for (var situacao : com.datadynecode.futcompetitivo.api.dto.SituacaoCadastro.values()) {
            mvc.perform(get("/equipes").param("situacao", situacao.name())).andExpect(status().isOk());
            verify(service).listar(situacao);
        }
        clearInvocations(service);
        mvc.perform(get("/equipes")).andExpect(status().isOk());
        verify(service).listar(com.datadynecode.futcompetitivo.api.dto.SituacaoCadastro.ATIVOS);
        clearInvocations(service);
        mvc.perform(get("/equipes").param("situacao", "INVALIDO")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void ativacaoRetornaRegistroAtivo() throws Exception {
        when(service.ativar(1L)).thenReturn(new EquipeResponse(1L, "Ativo", "valor", true));
        mvc.perform(put("/equipes/1/ativar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ativo").value(true));
        verify(service).ativar(1L);
    }
}
