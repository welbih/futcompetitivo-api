package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.suporte.HistoricoJogadorFixture;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:estatisticas_http;DB_CLOSE_DELAY=-1", "spring.jpa.show-sql=false",
        "spring.jpa.open-in-view=false"})
@ActiveProfiles("test")
class JogadorEstatisticasControllerTests {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Environment environment;
    Long jogadorId;

    @BeforeEach
    void preparar() {
        jogadorId = new TransactionTemplate(transactions).execute(status -> {
            var f = new HistoricoJogadorFixture(em);
            f.campeonato(f.dois, true, 5, 1);
            f.x1(f.tres, false, 1, 3);
            return f.jogador.getId();
        });
    }

    @Test
    void endpointPublicoRetornaResumoERecordesComContextoCompleto() throws Exception {
        var response = get(jogadorId + "/estatisticas");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("json"));
        assertEquals("TODOS", JsonPath.read(response.body(), "$.origem"));
        assertEquals(2, (int) JsonPath.read(response.body(), "$.resumo.partidasDisputadas"));
        Map<String, Object> recorde = JsonPath.read(response.body(), "$.maioresGoleadasAplicadasPorGols[0]");
        assertEquals("CAMPEONATO", recorde.get("origem"));
        assertEquals("B9", JsonPath.read(response.body(), "$.maioresGoleadasAplicadasPorGols[0].adversario.apelido"));
        assertEquals("https://example.com/azul.png", JsonPath.read(response.body(), "$.maioresGoleadasAplicadasPorGols[0].equipeJogador.logoUrl"));
        assertEquals("Liga da Vila 2026", JsonPath.read(response.body(), "$.maioresGoleadasAplicadasPorGols[0].competicao.nome"));
        assertEquals(2026, (int) JsonPath.read(response.body(), "$.maioresGoleadasAplicadasPorGols[0].competicao.edicao"));
        assertEquals("Liga da Vila", JsonPath.read(response.body(), "$.maioresGoleadasAplicadasPorGols[0].competicao.organizacao.nome"));
        assertEquals("LIGA", JsonPath.read(response.body(), "$.maioresGoleadasAplicadasPorGols[0].fase.tipo"));
        assertTrue(recorde.containsKey("dataPartida")); assertNull(recorde.get("dataPartida"));
        assertTrue(recorde.containsKey("penaltis")); assertNull(recorde.get("penaltis"));
        assertEquals("X1", JsonPath.read(response.body(), "$.maioresGoleadasSofridasPorGols[0].origem"));
    }

    @Test
    void filtrosDeOrigemSaoRespeitadosEParametrosDePaginacaoNaoLimitamHistorico() throws Exception {
        var campeonato = get(jogadorId + "/estatisticas?origem=CAMPEONATO&page=10&size=1");
        assertEquals(200, campeonato.statusCode());
        assertEquals(1, (int) JsonPath.read(campeonato.body(), "$.resumo.vitorias"));
        assertEquals(List.of(), JsonPath.read(campeonato.body(), "$.maioresCarrascos"));
        var x1 = get(jogadorId + "/estatisticas?origem=X1");
        assertEquals(200, x1.statusCode());
        assertEquals(1, (int) JsonPath.read(x1.body(), "$.resumo.derrotas"));
        assertEquals(List.of(), JsonPath.read(x1.body(), "$.maioresFregueses"));
    }

    @Test
    void jogadorInativoContinuaDisponivelSemAutenticacao() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                em.find(com.datadynecode.futcompetitivo.api.entity.Jogador.class, jogadorId).setAtivo(false));
        var response = get(jogadorId + "/estatisticas");
        assertEquals(200, response.statusCode());
        assertEquals(Boolean.FALSE, JsonPath.read(response.body(), "$.jogador.ativo"));
        assertEquals(2, (int) JsonPath.read(response.body(), "$.resumo.partidasDisputadas"));
    }

    @Test
    void errosDeParametroENaoEncontradoSeguemPadraoDaApi() throws Exception {
        assertEquals(400, get(jogadorId + "/estatisticas?origem=INVALIDA").statusCode());
        var response = get(Long.MAX_VALUE + "/estatisticas");
        assertEquals(404, response.statusCode());
        assertEquals("Jogador nao encontrado: " + Long.MAX_VALUE, JsonPath.read(response.body(), "$.detail"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"
                + environment.getProperty("local.server.port") + "/jogadores/" + path)).GET().build();
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
