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
                .andExpect(jsonPath("$.erros.nome").value("nome é obrigatório."))
                .andExpect(jsonPath("$.erros.edicao").value("edicao é obrigatório."))
                .andExpect(jsonPath("$.erros.participanteIds").value("participantes é obrigatório."));
        verifyNoInteractions(service);
    }

    @Test
    void cadastroRetorna201ELocation() throws Exception {
        when(service.criar(any())).thenReturn(new CampeonatoResponse(1L, "Liga", 2026,
                CampeonatoStatus.CRIADO, List.of(), List.of(), List.of()));
        mvc.perform(post("/campeonatos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Liga\",\"edicao\":2026,\"participanteIds\":[1,2,3]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/campeonatos/1"))
                .andExpect(jsonPath("$.status").value("CRIADO"));
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
