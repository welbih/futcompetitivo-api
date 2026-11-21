package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.*;
import com.datadynecode.futcompetitivo.api.exception.RegraCampeonatoException;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="spring.jpa.show-sql=false")
@ActiveProfiles("test")
@Transactional
class IdentificacaoCampeonatoServiceTests {
    @Autowired CampeonatoService service;
    @Autowired OrganizacaoService organizacoes;
    @Autowired JogadorService jogadores;
    @Autowired EquipeService equipes;
    @Autowired EntityManager em;

    @ParameterizedTest
    @EnumSource(CampeonatoStatus.class)
    void trocaIdentificacaoEmQualquerStatusPreservandoCompeticao(CampeonatoStatus status) {
        var antes=preparar(status);
        var base=List.copyOf(em.find(Campeonato.class,antes.id()).getClassificadosBase());
        var destino=organizacoes.criar(new OrganizacaoRequest("Nova organizacao",true));
        var resposta=service.alterarIdentificacao(antes.id(),new IdentificacaoCampeonatoRequest(destino.id(),Integer.MAX_VALUE));
        assertEquals(destino,resposta.organizacao()); assertEquals(Integer.MAX_VALUE,resposta.edicao());
        assertCompeticaoIgual(antes,resposta);
        em.flush(); em.clear();
        assertEquals(resposta,service.buscar(antes.id()));
        assertEquals(base,em.find(Campeonato.class,antes.id()).getClassificadosBase());
        if(status!=CampeonatoStatus.CRIADO) {
            assertFalse(resposta.participantesDesistentes().isEmpty());
            assertFalse(resposta.substituicoesMataMata().isEmpty());
            assertFalse(resposta.fases().getFirst().grupos().isEmpty());
            assertTrue(resposta.fases().getFirst().rodadas().stream().flatMap(r -> r.partidas().stream()).anyMatch(PartidaResponse::wo));
            assertThrows(RegraCampeonatoException.class,()->service.atualizar(antes.id(),
                    new CampeonatoRequest(destino.id(),3,antes.participantes().stream().map(p -> new ParticipanteRequest(p.jogador().id(),p.equipe().id())).toList())));
        }
    }

    @Test
    void permiteCorrigirSomenteEdicaoNaOrganizacaoAtualInativa() {
        var c=preparar(CampeonatoStatus.EM_ANDAMENTO);
        organizacoes.desativar(c.organizacao().id());
        var antes=service.buscar(c.id());
        var corrigido=service.alterarIdentificacao(c.id(),new IdentificacaoCampeonatoRequest(c.organizacao().id(),5));
        assertEquals(5,corrigido.edicao()); assertFalse(corrigido.organizacao().ativo());
        assertCompeticaoIgual(antes,corrigido);
        assertEquals(corrigido,service.alterarIdentificacao(c.id(),new IdentificacaoCampeonatoRequest(c.organizacao().id(),5)));
        var ativa=organizacoes.criar(new OrganizacaoRequest("Destino ativo",true));
        assertEquals(ativa,service.alterarIdentificacao(c.id(),new IdentificacaoCampeonatoRequest(ativa.id(),6)).organizacao());
    }

    @Test
    void rejeitaTrocaParaInativaERecursosInexistentesSemAlterarDados() {
        var c=preparar(CampeonatoStatus.CRIADO);
        var inativa=organizacoes.criar(new OrganizacaoRequest("Inativa",false));
        assertThrows(RegraCampeonatoException.class,()->service.alterarIdentificacao(c.id(),new IdentificacaoCampeonatoRequest(inativa.id(),5)));
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.alterarIdentificacao(c.id(),
                new IdentificacaoCampeonatoRequest(Long.MAX_VALUE,5))).getStatusCode().value());
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.alterarIdentificacao(Long.MAX_VALUE,
                new IdentificacaoCampeonatoRequest(c.organizacao().id(),5))).getStatusCode().value());
        assertEquals(c,service.buscar(c.id()));
    }

    @Test
    void validaEdicaoPositivaEValoresIdenticos() {
        var c=preparar(CampeonatoStatus.FINALIZADO);
        assertEquals(c,service.alterarIdentificacao(c.id(),new IdentificacaoCampeonatoRequest(c.organizacao().id(),c.edicao())));
        for(Integer edicao:Arrays.asList(null,0,-1)) assertThrows(RegraCampeonatoException.class,()->
                service.alterarIdentificacao(c.id(),new IdentificacaoCampeonatoRequest(c.organizacao().id(),edicao)));
        assertEquals(c,service.buscar(c.id()));
    }

    @Test
    void preservaAusenciaDeUnicidadeDaEdicaoPorOrganizacao() {
        var um=criar(4); var dois=criar(4);
        var alterado=service.alterarIdentificacao(dois.id(),new IdentificacaoCampeonatoRequest(um.organizacao().id(),um.edicao()));
        assertEquals(um.organizacao().id(),alterado.organizacao().id()); assertEquals(um.edicao(),alterado.edicao());
    }

    private void assertCompeticaoIgual(CampeonatoResponse antes,CampeonatoResponse depois) {
        assertEquals(antes.id(),depois.id()); assertEquals(antes.status(),depois.status());
        assertEquals(antes.participantes(),depois.participantes()); assertEquals(antes.fases(),depois.fases());
        assertEquals(antes.classificacaoPorRodada(),depois.classificacaoPorRodada());
        assertEquals(antes.classificacaoAtual(),depois.classificacaoAtual());
        assertEquals(antes.participantesDesistentes(),depois.participantesDesistentes());
        assertEquals(antes.substituicoesMataMata(),depois.substituicoesMataMata());
        assertEquals(antes.aguardandoMataMata(),depois.aguardandoMataMata());
    }

    private CampeonatoResponse preparar(CampeonatoStatus status) {
        var c=criar(status==CampeonatoStatus.CRIADO ? 4:12);
        if(status==CampeonatoStatus.CRIADO) return c;
        var ids=c.participantes().stream().map(ParticipanteResponse::id).toList();
        service.iniciarComGruposManuais(c.id(),new IniciarGruposManuaisRequest(null,null,8,false,true,
                FormatoEliminatorio.IDA_E_VOLTA_FINAL_UNICA,FormaGeracaoMataMata.AUTOMATICA,
                List.of(ids.subList(0,4),ids.subList(4,8),ids.subList(8,12))));
        var estado=service.desistirParticipante(c.id(),ids.getFirst());
        for(var r:estado.fases().getFirst().rodadas()) for(var p:r.partidas()) if(!p.wo())
            service.registrarResultado(c.id(),p.id(),placar(1,0));
        Long desistente=service.classificados(c.id(),8).getFirst().id();
        service.desistirParticipante(c.id(),desistente);
        var vagas=service.vagasMataMata(c.id());
        var escolhas=vagas.stream().map(v -> new IniciarMataMataRequest.Substituicao(v.desistenteId(),v.candidatos().getFirst().id())).toList();
        var iniciado=service.iniciarMataMata(c.id(),new IniciarMataMataRequest(escolhas));
        service.registrarResultado(c.id(),iniciado.fases().getLast().rodadas().getFirst().partidas().getFirst().id(),placar(2,0));
        if(status==CampeonatoStatus.FINALIZADO) {
            while(service.buscar(c.id()).status()!=CampeonatoStatus.FINALIZADO) {
                var fase=service.buscar(c.id()).fases().getLast();
                var rodada=fase.rodadas().stream().filter(r -> r.status()==RodadaStatus.EM_ANDAMENTO).findFirst().orElseThrow();
                for(var p:rodada.partidas()) if(p.status()!=StatusPartida.FINALIZADA) {
                    var confronto=fase.confrontos().stream().filter(x -> x.partidaIds().contains(p.id())).findFirst().orElseThrow();
                    service.registrarResultado(c.id(),p.id(),confronto.partidaIds().indexOf(p.id())==0 ? placar(2,0):placar(0,2));
                }
            }
        }
        em.flush(); em.clear();
        return service.buscar(c.id());
    }

    private ResultadoRequest placar(int a,int b) {return new ResultadoRequest(a,b,null,null,false);}
    private CampeonatoResponse criar(int total) {
        var org=organizacoes.criar(new OrganizacaoRequest("Origem",true));
        var participantes=new ArrayList<ParticipanteRequest>();
        for(int i=0;i<total;i++) {
            var j=jogadores.criar(new JogadorRequest("Jogador "+i,null));
            var e=equipes.criar(new EquipeRequest("Equipe "+i,"https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(j.id(),e.id()));
        }
        return service.criar(new CampeonatoRequest(org.id(),1,participantes));
    }
}
