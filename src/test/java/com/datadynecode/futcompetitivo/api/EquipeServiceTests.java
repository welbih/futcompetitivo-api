package com.datadynecode.futcompetitivo.api;

import com.datadynecode.futcompetitivo.api.dto.EquipeRequest;
import com.datadynecode.futcompetitivo.api.dto.EquipeResponse;
import com.datadynecode.futcompetitivo.api.repository.EquipeRepository;
import com.datadynecode.futcompetitivo.api.service.EquipeService;
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
class EquipeServiceTests {

    @Autowired
    private EquipeService service;

    @Autowired
    private EquipeRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void criaConsultaEAtualizaEquipe() {
        EquipeResponse criado = service.criar(new EquipeRequest("Equipe Azul", "https://example.com/logo.png"));
        assertNotNull(criado.id());
        assertTrue(criado.ativo());
        assertEquals(criado, service.buscar(criado.id()));

        service.atualizar(criado.id(), new EquipeRequest("Equipe Azul Atualizada", "https://example.com/logo-nova.png"));
        entityManager.flush();
        entityManager.clear();

        EquipeResponse atualizado = service.buscar(criado.id());
        assertEquals("Equipe Azul Atualizada", atualizado.nome());
        assertEquals("https://example.com/logo-nova.png", atualizado.logoUrl());
        assertTrue(atualizado.ativo());
    }

    @Test
    void desativaSemExcluirRegistroEFiltraConsultas() {
        EquipeResponse inativo = service.criar(new EquipeRequest("Equipe Azul", "https://example.com/logo.png"));
        EquipeResponse ativo = service.criar(new EquipeRequest("Equipe Verde", "https://example.com/verde.png"));

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
                                new EquipeRequest("Outro", "https://example.com/logo.png"))).getStatusCode());
        service.desativar(inativo.id());
        assertTrue(repository.existsById(inativo.id()));
    }

    @Test
    void retorna404ParaEquipeInexistente() {
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> service.buscar(Long.MAX_VALUE)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> service.atualizar(Long.MAX_VALUE,
                                new EquipeRequest("Equipe Azul", "https://example.com/logo.png"))).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class,
                        () -> service.desativar(Long.MAX_VALUE)).getStatusCode());
    }
}
