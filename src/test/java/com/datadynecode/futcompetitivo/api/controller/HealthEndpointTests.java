package com.datadynecode.futcompetitivo.api.controller;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:health_endpoint;DB_CLOSE_DELAY=-1",
        "spring.jpa.show-sql=false"
})
@ActiveProfiles("test")
class HealthEndpointTests {
    @Autowired private Environment environment;

    @Test
    void retornaSaudeSemExporDetalhesInternos() throws Exception {
        var response = consultar("/actuator/health");
        assertEquals(200, response.statusCode());
        Map<String, Object> body = JsonPath.read(response.body(), "$");
        assertEquals("UP", body.get("status"));
        assertTrue(Set.of("status", "groups").containsAll(body.keySet()));
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("json"));
    }

    @Test
    void naoExpoeOutrosEndpointsAdministrativos() throws Exception {
        assertEquals(404, consultar("/actuator/env").statusCode());
        assertEquals(404, consultar("/actuator/beans").statusCode());
    }

    private HttpResponse<String> consultar(String path) throws Exception {
        int port = environment.getRequiredProperty("local.server.port", Integer.class);
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
