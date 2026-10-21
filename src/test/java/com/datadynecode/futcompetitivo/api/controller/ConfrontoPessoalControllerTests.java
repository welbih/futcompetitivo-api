package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.ApiExceptionHandler;
import com.datadynecode.futcompetitivo.api.exception.RegraConfrontoPessoalException;
import com.datadynecode.futcompetitivo.api.service.ConfrontoPessoalService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ConfrontoPessoalControllerTests {
    private ConfrontoPessoalService service;
    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        service = mock(ConfrontoPessoalService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ConfrontoPessoalController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void exigeDoisJogadoresETipoDeSerie() throws Exception {
        mvc.perform(post("/x1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.participanteUm").value("participanteUm é obrigatório."))
                .andExpect(jsonPath("$.erros.participanteDois").value("participanteDois é obrigatório."))
                .andExpect(jsonPath("$.erros.tipo").value("tipo é obrigatório."));
        mvc.perform(post("/x1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"participanteUm\":{\"jogadorId\":1,\"equipeId\":3},\"participanteDois\":{\"jogadorId\":2,\"equipeId\":4},\"tipo\":\"JOGO_UNICO\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void criaSerieComDoisIdsSemReceberCampeonato() throws Exception {
        var request = new ConfrontoPessoalRequest(new ParticipanteRequest(1L, 3L), new ParticipanteRequest(2L, 4L), TipoSeriePessoal.MELHOR_DE_TRES, true);
        when(service.criar(request)).thenReturn(response());
        mvc.perform(post("/x1").contentType(MediaType.APPLICATION_JSON).content("""
                {"participanteUm":{"jogadorId":1,"equipeId":3},"participanteDois":{"jogadorId":2,"equipeId":4},"tipo":"MELHOR_DE_TRES","encerrarAntecipadamente":true}
                """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost/x1/10"))
                .andExpect(jsonPath("$.participanteUm.jogador.id").value(1)).andExpect(jsonPath("$.participanteDois.jogador.id").value(2))
                .andExpect(jsonPath("$.participanteUm.equipe.id").value(3)).andExpect(jsonPath("$.participanteDois.equipe.id").value(4))
                .andExpect(jsonPath("$.campeonato").doesNotExist());
        verify(service).criar(request);
    }

    @Test
    void consultaRegistraResultadoEEncerraPorRotasProprias() throws Exception {
        when(service.buscar(10L)).thenReturn(response());
        when(service.listar()).thenReturn(List.of(response()));
        when(service.registrarResultado(eq(10L), eq(20L), any())).thenReturn(response());
        when(service.encerrar(10L)).thenReturn(response());
        mvc.perform(get("/x1/10")).andExpect(status().isOk());
        mvc.perform(get("/x1")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(10));
        mvc.perform(put("/x1/10/partidas/20/resultado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"golsMandante\":2,\"golsVisitante\":0}"))
                .andExpect(status().isOk());
        verify(service).registrarResultado(10L, 20L, new ResultadoRequest(2, 0, null, null, null));
        mvc.perform(post("/x1/10/encerrar")).andExpect(status().isOk());
        verify(service).encerrar(10L);
    }

    @Test
    void validaJogadorEEquipeDosDoisParticipantes() throws Exception {
        mvc.perform(post("/x1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"participanteUm\":{},\"participanteDois\":{},\"tipo\":\"MELHOR_DE_TRES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros['participanteUm.jogadorId']").value("jogador é obrigatório."))
                .andExpect(jsonPath("$.erros['participanteUm.equipeId']").value("equipe é obrigatório."))
                .andExpect(jsonPath("$.erros['participanteDois.jogadorId']").value("jogador é obrigatório."))
                .andExpect(jsonPath("$.erros['participanteDois.equipeId']").value("equipe é obrigatório."));
        verifyNoInteractions(service);
    }

    @Test
    void validaPlacarETraduzRegraDeConfrontoPessoal() throws Exception {
        mvc.perform(put("/x1/10/partidas/20/resultado").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.erros.golsMandante").value("golsMandante é obrigatório."));
        mvc.perform(put("/x1/10/partidas/20/resultado").contentType(MediaType.APPLICATION_JSON)
                .content("{\"golsMandante\":-1,\"golsVisitante\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
        when(service.encerrar(10L)).thenThrow(new RegraConfrontoPessoalException("Nenhum jogador alcançou o número de vitórias necessário."));
        mvc.perform(post("/x1/10/encerrar")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Nenhum jogador alcançou o número de vitórias necessário."));
    }

    private ConfrontoPessoalResponse response() {
        return new ConfrontoPessoalResponse(10L, new ParticipanteResponse(11L, new JogadorResponse(1L, "Um", null, true), new EquipeResponse(3L, "Equipe um", "logo", true), true),
                new ParticipanteResponse(12L, new JogadorResponse(2L, "Dois", null, true), new EquipeResponse(4L, "Equipe dois", "logo", true), true), TipoSeriePessoal.MELHOR_DE_TRES, true,
                StatusConfrontoPessoal.EM_ANDAMENTO, 0, 0, null, List.of());
    }
}
