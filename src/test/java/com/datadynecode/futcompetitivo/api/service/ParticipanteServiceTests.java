package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.EquipeRequest;
import com.datadynecode.futcompetitivo.api.dto.JogadorRequest;
import com.datadynecode.futcompetitivo.api.dto.ParticipanteRequest;
import com.datadynecode.futcompetitivo.api.service.EquipeService;
import com.datadynecode.futcompetitivo.api.service.JogadorService;
import com.datadynecode.futcompetitivo.api.service.ParticipanteService;
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
class ParticipanteServiceTests {

    @Autowired private ParticipanteService service;
    @Autowired private JogadorService jogadores;
    @Autowired private EquipeService equipes;
    @Autowired private EntityManager entityManager;

    @Test
    void criaConsultaEAtualizaRelacionamentosManyToOne() {
        var jogador = jogadores.criar(new JogadorRequest("Welber", "Sniper"));
        var outroJogador = jogadores.criar(new JogadorRequest("Osmar", null));
        var equipe = equipes.criar(new EquipeRequest("Azul", "https://example.com/azul.png"));
        var outraEquipe = equipes.criar(new EquipeRequest("Verde", "https://example.com/verde.png"));
        var primeiro = service.criar(new ParticipanteRequest(jogador.id(), equipe.id()));
        var segundo = service.criar(new ParticipanteRequest(jogador.id(), outraEquipe.id()));
        var terceiro = service.criar(new ParticipanteRequest(outroJogador.id(), equipe.id()));
        entityManager.flush();
        entityManager.clear();

        assertEquals(primeiro, service.buscar(primeiro.id()));
        assertEquals(jogador.id(), service.buscar(segundo.id()).jogador().id());
        assertEquals(equipe.id(), service.buscar(terceiro.id()).equipe().id());

        service.atualizar(primeiro.id(), new ParticipanteRequest(outroJogador.id(), outraEquipe.id()));
        entityManager.flush();
        entityManager.clear();
        var atualizado = service.buscar(primeiro.id());
        assertEquals(outroJogador.id(), atualizado.jogador().id());
        assertEquals(outraEquipe.id(), atualizado.equipe().id());
    }

    @Test
    void rejeitaRelacionamentosInexistentesOuInativos() {
        var jogador = jogadores.criar(new JogadorRequest("Welber", "Sniper"));
        var equipe = equipes.criar(new EquipeRequest("Azul", "https://example.com/azul.png"));
        assert404(() -> service.criar(new ParticipanteRequest(Long.MAX_VALUE, equipe.id())));
        assert404(() -> service.criar(new ParticipanteRequest(jogador.id(), Long.MAX_VALUE)));
        equipes.desativar(equipe.id());
        assert404(() -> service.criar(new ParticipanteRequest(jogador.id(), equipe.id())));
        jogadores.desativar(jogador.id());
        assert404(() -> service.criar(new ParticipanteRequest(jogador.id(), equipe.id())));
        assert404(() -> service.buscar(Long.MAX_VALUE));
        assert404(() -> service.atualizar(Long.MAX_VALUE,
                new ParticipanteRequest(jogador.id(), equipe.id())));
    }

    private void assert404(org.junit.jupiter.api.function.Executable acao) {
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ResponseStatusException.class, acao).getStatusCode());
    }
}
