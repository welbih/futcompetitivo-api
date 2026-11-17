package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.repository.UsuarioRepository;
import com.datadynecode.futcompetitivo.api.repository.RoleRepository;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:autenticacao;DB_CLOSE_DELAY=-1",
    "spring.jpa.show-sql=false", "security.client-id=teste", "security.client-secret=segredo"
})
@ActiveProfiles("test")
class AutenticacaoTests {
    @Autowired Environment environment;
    @Autowired UsuarioRepository usuarios;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void cadastraAutenticaRevogaEMantemRotasAbertas() throws Exception {
        assertTrue(roles.findByAuthority("ROLE_ADMIN").isPresent());
        assertTrue(roles.findByAuthority("ROLE_USER").isPresent());
        String cadastro = """
            {"nome":"Maria","sobrenome":"Silva","email":"maria@example.com","senha":"Usuario123!"}
            """;
        var criado = enviar("POST", "/usuarios", cadastro, "application/json", null);
        assertEquals(201, criado.statusCode(), criado.body());
        assertFalse(criado.body().contains("senha"));
        assertEquals(java.util.List.of("ROLE_USER"), JsonPath.read(criado.body(), "$.roles"));
        var usuario = usuarios.findByEmail("maria@example.com").orElseThrow();
        assertNotEquals("Usuario123!", usuario.getSenha());
        assertTrue(encoder.matches("Usuario123!", usuario.getSenha()));
        assertEquals(409, enviar("POST", "/usuarios", cadastro, "application/json", null).statusCode());
        assertEquals(200, enviar("GET", "/usuarios", null, null, null).statusCode());
        assertEquals(200, enviar("GET", "/jogadores", null, null, null).statusCode());
        assertEquals(201, enviar("POST", "/jogadores", "{\"nome\":\"Jogador aberto\"}", "application/json", null).statusCode());

        var login = login("Usuario123!", "segredo");
        assertEquals(200, login.statusCode(), login.body());
        String token = JsonPath.read(login.body(), "$.access_token");
        var jwt = decoder.decode(token);
        assertEquals("maria@example.com", jwt.getSubject());
        assertEquals("Maria", jwt.getClaimAsString("nome"));
        assertEquals(java.util.List.of("ROLE_USER"), jwt.getClaimAsStringList("authorities"));
        for (String rota : java.util.List.of("/x1/9223372036854775807", "/campeonatos/9223372036854775807")) {
            assertEquals(401, enviar("DELETE", rota, null, null, null).statusCode());
            assertEquals(403, enviar("DELETE", rota, null, null, "Bearer " + token).statusCode());
        }
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var admin = usuarios.findByEmail("maria@example.com").orElseThrow();
            admin.getRoles().adicionar(roles.findByAuthority("ROLE_ADMIN").orElseThrow());
            usuarios.saveAndFlush(admin);
        });
        var loginAdmin = login("Usuario123!", "segredo");
        assertEquals(200, loginAdmin.statusCode());
        String tokenAdmin = JsonPath.read(loginAdmin.body(), "$.access_token");
        assertTrue(decoder.decode(tokenAdmin).getClaimAsStringList("authorities").contains("ROLE_ADMIN"));
        for (String rota : java.util.List.of("/x1/9223372036854775807", "/campeonatos/9223372036854775807")) {
            // ADMIN chega ao controller e recebe 404 para o recurso inexistente.
            assertEquals(404, enviar("DELETE", rota, null, null, "Bearer " + tokenAdmin).statusCode());
        }
        assertEquals(400, login("errada", "segredo").statusCode());
        assertEquals(401, login("Usuario123!", "errado").statusCode());
        assertEquals(204, enviar("POST", "/auth/logout", "", null, "Bearer " + token).statusCode());
        assertEquals(401, enviar("GET", "/usuarios", null, null, "Bearer " + token).statusCode());
        assertEquals(200, enviar("GET", "/usuarios", null, null, null).statusCode());
        assertEquals(204, enviar("PATCH", "/usuarios/" + usuario.getId() + "/inativar", "", null, null).statusCode());
        assertEquals(400, login("Usuario123!", "segredo").statusCode());
    }

    private HttpResponse<String> login(String senha, String secret) throws Exception {
        String basic = Base64.getEncoder().encodeToString(("teste:" + secret).getBytes(StandardCharsets.UTF_8));
        return enviar("POST", "/oauth2/token", "grant_type=urn:futcompetitivo:params:oauth:grant-type:password&username=maria%40example.com&password=" + senha,
            "application/x-www-form-urlencoded", "Basic " + basic);
    }

    private HttpResponse<String> enviar(String metodo, String path, String corpo, String tipo, String auth) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path));
        if (tipo != null) request.header("Content-Type", tipo);
        if (auth != null) request.header("Authorization", auth);
        request.method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(corpo));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
