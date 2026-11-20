package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("test")
@Transactional
class ConfrontosManuaisServiceTests {
    @Autowired CampeonatoService service;
    @Autowired OrganizacaoService organizacoes;
    @Autowired JogadorService jogadores;
    @Autowired EquipeService equipes;
    @Autowired EntityManager em;

    @ParameterizedTest
    @CsvSource({"ELIMINATORIO,JOGO_UNICO", "ELIMINATORIO,IDA_E_VOLTA", "ELIMINATORIO,IDA_E_VOLTA_FINAL_UNICA",
            "MISTA,JOGO_UNICO", "MISTA,IDA_E_VOLTA", "MISTA,IDA_E_VOLTA_FINAL_UNICA",
            "GRUPOS,JOGO_UNICO", "GRUPOS,IDA_E_VOLTA", "GRUPOS,IDA_E_VOLTA_FINAL_UNICA"})
    void preservaEscolhasMandosEAvancaAutomaticamente(TipoFormato formato, FormatoEliminatorio modo) {
        var criado = criar(formato == TipoFormato.ELIMINATORIO ? 4 : 6);
        List<Long> ids;
        if (formato == TipoFormato.ELIMINATORIO) ids = criado.participantes().stream().map(ParticipanteResponse::id).toList();
        else {
            var iniciado = service.iniciar(criado.id(),new IniciarCampeonatoRequest(formato,false,null,null,4,modo,true,FormaGeracaoMataMata.ALEATORIA));
            for (var r : iniciado.fases().getFirst().rodadas()) for (var p : r.partidas())
                service.registrarResultado(criado.id(),p.id(),placar(2,0));
            assertEquals(1,service.buscar(criado.id()).fases().size());
            ids = service.classificados(criado.id(),4).stream().map(ParticipanteResponse::id).toList();
        }
        var pares = List.of(new ConfrontoInicialRequest(ids.get(2),ids.get(0)),new ConfrontoInicialRequest(ids.get(1),ids.get(3)));
        var iniciado = formato == TipoFormato.ELIMINATORIO
                ? service.iniciarEliminatorioManual(criado.id(),new IniciarEliminatorioManualRequest(modo,true,pares,FormaGeracaoMataMata.ALEATORIA))
                : service.iniciarMataMata(criado.id(),new IniciarMataMataRequest(null,pares));
        em.flush(); em.clear();
        var fase = service.buscar(criado.id()).fases().getLast();
        assertTrue(em.find(Fase.class,fase.id()).isConfrontosManuais());
        assertEquals(FormaGeracaoMataMata.ALEATORIA,fase.formaGeracaoMataMata());
        for (int i=0;i<2;i++) {
            var c = fase.confrontos().get(i);
            assertEquals(pares.get(i).mandanteId(),c.participanteUmId());
            assertEquals(pares.get(i).visitanteId(),c.participanteDoisId());
            var partidas = fase.rodadas().getFirst().partidas().stream().filter(p -> c.partidaIds().contains(p.id())).toList();
            assertEquals(modo==FormatoEliminatorio.JOGO_UNICO ? 1 : 2,partidas.size());
            assertEquals(pares.get(i).mandanteId(),partidas.getFirst().mandante().id());
            for (int j=0;j<partidas.size();j++) {
                var p = partidas.get(j);
                assertEquals(j==0 ? pares.get(i).mandanteId() : pares.get(i).visitanteId(),p.mandante().id());
                service.registrarResultado(criado.id(),p.id(),j==0 ? placar(2,0) : placar(0,2));
            }
        }
        var finalFase = service.buscar(criado.id()).fases().getLast();
        assertEquals(RodadaStatus.EM_ANDAMENTO,finalFase.rodadas().getLast().status());
        assertEquals(ids.get(2),finalFase.confrontos().getLast().participanteUmId());
        assertEquals(ids.get(1),finalFase.confrontos().getLast().participanteDoisId());
        assertThrows(RegraCampeonatoException.class, () -> service.iniciarEliminatorioManual(criado.id(),
                new IniciarEliminatorioManualRequest(modo,true,pares)));
    }

    @Test
    void validaParticipantesDuplicadosAusentesExternosEConfrontosVazios() {
        var c = criar(4);
        var ids = c.participantes().stream().map(ParticipanteResponse::id).toList();
        var invalidos = List.of(
                List.<ConfrontoInicialRequest>of(),
                List.of(new ConfrontoInicialRequest(ids.get(0),ids.get(1))),
                List.of(new ConfrontoInicialRequest(ids.get(0),ids.get(0)),new ConfrontoInicialRequest(ids.get(2),ids.get(3))),
                List.of(new ConfrontoInicialRequest(ids.get(0),Long.MAX_VALUE),new ConfrontoInicialRequest(ids.get(2),ids.get(3))),
                List.of(new ConfrontoInicialRequest(ids.get(0),null),new ConfrontoInicialRequest(ids.get(2),ids.get(3))),
                List.of(new ConfrontoInicialRequest(null,null),new ConfrontoInicialRequest(ids.get(2),ids.get(3))));
        for (var pares : invalidos) {
            assertThrows(RegraCampeonatoException.class, () -> service.iniciarEliminatorioManual(c.id(),
                    new IniciarEliminatorioManualRequest(null,false,pares)));
            assertEquals(CampeonatoStatus.CRIADO,service.buscar(c.id()).status());
        }
    }

    @Test
    void permiteFolgasExplicitasSemCriarPartidaFicticia() {
        var c = criar(5);
        var ids = c.participantes().stream().map(ParticipanteResponse::id).toList();
        var pares = List.of(new ConfrontoInicialRequest(ids.get(0),ids.get(4)),
                new ConfrontoInicialRequest(ids.get(1),null), new ConfrontoInicialRequest(null,ids.get(2)),
                new ConfrontoInicialRequest(ids.get(3),null));
        var r = service.iniciarEliminatorioManual(c.id(),new IniciarEliminatorioManualRequest(null,false,pares));
        assertEquals(1,r.fases().getFirst().rodadas().getFirst().partidas().size());
        var partida = r.fases().getFirst().rodadas().getFirst().partidas().getFirst();
        service.registrarResultado(c.id(),partida.id(),placar(1,0));
        assertEquals(2,service.buscar(c.id()).fases().getFirst().rodadas().get(1).partidas().size());
    }

    @ParameterizedTest
    @CsvSource({"ELIMINATORIO,AUTOMATICA", "ELIMINATORIO,ALEATORIA",
            "MISTA,AUTOMATICA", "MISTA,ALEATORIA", "GRUPOS,AUTOMATICA", "GRUPOS,ALEATORIA"})
    void aplicaConfiguracaoEPreservaChaveSalva(TipoFormato formato, FormaGeracaoMataMata geracao) {
        var c=criar(formato==TipoFormato.ELIMINATORIO ? 4 : 6);
        var iniciado=service.iniciar(c.id(),new IniciarCampeonatoRequest(formato,false,null,null,
                formato==TipoFormato.ELIMINATORIO ? null : 4,FormatoEliminatorio.IDA_E_VOLTA,false,geracao));
        assertEquals(geracao,iniciado.fases().getFirst().formaGeracaoMataMata());
        List<Long> elegiveis;
        if(formato!=TipoFormato.ELIMINATORIO) {
            for(var r:iniciado.fases().getFirst().rodadas()) for(var p:r.partidas()) service.registrarResultado(c.id(),p.id(),placar(2,0));
            elegiveis=service.classificados(c.id(),4).stream().map(ParticipanteResponse::id).toList();
            iniciado=service.iniciarMataMata(c.id());
        } else elegiveis=c.participantes().stream().map(ParticipanteResponse::id).toList();
        var fase=iniciado.fases().getLast();
        assertEquals(geracao,fase.formaGeracaoMataMata());
        var pares=fase.confrontos().stream().filter(x -> x.origemUmId()==null).toList();
        assertEquals(new HashSet<>(elegiveis),pares.stream().flatMap(x -> java.util.stream.Stream.of(x.participanteUmId(),x.participanteDoisId()))
                .collect(java.util.stream.Collectors.toSet()));
        if(geracao==FormaGeracaoMataMata.AUTOMATICA) {
            assertEquals(elegiveis.get(0),pares.get(0).participanteUmId());
            assertEquals(elegiveis.get(3),pares.get(0).participanteDoisId());
            assertEquals(elegiveis.get(1),pares.get(1).participanteUmId());
            assertEquals(elegiveis.get(2),pares.get(1).participanteDoisId());
        }
        em.flush(); em.clear();
        assertEquals(fase,service.buscar(c.id()).fases().getLast());
    }

    @Test
    void gruposManuaisPreservamPreferenciaESemMataMataRejeitaConfiguracao() {
        var c=criar(6);
        var ids=c.participantes().stream().map(ParticipanteResponse::id).toList();
        var iniciado=service.iniciarComGruposManuais(c.id(),new IniciarGruposManuaisRequest(ids.subList(0,3),ids.subList(3,6),
                4,false,false,null,FormaGeracaoMataMata.ALEATORIA));
        for(var r:iniciado.fases().getFirst().rodadas()) for(var p:r.partidas()) service.registrarResultado(c.id(),p.id(),placar(1,0));
        assertEquals(FormaGeracaoMataMata.ALEATORIA,service.iniciarMataMata(c.id()).fases().getLast().formaGeracaoMataMata());
        var liga=criar(4);
        assertThrows(RegraCampeonatoException.class,()->service.iniciar(liga.id(),new IniciarCampeonatoRequest(
                TipoFormato.LIGA,false,null,null,null,null,false,FormaGeracaoMataMata.ALEATORIA)));
    }

    @ParameterizedTest
    @CsvSource({"6,2,5,false", "10,3,4,false", "12,4,8,true", "15,5,8,false", "18,6,4,false", "20,3,8,true"})
    void quantidadeDeGruposGeraAgendaEClassificados(int total,int quantidade,int vagas,boolean returno) {
        var c=criar(total);
        var iniciado=service.iniciar(c.id(),new IniciarCampeonatoRequest(TipoFormato.GRUPOS,returno,null,null,vagas,
                null,false,FormaGeracaoMataMata.AUTOMATICA,quantidade));
        var fase=iniciado.fases().getFirst();
        assertEquals(quantidade,fase.quantidadeGrupos());
        assertTrue(fase.grupos().stream().allMatch(g -> g.participantes().size()>=3));
        int menor=fase.grupos().stream().mapToInt(g -> g.participantes().size()).min().orElseThrow();
        int maior=fase.grupos().stream().mapToInt(g -> g.participantes().size()).max().orElseThrow();
        assertTrue(maior-menor<=1);
        Map<Long,Long> grupoPorId=new HashMap<>();
        fase.grupos().forEach(g -> g.participantes().forEach(p -> assertNull(grupoPorId.put(p.id(),g.id()))));
        assertEquals(total,grupoPorId.size());
        int esperados=fase.grupos().stream().mapToInt(g -> g.participantes().size()*(g.participantes().size()-1)/2).sum()*(returno?2:1);
        assertEquals(esperados,fase.rodadas().stream().mapToInt(r -> r.partidas().size()).sum());
        for(var r:fase.rodadas()) {
            Set<Long> usados=new HashSet<>();
            for(var p:r.partidas()) {
                assertEquals(grupoPorId.get(p.mandante().id()),grupoPorId.get(p.visitante().id()));
                assertTrue(usados.add(p.mandante().id())); assertTrue(usados.add(p.visitante().id()));
                service.registrarResultado(c.id(),p.id(),placar(2,0));
            }
        }
        em.flush(); em.clear();
        assertEquals(quantidade,service.buscar(c.id()).fases().getFirst().quantidadeGrupos());
        assertTrue(service.buscar(c.id()).aguardandoMataMata());
        var classificados=service.classificados(c.id(),vagas);
        assertEquals(vagas,classificados.size());
        for(var g:service.grupos(c.id())) {
            var garantidos=service.classificacaoGrupo(c.id(),g.id()).tabela().stream().limit(vagas/quantidade).map(p -> p.participante().id()).toList();
            assertTrue(classificados.stream().map(ParticipanteResponse::id).toList().containsAll(garantidos));
        }
        assertEquals(TipoFase.ELIMINATORIA,service.iniciarMataMata(c.id()).fases().getLast().tipo());
    }

    @Test
    void validaQuantidadeEMinimoTresEPermiteVariosGruposManuais() {
        var c=criar(10);
        for(int quantidade:List.of(0,1,4,20)) assertThrows(RegraCampeonatoException.class,()->service.iniciar(c.id(),
                new IniciarCampeonatoRequest(TipoFormato.GRUPOS,false,null,null,4,null,false,null,quantidade)));
        assertThrows(RegraCampeonatoException.class,()->service.iniciar(c.id(),new IniciarCampeonatoRequest(
                TipoFormato.LIGA,false,null,null,null,null,false,null,3)));
        var ids=c.participantes().stream().map(ParticipanteResponse::id).toList();
        var grupos=List.of(ids.subList(0,4),ids.subList(4,7),ids.subList(7,10));
        var r=service.iniciarComGruposManuais(c.id(),new IniciarGruposManuaisRequest(null,null,4,false,false,null,null,grupos));
        assertEquals(3,r.fases().getFirst().quantidadeGrupos());
        for(int i=0;i<3;i++) assertEquals(grupos.get(i),r.fases().getFirst().grupos().get(i).participantes().stream().map(ParticipanteResponse::id).toList());
        var pequeno=criar(5);
        assertThrows(RegraCampeonatoException.class,()->service.iniciar(pequeno.id(),new IniciarCampeonatoRequest(TipoFormato.GRUPOS,false,null,null,4)));
    }

    private ResultadoRequest placar(int a,int b) { return new ResultadoRequest(a,b,null,null,false); }
    private CampeonatoResponse criar(int total) {
        var org = organizacoes.criar(new OrganizacaoRequest("Copa antiga",true));
        var participantes = new ArrayList<ParticipanteRequest>();
        for (int i=0;i<total;i++) {
            var j=jogadores.criar(new JogadorRequest("Jogador " + i,null));
            var e=equipes.criar(new EquipeRequest("Equipe " + i,"https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(j.id(),e.id()));
        }
        return service.criar(new CampeonatoRequest(org.id(),2020,participantes));
    }
}
