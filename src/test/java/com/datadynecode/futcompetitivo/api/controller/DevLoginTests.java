package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.repository.UsuarioRepository;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.datadynecode.futcompetitivo.api.suporte.PostgresTestConfiguration;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "security.client-id=dev-test", "security.client-secret=dev-secret", "jwt.issuer=http://localhost:8080"
})
@ActiveProfiles("dev")
@Import(PostgresTestConfiguration.class)
class DevLoginTests {
    @Autowired Environment environment;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtDecoder decoder;

    @Test
    void autenticaOsDoisUsuariosDoScriptDevSemDuplicar() throws Exception {
        assertEquals(2, usuarios.count());
        for (String perfil : java.util.List.of("admin", "user")) {
            String email = perfil + "@futcompetitivo.local";
            var request = HttpRequest.newBuilder(URI.create("http://localhost:"
                    + environment.getProperty("local.server.port") + "/oauth2/token"))
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString("dev-test:dev-secret".getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=urn:futcompetitivo:params:oauth:grant-type:password&username="
                        + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=123456")).build();
            try (var client = HttpClient.newHttpClient()) {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode(), response.body());
                String token = JsonPath.read(response.body(), "$.access_token");
                var jwt = decoder.decode(token);
                assertEquals(email, jwt.getSubject());
                assertEquals(java.util.List.of("ROLE_" + perfil.toUpperCase(java.util.Locale.ROOT)), jwt.getClaimAsStringList("authorities"));
            }
        }
    }
}
