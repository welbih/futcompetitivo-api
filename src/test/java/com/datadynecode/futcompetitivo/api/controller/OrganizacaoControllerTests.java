package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.exception.ApiExceptionHandler;
import com.datadynecode.futcompetitivo.api.service.OrganizacaoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrganizacaoControllerTests {
    private OrganizacaoService service;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        service = mock(OrganizacaoService.class);
        mvc = MockMvcBuilders.standaloneSetup(new OrganizacaoController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"ativo\":true}", "{\"nome\":null,\"ativo\":true}",
            "{\"nome\":\"\",\"ativo\":true}", "{\"nome\":\"   \",\"ativo\":true}"})
    void exigeNomeNoCadastroEAtualizacao(String corpo) throws Exception {
        mvc.perform(post("/organizacoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.nome").value("nome é obrigatório."));
        mvc.perform(put("/organizacoes/1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.nome").value("nome é obrigatório."));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"nome\":\"Liga\"}", "{\"nome\":\"Liga\",\"ativo\":null}"})
    void exigeAtivo(String corpo) throws Exception {
        mvc.perform(post("/organizacoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.ativo").value("ativo é obrigatório."));
        verifyNoInteractions(service);
    }

    @Test
    void cadastraComLocationEPermiteAtivoFalse() throws Exception {
        when(service.criar(any())).thenReturn(new OrganizacaoResponse(1L, "Liga", false));
        mvc.perform(post("/organizacoes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Liga\",\"ativo\":false}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/organizacoes/1"))
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void deleteDelegaDesativacaoERetorna204() throws Exception {
        mvc.perform(delete("/organizacoes/1")).andExpect(status().isNoContent());
        verify(service).desativar(1L);
    }
}
