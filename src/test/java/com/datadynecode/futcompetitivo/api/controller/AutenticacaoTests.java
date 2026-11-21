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
        for (String rota : java.util.List.of("/x1/9223372036854775807", "/campeonatos/9223372036854775807",
                "/jogadores/9223372036854775807", "/equipes/9223372036854775807")) {
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
        for (String rota : java.util.List.of("/x1/9223372036854775807", "/campeonatos/9223372036854775807",
                "/jogadores/9223372036854775807", "/equipes/9223372036854775807")) {
            // ADMIN chega ao controller e recebe 404 para o recurso inexistente.
            assertEquals(404, enviar("DELETE", rota, null, null, "Bearer " + tokenAdmin).statusCode());
        }
        for (String recurso : java.util.List.of("jogadores", "equipes")) {
            String corpo = recurso.equals("jogadores") ? "{\"nome\":\"Jogador restrito\"}"
                    : "{\"nome\":\"Equipe restrita\",\"logoUrl\":\"https://example.com/logo.png\"}";
            var cadastroRestrito = enviar("POST", "/" + recurso, corpo, "application/json", null);
            assertEquals(201, cadastroRestrito.statusCode(), cadastroRestrito.body());
            Number id = JsonPath.read(cadastroRestrito.body(), "$.id");
            String rota = "/" + recurso + "/" + id.longValue();
            String edicao = recurso.equals("jogadores") ? "{\"nome\":\"Jogador editado\",\"apelido\":\"Novo\"}"
                    : "{\"nome\":\"Equipe editada\",\"logoUrl\":\"https://example.com/nova.png\"}";
            assertEquals(401, enviar("PUT", rota, edicao, "application/json", null).statusCode());
            assertEquals(recurso.equals("jogadores") ? "Jogador restrito" : "Equipe restrita",
                    JsonPath.read(enviar("GET", rota, null, null, null).body(), "$.nome"));
            assertEquals(200, enviar("PUT", rota, edicao, "application/json", "Bearer " + token).statusCode());
            assertEquals(recurso.equals("jogadores") ? "Jogador editado" : "Equipe editada",
                    JsonPath.read(enviar("GET", rota, null, null, null).body(), "$.nome"));
            assertEquals(200, enviar("PUT", rota, corpo, "application/json", "Bearer " + tokenAdmin).statusCode());
            assertEquals(401, enviar("DELETE", rota, null, null, null).statusCode());
            assertEquals(403, enviar("DELETE", rota, null, null, "Bearer " + token).statusCode());
            assertEquals(200, enviar("GET", rota, null, null, null).statusCode());
            assertEquals(204, enviar("DELETE", rota, null, null, "Bearer " + tokenAdmin).statusCode());
            assertEquals(404, enviar("GET", rota, null, null, null).statusCode());
            assertEquals(401, enviar("PUT", rota + "/ativar", null, null, null).statusCode());
            assertEquals(403, enviar("PUT", rota + "/ativar", null, null, "Bearer " + token).statusCode());
            assertEquals(404, enviar("GET", rota, null, null, null).statusCode());
            assertEquals(200, enviar("PUT", rota + "/ativar", null, null, "Bearer " + tokenAdmin).statusCode());
            assertEquals(200, enviar("GET", rota, null, null, null).statusCode());
        }
        verificarPermissoesOrganizacao(token, tokenAdmin);
        verificarPermissoesGruposManuais(token, tokenAdmin);
        assertEquals(400, login("errada", "segredo").statusCode());
        assertEquals(401, login("Usuario123!", "errado").statusCode());
        assertEquals(204, enviar("POST", "/auth/logout", "", null, "Bearer " + token).statusCode());
        assertEquals(401, enviar("GET", "/usuarios", null, null, "Bearer " + token).statusCode());
        assertEquals(200, enviar("GET", "/usuarios", null, null, null).statusCode());
        assertEquals(204, enviar("PATCH", "/usuarios/" + usuario.getId() + "/inativar", "", null, null).statusCode());
        assertEquals(400, login("Usuario123!", "segredo").statusCode());
    }

    private void verificarPermissoesGruposManuais(String tokenUser, String tokenAdmin) throws Exception {
        var organizacao = enviar("POST", "/organizacoes", "{\"nome\":\"Grupos historicos\",\"ativo\":true}",
                "application/json", "Bearer " + tokenUser);
        assertEquals(201, organizacao.statusCode());
        Number organizacaoId = JsonPath.read(organizacao.body(), "$.id");
        String cadastro = "{\"organizacaoId\":" + organizacaoId + ",\"edicao\":2020,\"participantes\":["
                + "{\"jogadorId\":1,\"equipeId\":1},{\"jogadorId\":2,\"equipeId\":2},"
                + "{\"jogadorId\":3,\"equipeId\":3},{\"jogadorId\":4,\"equipeId\":4}]}";
        var copaManual = enviar("POST", "/campeonatos", cadastro, "application/json", "Bearer " + tokenAdmin);
        assertEquals(201,copaManual.statusCode(),copaManual.body());
        Number copaManualId = JsonPath.read(copaManual.body(),"$.id");
        java.util.List<Number> manuais = JsonPath.read(copaManual.body(),"$.participantes[*].id");
        var ligaAntiga=enviar("POST","/campeonatos",cadastro,"application/json","Bearer "+tokenAdmin);
        assertEquals(201,ligaAntiga.statusCode(),ligaAntiga.body());
        Number ligaId=JsonPath.read(ligaAntiga.body(),"$.id");
        java.util.List<Number> participantesLiga=JsonPath.read(ligaAntiga.body(),"$.participantes[*].id");
        int[][][] agendaLiga={{{0,1},{2,3}},{{0,2},{1,3}},{{0,3},{1,2}}};
        var rodadasLiga=java.util.Arrays.stream(agendaLiga).map(r -> new com.datadynecode.futcompetitivo.api.dto.IniciarLigaParcialManualRequest.RodadaManual(
                java.util.Arrays.stream(r).map(p -> new com.datadynecode.futcompetitivo.api.dto.ConfrontoInicialRequest(
                        participantesLiga.get(p[0]).longValue(),participantesLiga.get(p[1]).longValue())).toList())).toList();
        var configuracaoLiga=new com.datadynecode.futcompetitivo.api.dto.IniciarCampeonatoRequest(
                com.datadynecode.futcompetitivo.api.entity.TipoFormato.MISTA_PARCIAL,false,3,null,4);
        String corpoLiga=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(
                new com.datadynecode.futcompetitivo.api.dto.IniciarLigaParcialManualRequest(configuracaoLiga,rodadasLiga));
        String rotaLiga="/campeonatos/"+ligaId+"/iniciar/liga-parcial/manual";
        assertEquals(401,enviar("POST",rotaLiga,corpoLiga,"application/json",null).statusCode());
        assertEquals(403,enviar("POST",rotaLiga,corpoLiga,"application/json","Bearer "+tokenUser).statusCode());
        assertEquals(400,enviar("POST",rotaLiga,"{}","application/json","Bearer "+tokenAdmin).statusCode());
        var inicioLiga=enviar("POST",rotaLiga,corpoLiga,"application/json","Bearer "+tokenAdmin);
        assertEquals(200,inicioLiga.statusCode(),inicioLiga.body());
        assertEquals(3,(int)JsonPath.read(inicioLiga.body(),"$.fases[0].jogosPorParticipante"));
        assertEquals(3,(int)JsonPath.read(inicioLiga.body(),"$.fases[0].totalRodadas"));
        assertEquals(400,enviar("POST",rotaLiga,corpoLiga,"application/json","Bearer "+tokenAdmin).statusCode());
        String rotaManual = "/campeonatos/"+copaManualId+"/iniciar/eliminatorio/manual";
        String corpoManual = "{\"confrontosPrimeiraRodada\":[{\"mandanteId\":"+manuais.get(2)+",\"visitanteId\":"+manuais.get(0)
                +"},{\"mandanteId\":"+manuais.get(1)+",\"visitanteId\":"+manuais.get(3)+"}]}";
        assertEquals(401,enviar("POST",rotaManual,corpoManual,"application/json",null).statusCode());
        assertEquals(403,enviar("POST",rotaManual,corpoManual,"application/json","Bearer "+tokenUser).statusCode());
        assertEquals(400,enviar("POST",rotaManual,"{}","application/json","Bearer "+tokenAdmin).statusCode());
        var chaveManual = enviar("POST",rotaManual,corpoManual,"application/json","Bearer "+tokenAdmin);
        assertEquals(200,chaveManual.statusCode(),chaveManual.body());
        assertEquals(manuais.get(2).longValue(),((Number)JsonPath.read(chaveManual.body(),"$.fases[0].rodadas[0].partidas[0].mandante.id")).longValue());
        assertEquals(400,enviar("POST",rotaManual,corpoManual,"application/json","Bearer "+tokenAdmin).statusCode());
        Number extraUm=JsonPath.read(enviar("POST","/jogadores","{\"nome\":\"Extra um\"}","application/json",null).body(),"$.id");
        Number extraDois=JsonPath.read(enviar("POST","/jogadores","{\"nome\":\"Extra dois\"}","application/json",null).body(),"$.id");
        cadastro=cadastro.substring(0,cadastro.length()-2)+",{\"jogadorId\":"+extraUm+",\"equipeId\":1},"
                +"{\"jogadorId\":"+extraDois+",\"equipeId\":2}]}";
        var criado = enviar("POST", "/campeonatos", cadastro, "application/json", "Bearer " + tokenAdmin);
        assertEquals(201, criado.statusCode(), criado.body());
        Number campeonatoId = JsonPath.read(criado.body(), "$.id");
        java.util.List<Number> participantes = JsonPath.read(criado.body(), "$.participantes[*].id");
        var ids = participantes.stream().map(Number::longValue).toList();
        var dados = new com.datadynecode.futcompetitivo.api.dto.IniciarGruposManuaisRequest(
                ids.subList(0,3),ids.subList(3,6),4,false,false);
        String corpo = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(dados);
        String rota = "/campeonatos/" + campeonatoId + "/iniciar/grupos";
        assertEquals(401, enviar("POST", rota, corpo, "application/json", null).statusCode());
        assertEquals(403, enviar("POST", rota, corpo, "application/json", "Bearer " + tokenUser).statusCode());
        assertEquals("CRIADO", JsonPath.read(enviar("GET", "/campeonatos/" + campeonatoId, null, null, null).body(), "$.status"));
        assertEquals(400, enviar("POST", rota, "{}", "application/json", "Bearer " + tokenAdmin).statusCode());
        var iniciado = enviar("POST", rota, corpo, "application/json", "Bearer " + tokenAdmin);
        assertEquals(200, iniciado.statusCode(), iniciado.body());
        assertEquals("EM_ANDAMENTO", JsonPath.read(iniciado.body(), "$.status"));
        java.util.List<Number> grupoA = JsonPath.read(iniciado.body(), "$.fases[0].grupos[0].participantes[*].id");
        java.util.List<Number> grupoB = JsonPath.read(iniciado.body(), "$.fases[0].grupos[1].participantes[*].id");
        assertEquals(dados.grupoA(), grupoA.stream().map(Number::longValue).toList());
        assertEquals(dados.grupoB(), grupoB.stream().map(Number::longValue).toList());
        assertEquals(400, enviar("POST", rota, corpo, "application/json", "Bearer " + tokenAdmin).statusCode());
        String base = "/campeonatos/" + campeonatoId;
        for (String acao : java.util.List.of("/mata-mata", "/rodadas/9223372036854775807/iniciar",
                "/participantes/9223372036854775807/desistir")) {
            assertEquals(401,enviar("POST",base+acao,null,null,null).statusCode());
            assertEquals(403,enviar("POST",base+acao,null,null,"Bearer "+tokenUser).statusCode());
        }
        assertEquals(401,enviar("GET",base+"/mata-mata/vagas",null,null,null).statusCode());
        assertEquals(403,enviar("GET",base+"/mata-mata/vagas",null,null,"Bearer "+tokenUser).statusCode());
        assertEquals(400,enviar("POST",base+"/mata-mata",null,null,"Bearer "+tokenAdmin).statusCode());
        java.util.List<Number> jogosGrupo = JsonPath.read(iniciado.body(),"$.fases[0].rodadas[*].partidas[*].id");
        var parcial=enviar("PUT",base+"/partidas/"+jogosGrupo.getFirst()+"/resultado",
                "{\"golsMandante\":1,\"golsVisitante\":0}","application/json","Bearer "+tokenAdmin);
        assertEquals(200,parcial.statusCode(),parcial.body());
        assertEquals(Boolean.TRUE,JsonPath.read(parcial.body(),"$.fases[0].grupos[0].classificacaoAtual.parcial"));
        assertTrue(((java.util.List<?>)JsonPath.read(parcial.body(),"$.fases[0].grupos[0].classificacaoPorRodada")).isEmpty());
        for (Number partida : jogosGrupo) assertEquals(200,enviar("PUT",base+"/partidas/"+partida+"/resultado",
                "{\"golsMandante\":1,\"golsVisitante\":0}","application/json","Bearer "+tokenAdmin).statusCode());
        var aguardando = enviar("GET",base,null,null,null);
        assertEquals(Boolean.TRUE,JsonPath.read(aguardando.body(),"$.aguardandoMataMata"));
        var mataMata = enviar("POST",base+"/mata-mata",null,null,"Bearer "+tokenAdmin);
        assertEquals(200,mataMata.statusCode(),mataMata.body());
        assertEquals(400,enviar("POST",base+"/mata-mata",null,null,"Bearer "+tokenAdmin).statusCode());
        java.util.List<Number> semifinais = JsonPath.read(mataMata.body(),"$.fases[1].rodadas[0].partidas[*].id");
        Number rodadaFinal = JsonPath.read(mataMata.body(),"$.fases[1].rodadas[1].id");
        assertEquals(400,enviar("POST",base+"/rodadas/"+rodadaFinal+"/iniciar",null,null,"Bearer "+tokenAdmin).statusCode());
        for (Number partida : semifinais) assertEquals(200,enviar("PUT",base+"/partidas/"+partida+"/resultado",
                "{\"golsMandante\":1,\"golsVisitante\":0}","application/json","Bearer "+tokenAdmin).statusCode());
        var finalAberta = enviar("GET",base,null,null,"Bearer "+tokenAdmin);
        assertEquals(200,finalAberta.statusCode(),finalAberta.body());
        Number desistente = JsonPath.read(finalAberta.body(),"$.fases[1].rodadas[1].partidas[0].mandante.id");
        var desistencia = enviar("POST",base+"/participantes/"+desistente+"/desistir",null,null,"Bearer "+tokenAdmin);
        assertEquals(200,desistencia.statusCode(),desistencia.body());
        assertEquals(Boolean.TRUE,JsonPath.read(desistencia.body(),"$.fases[1].rodadas[1].partidas[0].wo"));
        assertEquals(3,(int)JsonPath.read(desistencia.body(),"$.fases[1].rodadas[1].partidas[0].resultado.golsVisitante"));
    }

    private void verificarPermissoesOrganizacao(String tokenUser, String tokenAdmin) throws Exception {
        for (boolean multipart : java.util.List.of(false, true)) {
            String tipo = multipart ? "multipart/form-data; boundary=teste-organizacao" : "application/json";
            String dados = "{\"nome\":\"Organização restrita\",\"ativo\":true}";
            String corpo = multipart ? "--teste-organizacao\r\nContent-Disposition: form-data; name=\"dados\"\r\nContent-Type: application/json\r\n\r\n"
                    + dados + "\r\n--teste-organizacao\r\nContent-Disposition: form-data; name=\"trofeu\"; filename=\"trofeu.svg\"\r\nContent-Type: image/svg+xml\r\n\r\n"
                    + "<svg xmlns='http://www.w3.org/2000/svg'/>\r\n--teste-organizacao--\r\n" : dados;
            assertEquals(401, enviar("POST", "/organizacoes", corpo, tipo, null).statusCode());
            var criada = enviar("POST", "/organizacoes", corpo, tipo, "Bearer " + tokenUser);
            assertEquals(201, criada.statusCode(), criada.body());
            assertEquals(201, enviar("POST", "/organizacoes", corpo, tipo, "Bearer " + tokenAdmin).statusCode());
            Number id = JsonPath.read(criada.body(), "$.id");
            String rota = "/organizacoes/" + id.longValue();
            assertEquals(200, enviar("GET", "/organizacoes", null, null, null).statusCode());
            assertEquals(200, enviar("GET", rota, null, null, null).statusCode());
            String edicao = corpo.replace("Organização restrita", "Organização editada");
            assertEquals(401, enviar("PUT", rota, edicao, tipo, null).statusCode());
            assertEquals(403, enviar("PUT", rota, edicao, tipo, "Bearer " + tokenUser).statusCode());
            assertEquals("Organização restrita", JsonPath.read(enviar("GET", rota, null, null, null).body(), "$.nome"));
            assertEquals(200, enviar("PUT", rota, edicao, tipo, "Bearer " + tokenAdmin).statusCode());
            assertEquals("Organização editada", JsonPath.read(enviar("GET", rota, null, null, null).body(), "$.nome"));
            assertEquals(401, enviar("DELETE", rota, null, null, null).statusCode());
            assertEquals(403, enviar("DELETE", rota, null, null, "Bearer " + tokenUser).statusCode());
            assertEquals(Boolean.TRUE, JsonPath.read(enviar("GET", rota, null, null, null).body(), "$.ativo"));
            assertEquals(204, enviar("DELETE", rota, null, null, "Bearer " + tokenAdmin).statusCode());
            assertEquals(Boolean.FALSE, JsonPath.read(enviar("GET", rota, null, null, null).body(), "$.ativo"));
        }
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
