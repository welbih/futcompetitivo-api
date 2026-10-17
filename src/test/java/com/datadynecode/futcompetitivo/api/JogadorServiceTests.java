package com.datadynecode.futcompetitivo.api;

import com.datadynecode.futcompetitivo.api.dto.JogadorRequest;
import com.datadynecode.futcompetitivo.api.dto.JogadorResponse;
import com.datadynecode.futcompetitivo.api.repository.JogadorRepository;
import com.datadynecode.futcompetitivo.api.service.JogadorService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class JogadorServiceTests {

    @Autowired
    private JogadorService service;

    @Autowired
    private JogadorRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void criaConsultaEAtualizaJogador() {
        JogadorResponse criado = service.criar(new JogadorRequest("Joao", "Jota"));
        assertNotNull(criado.id());
        assertTrue(criado.ativo());
        assertEquals(criado, service.buscar(criado.id()));

        service.atualizar(criado.id(), new JogadorRequest("Joao Silva", "JS"));
        entityManager.flush();
        entityManager.clear();

        JogadorResponse atualizado = service.buscar(criado.id());
        assertEquals("Joao Silva", atualizado.nome());
        assertEquals("JS", atualizado.apelido());
        assertTrue(atualizado.ativo());
    }

    @Test
    void desativaSemExcluirRegistroEFiltraConsultas() {
        JogadorResponse inativo = service.criar(new JogadorRequest("Joao", "Jota"));
        JogadorResponse ativo = service.criar(new JogadorRequest("Pedro", null));

        service.desativar(inativo.id());
        entityManager.flush();
        entityManager.clear();

        assertFalse(repository.findById(inativo.id()).orElseThrow().isAtivo());
        assertTrue(service.listar().stream().anyMatch(j -> j.id().equals(ativo.id())));
        assertTrue(service.listar().stream().noneMatch(j -> j.id().equals(inativo.id())));
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> service.buscar(inativo.id())).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> service.atualizar(inativo.id(),
                                new JogadorRequest("Outro", null))).getStatusCode());
        service.desativar(inativo.id());
        assertTrue(repository.existsById(inativo.id()));
    }

    @Test
    void retorna404ParaJogadorInexistente() {
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> service.buscar(Long.MAX_VALUE)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> service.atualizar(Long.MAX_VALUE,
                                new JogadorRequest("Joao", null))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> service.desativar(Long.MAX_VALUE)).getStatusCode());
    }
}
