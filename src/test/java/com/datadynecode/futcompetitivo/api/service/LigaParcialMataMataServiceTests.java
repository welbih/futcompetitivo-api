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

@SpringBootTest(properties="spring.jpa.show-sql=false")
@ActiveProfiles("test")
@Transactional
class LigaParcialMataMataServiceTests {
    @Autowired CampeonatoService service;
    @Autowired OrganizacaoService organizacoes;
    @Autowired JogadorService jogadores;
    @Autowired EquipeService equipes;
    @Autowired EntityManager em;

    @ParameterizedTest
    @CsvSource({"AUTOMATICA,JOGO_UNICO", "AUTOMATICA,IDA_E_VOLTA", "AUTOMATICA,IDA_E_VOLTA_FINAL_UNICA",
            "ALEATORIA,JOGO_UNICO", "ALEATORIA,IDA_E_VOLTA", "ALEATORIA,IDA_E_VOLTA_FINAL_UNICA"})
    void agendaAutomaticaEClassificacaoAteCampeao(FormaGeracaoMataMata forma,FormatoEliminatorio modo) {
        var c=criar();
        var iniciado=service.iniciar(c.id(),config(forma,modo));
        var fase=iniciado.fases().getFirst();
        assertEquals(3,fase.jogosPorParticipante());
        assertEquals(fase.rodadas().size(),fase.totalRodadas());
        var contagem=new HashMap<Long,Integer>();
        Set<String> pares=new HashSet<>();
        for(var r:fase.rodadas()) {
            Set<Long> usados=new HashSet<>();
            for(var p:r.partidas()) {
                assertTrue(usados.add(p.mandante().id())); assertTrue(usados.add(p.visitante().id()));
                assertTrue(pares.add(Math.min(p.mandante().id(),p.visitante().id())+":"+Math.max(p.mandante().id(),p.visitante().id())));
                contagem.merge(p.mandante().id(),1,Integer::sum); contagem.merge(p.visitante().id(),1,Integer::sum);
            }
        }
        assertEquals(6,contagem.size()); assertTrue(contagem.values().stream().allMatch(n -> n==3));
        assertThrows(RegraCampeonatoException.class,()->service.iniciarMataMata(c.id()));
        preencher(c.id(),fase);
        var aguardando=service.buscar(c.id());
        assertEquals(CampeonatoStatus.EM_ANDAMENTO,aguardando.status()); assertTrue(aguardando.aguardandoMataMata());
        assertEquals(1,aguardando.fases().size());
        assertEquals(4,service.classificados(c.id(),4).size());
        var aberto=service.iniciarMataMata(c.id());
        assertEquals(forma,aberto.fases().getLast().formaGeracaoMataMata());
        assertEquals(modo,aberto.fases().getLast().formatoEliminatorio());
        while(service.buscar(c.id()).status()!=CampeonatoStatus.FINALIZADO) {
            var atual=service.buscar(c.id()).fases().getLast();
            var rodada=atual.rodadas().stream().filter(r -> r.status()==RodadaStatus.EM_ANDAMENTO).findFirst().orElseThrow();
            for(var p:rodada.partidas()) {
                var confronto=atual.confrontos().stream().filter(x -> x.partidaIds().contains(p.id())).findFirst().orElseThrow();
                service.registrarResultado(c.id(),p.id(),confronto.partidaIds().indexOf(p.id())==0 ? placar(2,0):placar(0,2));
            }
        }
        assertNotNull(service.vencedor(c.id())); assertNotNull(service.terceiroLugar(c.id()));
    }

    @Test
    void agendaManualPreservaRodadasMandosEPermiteMataMataManual() {
        var c=criar(); var agenda=agenda(c);
        var resposta=service.iniciarLigaParcialManual(c.id(),new IniciarLigaParcialManualRequest(config(FormaGeracaoMataMata.ALEATORIA,FormatoEliminatorio.JOGO_UNICO),agenda));
        em.flush(); em.clear();
        var fase=service.buscar(c.id()).fases().getFirst();
        assertEquals(3,fase.totalRodadas()); assertEquals(3,fase.jogosPorParticipante());
        for(int i=0;i<3;i++) for(int j=0;j<3;j++) {
            assertEquals(agenda.get(i).partidas().get(j).mandanteId(),fase.rodadas().get(i).partidas().get(j).mandante().id());
            assertEquals(agenda.get(i).partidas().get(j).visitanteId(),fase.rodadas().get(i).partidas().get(j).visitante().id());
        }
        var primeiro=fase.rodadas().getFirst().partidas().getFirst();
        assertTrue(service.registrarResultado(c.id(),primeiro.id(),placar(1,0)).classificacaoAtual().parcial());
        preencher(c.id(),fase);
        var classificados=service.classificados(c.id(),4).stream().map(ParticipanteResponse::id).toList();
        var pares=List.of(new ConfrontoInicialRequest(classificados.get(2),classificados.get(0)),
                new ConfrontoInicialRequest(classificados.get(1),classificados.get(3)));
        var mata=service.iniciarMataMata(c.id(),new IniciarMataMataRequest(null,pares)).fases().getLast();
        assertEquals(pares.getFirst().mandanteId(),mata.rodadas().getFirst().partidas().getFirst().mandante().id());
        assertThrows(RegraCampeonatoException.class,()->service.iniciarLigaParcialManual(c.id(),new IniciarLigaParcialManualRequest(config(null,null),agenda)));
    }

    @Test
    void rejeitaAgendaIncompletaRepeticoesEstrangeirosENulosSemIniciar() {
        var c=criar(); var original=agenda(c); var ids=c.participantes().stream().map(ParticipanteResponse::id).toList();
        var invalidas=new ArrayList<List<IniciarLigaParcialManualRequest.RodadaManual>>();
        invalidas.add(List.of()); invalidas.add(original.subList(0,2));
        invalidas.add(List.of(original.getFirst(),original.getFirst(),original.getLast()));
        for(var par:Arrays.asList(new ConfrontoInicialRequest(ids.get(0),ids.get(0)),
                new ConfrontoInicialRequest(ids.get(0),ids.get(2)),new ConfrontoInicialRequest(null,ids.get(1)),
                new ConfrontoInicialRequest(ids.get(0),Long.MAX_VALUE),null)) {
            var jogos=new ArrayList<>(original.getFirst().partidas()); jogos.set(0,par);
            var rodadas=new ArrayList<>(original); rodadas.set(0,new IniciarLigaParcialManualRequest.RodadaManual(jogos)); invalidas.add(rodadas);
        }
        for(var rodadas:invalidas) {
            assertThrows(RegraCampeonatoException.class,()->service.iniciarLigaParcialManual(c.id(),new IniciarLigaParcialManualRequest(config(null,null),rodadas)));
            assertEquals(CampeonatoStatus.CRIADO,service.buscar(c.id()).status());
        }
        for(int jogos:List.of(0,2,6)) assertThrows(RegraCampeonatoException.class,()->service.iniciar(c.id(),
                new IniciarCampeonatoRequest(TipoFormato.MISTA_PARCIAL,false,jogos,null,4)));
        assertThrows(RegraCampeonatoException.class,()->service.iniciar(c.id(),new IniciarCampeonatoRequest(TipoFormato.MISTA_PARCIAL,true,3,null,4)));
        assertThrows(RegraCampeonatoException.class,()->service.iniciar(c.id(),new IniciarCampeonatoRequest(TipoFormato.MISTA_PARCIAL,false,3,3,4)));
        for(Integer vagas:Arrays.asList(null,3,7)) assertThrows(RegraCampeonatoException.class,()->service.iniciar(c.id(),
                new IniciarCampeonatoRequest(TipoFormato.MISTA_PARCIAL,false,3,null,vagas)));
    }

    @Test
    void desistenciasMantemWoEVagaManual() {
        var c=criar();
        var inicio=service.iniciarLigaParcialManual(c.id(),new IniciarLigaParcialManualRequest(config(null,null),agenda(c)));
        var p=inicio.fases().getFirst().rodadas().getFirst().partidas().getFirst();
        service.desistirParticipante(c.id(),p.mandante().id());
        var estado=service.buscar(c.id());
        assertEquals(3,estado.fases().getFirst().rodadas().stream().flatMap(r -> r.partidas().stream()).filter(PartidaResponse::wo).count());
        preencher(c.id(),estado.fases().getFirst());
        Long lider=service.classificados(c.id(),4).getFirst().id();
        service.desistirParticipante(c.id(),lider);
        assertTrue(service.vagasMataMata(c.id()).stream().anyMatch(v -> v.desistenteId().equals(lider)));
        assertThrows(RegraCampeonatoException.class,()->service.iniciarMataMata(c.id()));
    }

    private List<IniciarLigaParcialManualRequest.RodadaManual> agenda(CampeonatoResponse c) {
        var ids=c.participantes().stream().map(ParticipanteResponse::id).toList();
        int[][][] rodadas={{{0,1},{2,3},{4,5}},{{0,2},{1,4},{3,5}},{{0,3},{1,5},{2,4}}};
        return Arrays.stream(rodadas).map(r -> new IniciarLigaParcialManualRequest.RodadaManual(Arrays.stream(r)
                .map(p -> new ConfrontoInicialRequest(ids.get(p[0]),ids.get(p[1]))).toList())).toList();
    }
    private IniciarCampeonatoRequest config(FormaGeracaoMataMata forma,FormatoEliminatorio modo) {
        return new IniciarCampeonatoRequest(TipoFormato.MISTA_PARCIAL,false,3,null,4,modo,true,forma);
    }
    private void preencher(Long id,FaseResponse fase) {
        for(var r:fase.rodadas()) for(var p:r.partidas()) if(!p.wo()) service.registrarResultado(id,p.id(),placar(1,0));
    }
    private ResultadoRequest placar(int a,int b) {return new ResultadoRequest(a,b,null,null,false);}
    private CampeonatoResponse criar() {
        var org=organizacoes.criar(new OrganizacaoRequest("Liga parcial antiga",true));
        var lista=new ArrayList<ParticipanteRequest>();
        for(int i=0;i<6;i++) {
            var j=jogadores.criar(new JogadorRequest("Jogador "+i,null));
            var e=equipes.criar(new EquipeRequest("Equipe "+i,"https://example.com/logo.png"));
            lista.add(new ParticipanteRequest(j.id(),e.id()));
        }
        return service.criar(new CampeonatoRequest(org.id(),2020,lista));
    }
}
