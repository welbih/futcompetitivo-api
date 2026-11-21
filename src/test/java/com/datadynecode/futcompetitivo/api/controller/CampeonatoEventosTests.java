package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.dto.*;
import com.datadynecode.futcompetitivo.api.entity.TipoFormato;
import com.datadynecode.futcompetitivo.api.service.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.datasource.url=jdbc:h2:mem:sse-http;DB_CLOSE_DELAY=-1","spring.jpa.show-sql=false",
        "campeonatos.sse.heartbeat-ms=200","campeonatos.sse.timeout-ms=30000","campeonatos.sse.timeout-envio-ms=5000"})
@ActiveProfiles("test")
class CampeonatoEventosTests {
    @Autowired Environment environment;
    @Autowired CampeonatoService campeonatos;
    @Autowired OrganizacaoService organizacoes;
    @Autowired JogadorService jogadores;
    @Autowired EquipeService equipes;
    private final HttpClient http=HttpClient.newHttpClient();
    private final JsonMapper json=JsonMapper.builder().build();

    @Test
    void streamPublicoTemHeadersContratoHeartbeatAtualizacaoEExclusao() throws Exception {
        var c=criar();
        var inicio=campeonatos.iniciar(c.id(),new IniciarCampeonatoRequest(TipoFormato.LIGA,false,null,null));
        var request=HttpRequest.newBuilder(uri("/campeonatos/"+c.id()+"/eventos"))
                .header("Accept","text/event-stream").header("Origin","http://localhost:4200")
                .header("Last-Event-ID","evento-perdido").GET().build();
        var resposta=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
        assertEquals(200,resposta.statusCode());
        assertTrue(resposta.headers().firstValue("Content-Type").orElseThrow().startsWith("text/event-stream"));
        assertEquals("no",resposta.headers().firstValue("X-Accel-Buffering").orElseThrow());
        assertTrue(resposta.headers().firstValue("Cache-Control").orElseThrow().contains("no-transform"));
        assertEquals("http://localhost:4200",resposta.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        try(var stream=new Stream(resposta.body())) {
            var conectado=stream.proximoEvento();
            assertEquals("conectado",conectado.nome()); assertFalse(conectado.id().isBlank());
            assertTrue(conectado.raw().contains("retry:3000"));
            assertEquals(c.id().longValue(),json.readTree(conectado.data()).get("campeonatoId").asLong());
            var heartbeat=stream.frame();
            assertTrue(heartbeat.raw().contains(":heartbeat")); assertTrue(heartbeat.id().isEmpty());
            var p=inicio.fases().getFirst().rodadas().getFirst().partidas().getFirst();
            campeonatos.registrarResultado(c.id(),p.id(),new ResultadoRequest(2,1,null,null,false));
            var evento=stream.proximoEvento();
            assertEquals("campeonato-atualizado",evento.nome()); assertFalse(evento.id().isBlank());
            assertNotEquals(conectado.id(),evento.id());
            var dados=json.readTree(evento.data());
            assertEquals(c.id().longValue(),dados.get("campeonatoId").asLong());
            assertEquals("RESULTADO_ALTERADO",dados.get("motivo").asText());
            assertEquals(p.id().longValue(),dados.get("partidaId").asLong());
            assertNotNull(Instant.parse(dados.get("ocorridoEm").asText()));
            assertEquals(Set.of("campeonatoId","motivo","ocorridoEm","rodadaId","partidaId"),dados.propertyNames());
            var completo=http.send(HttpRequest.newBuilder(uri("/campeonatos/"+c.id())).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertEquals(200,completo.statusCode());
            assertTrue(json.readTree(completo.body()).get("classificacaoAtual").get("parcial").asBoolean());
            // A abertura publica do stream nao libera exclusao sem ADMIN.
            assertEquals(401,http.send(HttpRequest.newBuilder(uri("/campeonatos/"+c.id())).DELETE().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            campeonatos.deletar(c.id());
            var excluido=stream.proximoEvento();
            assertEquals("campeonato-excluido",excluido.nome());
            assertEquals("CAMPEONATO_EXCLUIDO",json.readTree(excluido.data()).get("motivo").asText());
            assertNull(stream.frame());
        }
    }

    @Test
    void inexistenteRetorna404AntesDeAbrirStreamECorsAceitaLastEventId() throws Exception {
        var resposta=http.send(HttpRequest.newBuilder(uri("/campeonatos/9223372036854775807/eventos"))
                .header("Accept","text/event-stream").GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(404,resposta.statusCode(),resposta.body());
        assertTrue(resposta.headers().firstValue("Content-Type").orElseThrow().contains("application/problem+json"));
        var preflight=http.send(HttpRequest.newBuilder(uri("/campeonatos/1/eventos"))
                .header("Origin","http://localhost:4200").header("Access-Control-Request-Method","GET")
                .header("Access-Control-Request-Headers","last-event-id")
                .method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,preflight.statusCode());
        assertTrue(preflight.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase().contains("last-event-id"));
    }

    private URI uri(String caminho) {return URI.create("http://localhost:"+environment.getProperty("local.server.port")+caminho);}
    private CampeonatoResponse criar() {
        var org=organizacoes.criar(new OrganizacaoRequest("SSE HTTP",true));
        var participantes=new ArrayList<ParticipanteRequest>();
        for(int i=0;i<4;i++) {
            var j=jogadores.criar(new JogadorRequest("Jogador HTTP "+i,null));
            var e=equipes.criar(new EquipeRequest("Equipe HTTP "+i,"https://example.com/logo.png"));
            participantes.add(new ParticipanteRequest(j.id(),e.id()));
        }
        return campeonatos.criar(new CampeonatoRequest(org.id(),2020,participantes));
    }

    private record Frame(String nome,String id,String data,String raw) {}
    private static final class Stream implements AutoCloseable {
        private final BufferedReader reader;
        private final ExecutorService leitura=Executors.newVirtualThreadPerTaskExecutor();
        Stream(InputStream input) {reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));}
        Frame proximoEvento() throws Exception {
            for(int i=0;i<30;i++) {var frame=frame(); if(frame==null || !frame.nome().isEmpty()) return frame;}
            throw new AssertionError("Evento SSE não recebido");
        }
        Frame frame() throws Exception {
            return leitura.submit(() -> {
                String nome="",id="",data=""; var raw=new StringBuilder();
                for(String linha;(linha=reader.readLine())!=null;) {
                    if(linha.isEmpty()) {if(!raw.isEmpty()) return new Frame(nome,id,data,raw.toString());continue;}
                    raw.append(linha).append('\n');
                    if(linha.startsWith("event:")) nome=linha.substring(6).trim();
                    if(linha.startsWith("id:")) id=linha.substring(3).trim();
                    if(linha.startsWith("data:")) data+=linha.substring(5).trim();
                }
                return null;
            }).get(5,TimeUnit.SECONDS);
        }
        public void close() throws IOException {reader.close();leitura.shutdownNow();}
    }
}
