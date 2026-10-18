package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.TipoFormato;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import com.datadynecode.futcompetitivo.api.repository.OrganizacaoRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
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
class OrganizacaoServiceTests {
    @Autowired private OrganizacaoService service;
    @Autowired private OrganizacaoRepository repository;
    @Autowired private CampeonatoService campeonatos;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private ParticipanteService participantes;
    @Autowired private EntityManager entityManager;

    @Test
    void cadastraAtualizaDesativaEReativaSemExcluir() {
        var criado = service.criar(new OrganizacaoRequest("Liga", true));
        assertNotNull(criado.id());
        service.atualizar(criado.id(), new OrganizacaoRequest("Liga Local", false));
        entityManager.flush(); entityManager.clear();
        assertEquals("Liga Local", service.buscar(criado.id()).nome());
        assertFalse(service.buscar(criado.id()).ativo());
        assertTrue(service.listar().stream().anyMatch(o -> o.id().equals(criado.id())));
        service.atualizar(criado.id(), new OrganizacaoRequest("Liga Local", true));
        assertTrue(service.buscar(criado.id()).ativo());
        service.desativar(criado.id());
        service.desativar(criado.id());
        entityManager.flush(); entityManager.clear();
        assertTrue(repository.existsById(criado.id()));
        assertFalse(service.buscar(criado.id()).ativo());
    }

    @Test
    void variosCampeonatosCompartilhamOrganizacaoSemApagarHistoricoAoDesativar() {
        var org = service.criar(new OrganizacaoRequest("Liga", true));
        List<Long> ids = criarParticipantes();
        var primeiro = campeonatos.criar(new CampeonatoRequest(org.id(), 2026, selecoes(ids)));
        var segundo = campeonatos.criar(new CampeonatoRequest(org.id(), 2027, selecoes(ids)));
        entityManager.flush(); entityManager.clear();
        assertEquals(org.id(), campeonatos.buscar(primeiro.id()).organizacao().id());
        assertEquals(org.id(), campeonatos.buscar(segundo.id()).organizacao().id());
        service.desativar(org.id());
        entityManager.flush(); entityManager.clear();
        assertFalse(campeonatos.buscar(primeiro.id()).organizacao().ativo());
        assertThrows(ResponseStatusException.class, () -> campeonatos.criar(new CampeonatoRequest(org.id(), 2028, selecoes(ids))));
        assertThrows(RegraCampeonatoException.class, () -> campeonatos.iniciar(primeiro.id(),
                new IniciarCampeonatoRequest(TipoFormato.LIGA, false, null, null)));
    }

    @Test
    void permiteTrocarOrganizacaoAntesDeIniciarERejeitaOrganizacaoInexistente() {
        var org = service.criar(new OrganizacaoRequest("Liga", true));
        var outra = service.criar(new OrganizacaoRequest("Outra Liga", true));
        List<Long> ids = criarParticipantes();
        var campeonato = campeonatos.criar(new CampeonatoRequest(org.id(), 2026, selecoes(ids)));
        campeonatos.atualizar(campeonato.id(), new CampeonatoRequest(outra.id(), 2026, selecoes(ids)));
        entityManager.flush(); entityManager.clear();
        assertEquals(outra.id(), campeonatos.buscar(campeonato.id()).organizacao().id());
        assertThrows(ResponseStatusException.class, () -> campeonatos.criar(new CampeonatoRequest(Long.MAX_VALUE, 2026, selecoes(ids))));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.buscar(Long.MAX_VALUE)).getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.atualizar(Long.MAX_VALUE, new OrganizacaoRequest("Liga", true)));
        assertThrows(ResponseStatusException.class, () -> service.desativar(Long.MAX_VALUE));
    }

    private List<Long> criarParticipantes() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador " + i, null));
            var equipe = equipes.criar(new EquipeRequest("Equipe " + i, "https://example.com/logo.png"));
            ids.add(participantes.criar(new ParticipanteRequest(jogador.id(), equipe.id())).id());
        }
        return ids;
    }

    private List<ParticipanteRequest> selecoes(List<Long> ids) {
        return ids.stream().map(id -> {
            if (id == Long.MAX_VALUE) return new ParticipanteRequest(id, id);
            var participante = participantes.buscar(id);
            return new ParticipanteRequest(participante.jogador().id(), participante.equipe().id());
        }).toList();
    }
}
