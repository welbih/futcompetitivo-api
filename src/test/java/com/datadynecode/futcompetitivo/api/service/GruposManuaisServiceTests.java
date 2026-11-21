package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.CampeonatoStatus;
import com.datadynecode.futcompetitivo.api.entity.TipoFormato;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("test")
@Transactional
class GruposManuaisServiceTests {
    @Autowired CampeonatoService campeonatos;
    @Autowired OrganizacaoService organizacoes;
    @Autowired JogadorService jogadores;
    @Autowired EquipeService equipes;
    @Autowired EntityManager em;

    @Test
    void preservaGruposEOrdemInformadosEGeraAgendaSemResultados() {
        var criado = criar(14);
        var ids = criado.participantes().stream().map(ParticipanteResponse::id).toList();
        var a = new ArrayList<>(ids.subList(0,7)); Collections.reverse(a);
        var b = new ArrayList<>(ids.subList(7,14)); Collections.reverse(b);
        var iniciado = campeonatos.iniciarComGruposManuais(criado.id(), new IniciarGruposManuaisRequest(a,b,8,false,false));
        em.flush(); em.clear();
        var salvo = campeonatos.buscar(criado.id());
        assertEquals(CampeonatoStatus.EM_ANDAMENTO, iniciado.status());
        assertEquals(ids, salvo.participantes().stream().map(ParticipanteResponse::id).toList());
        assertEquals(a, salvo.fases().getFirst().grupos().getFirst().participantes().stream().map(ParticipanteResponse::id).toList());
        assertEquals(b, salvo.fases().getFirst().grupos().getLast().participantes().stream().map(ParticipanteResponse::id).toList());
        assertEquals(8, salvo.fases().getFirst().quantidadeClassificados());
        var rodadas = salvo.fases().getFirst().rodadas();
        assertEquals(7, rodadas.size());
        assertEquals(42, rodadas.stream().mapToLong(r -> r.partidas().size()).sum());
        rodadas.stream().flatMap(r -> r.partidas().stream()).forEach(p -> {
            assertNull(p.resultado());
            assertEquals(a.contains(p.mandante().id()), a.contains(p.visitante().id()));
        });
        assertThrows(RegraCampeonatoException.class, () -> campeonatos.iniciarComGruposManuais(criado.id(),
                new IniciarGruposManuaisRequest(a,b,8,false,false)));
    }

    @Test
    void resultadosRegistradosNormalmenteClassificamQuatroDeCadaGrupo() {
        var criado = criar(14);
        var ids = criado.participantes().stream().map(ParticipanteResponse::id).toList();
        var a = ids.subList(0,7); var b = ids.subList(7,14);
        var iniciado = campeonatos.iniciarComGruposManuais(criado.id(), new IniciarGruposManuaisRequest(a,b,8,false,false));
        for (var rodada : iniciado.fases().getFirst().rodadas()) {
            for (var partida : rodada.partidas()) campeonatos.registrarResultado(criado.id(), partida.id(), new ResultadoRequest(1,0,null,null,false));
        }
        em.flush(); em.clear();
        assertTrue(campeonatos.buscar(criado.id()).aguardandoMataMata());
        var mataMata = campeonatos.iniciarMataMata(criado.id()).fases().getLast();
        var classificados = mataMata.rodadas().getFirst().partidas().stream()
                .flatMap(p -> java.util.stream.Stream.of(p.mandante().id(),p.visitante().id())).toList();
        assertEquals(8, classificados.size());
        assertEquals(4, classificados.stream().filter(a::contains).count());
        assertEquals(4, classificados.stream().filter(b::contains).count());
    }

    @Test
    void validaRepetidosAusentesEstrangeirosENumeroDeVagas() {
        var criado = criar(8);
        var ids = criado.participantes().stream().map(ParticipanteResponse::id).toList();
        var a = ids.subList(0,4); var b = ids.subList(4,8);
        var repetidos = new ArrayList<>(b); repetidos.set(0,a.getFirst());
        var estrangeiros = new ArrayList<>(b); estrangeiros.set(0,Long.MAX_VALUE);
        for (var request : List.of(
                new IniciarGruposManuaisRequest(a,repetidos,4,false,false),
                new IniciarGruposManuaisRequest(a,estrangeiros,4,false,false),
                new IniciarGruposManuaisRequest(a,b.subList(0,3),4,false,false),
                new IniciarGruposManuaisRequest(null,b,4,false,false),
                new IniciarGruposManuaisRequest(a,b,10,false,false),
                new IniciarGruposManuaisRequest(ids.subList(0,2),ids.subList(2,8),8,false,false))) {
            assertThrows(RegraCampeonatoException.class, () -> campeonatos.iniciarComGruposManuais(criado.id(),request));
        }
        assertEquals(CampeonatoStatus.CRIADO, campeonatos.buscar(criado.id()).status());
        assertTrue(campeonatos.buscar(criado.id()).fases().isEmpty());
    }

    @Test
    void permiteTurnoEReturnoETerceiroLugar() {
        var criado = criar(8);
        var ids = criado.participantes().stream().map(ParticipanteResponse::id).toList();
        var fase = campeonatos.iniciarComGruposManuais(criado.id(),
                new IniciarGruposManuaisRequest(ids.subList(0,4),ids.subList(4,8),4,true,true)).fases().getFirst();
        assertEquals(6, fase.rodadas().size());
        assertEquals(24, fase.rodadas().stream().mapToInt(r -> r.partidas().size()).sum());
        assertTrue(fase.disputaTerceiroLugar());
    }

    private CampeonatoResponse criar(int quantidade) {
        var organizacao = organizacoes.criar(new OrganizacaoRequest("Campeonato historico",true));
        List<ParticipanteRequest> participantes = new ArrayList<>();
        for (int i=0; i<quantidade; i++) {
            var jogador = jogadores.criar(new JogadorRequest("Jogador historico " + i,null));
            var equipe = equipes.criar(new EquipeRequest("Equipe historica " + i,"https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(jogador.id(),equipe.id()));
        }
        return campeonatos.criar(new CampeonatoRequest(organizacao.id(),2020,participantes));
    }
}
