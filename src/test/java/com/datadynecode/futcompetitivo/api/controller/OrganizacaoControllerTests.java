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

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
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
    @ValueSource(strings = {"POST", "PUT"})
    void recebeTrofeuMultipartNoCadastroEEdicao(String metodo) throws Exception {
        var dados = new org.springframework.mock.web.MockMultipartFile("dados", "", "application/json",
                "{\"nome\":\"Liga\",\"ativo\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] imagem = "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var trofeu = new org.springframework.mock.web.MockMultipartFile("trofeu", "trofeu.svg", "image/svg+xml", imagem);
        var request = new OrganizacaoRequest("Liga", true);
        var response = new OrganizacaoResponse(1L, "Liga", true, "/organizacoes/1/trofeu");
        when(service.criar(eq(request), any(byte[].class), eq("image/svg+xml"))).thenReturn(response);
        when(service.atualizar(eq(1L), eq(request), any(byte[].class), eq("image/svg+xml"))).thenReturn(response);
        mvc.perform(multipart(org.springframework.http.HttpMethod.valueOf(metodo), metodo.equals("POST") ? "/organizacoes" : "/organizacoes/1")
                .file(dados).file(trofeu))
                .andExpect(status().is(metodo.equals("POST") ? 201 : 200))
                .andExpect(jsonPath("$.trofeuUrl").value("/organizacoes/1/trofeu"));
        if (metodo.equals("POST")) verify(service).criar(request, imagem, "image/svg+xml");
        else verify(service).atualizar(1L, request, imagem, "image/svg+xml");
    }

    @Test
    void informaMotivoDaRejeicaoDoSvgERregistraNoLog(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        var dados = new org.springframework.mock.web.MockMultipartFile("dados", "", "application/json",
                "{\"nome\":\"Liga\",\"ativo\":true}".getBytes());
        var trofeu = new org.springframework.mock.web.MockMultipartFile("trofeu", "trofeu.svg", "image/svg+xml",
                "<svg xmlns='http://www.w3.org/2000/svg'><script/></svg>".getBytes());
        when(service.criar(any(), any(byte[].class), anyString())).thenThrow(
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "O SVG deve conter apenas elementos gráficos estáticos."));
        mvc.perform(multipart("/organizacoes").file(dados).file(trofeu))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.detail").value("O SVG deve conter apenas elementos gráficos estáticos."));
        org.junit.jupiter.api.Assertions.assertTrue(output.getOut().contains("status=400 motivo=O SVG deve conter apenas elementos gráficos estáticos."));
    }

    @Test
    void informaParteMultipartAusente() throws Exception {
        var trofeu = new org.springframework.mock.web.MockMultipartFile("trofeu", "trofeu.svg", "image/svg+xml", "svg".getBytes());
        mvc.perform(multipart("/organizacoes").file(trofeu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("dados")));
        verifyNoInteractions(service);
    }

    @Test
    void informaJsonInvalidoNoMultipart() throws Exception {
        var dados = new org.springframework.mock.web.MockMultipartFile("dados", "", "application/json", "{invalido".getBytes());
        var trofeu = new org.springframework.mock.web.MockMultipartFile("trofeu", "trofeu.svg", "image/svg+xml", "svg".getBytes());
        mvc.perform(multipart("/organizacoes").file(dados).file(trofeu))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("JSON inválido")));
        verifyNoInteractions(service);
    }

    @Test
    void retornaImagemSalvaComContentType() throws Exception {
        byte[] imagem = "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes();
        when(service.buscarTrofeu(1L)).thenReturn(new TrofeuImagem(imagem, "image/svg+xml"));
        mvc.perform(get("/organizacoes/1/trofeu")).andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml")).andExpect(content().bytes(imagem));
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
