package com.datadynecode.futcompetitivo.api.service;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class TrofeuImagemServiceTests {
    private final TrofeuImagemService service = new TrofeuImagemService();

    @Test
    void aceitaPngESvg() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1, 2), "png", output);
        assertArrayEquals(output.toByteArray(), service.validar(output.toByteArray(), "image/png").conteudo());
        byte[] svg = svg("<path d='M0 0 L10 10'/>");
        assertArrayEquals(svg, service.validar(svg, "image/svg+xml").conteudo());
    }

    @Test
    void aceitaTrofeuVilaComPngEmbutido() throws Exception {
        try (var input = getClass().getResourceAsStream("/imagens/logo-trofeu-vila.svg")) {
            assertNotNull(input);
            byte[] arquivo = input.readAllBytes();
            assertArrayEquals(arquivo, service.validar(arquivo, "image/svg+xml").conteudo());
        }
    }

    @Test
    void rejeitaImagensEmbutidasInvalidasOuAtivas() {
        for (String href : java.util.List.of("https://example.com/imagem.png", "file:///imagem.png",
                "data:image/svg+xml;base64,PHN2Zy8+", "data:text/html;base64,PHNjcmlwdC8+",
                "data:image/png;base64,invalido!", "data:image/png;base64,dGV4dG8=")) {
            assertThrows(ResponseStatusException.class, () -> service.validar(svg("<image href='" + href + "'/>"), "image/svg+xml"));
        }
        assertThrows(ResponseStatusException.class, () -> service.validar(
                svg("<image href='#imagem' onload='alert(1)'/>"), "image/svg+xml"));
    }

    @Test
    void rejeitaArquivosInvalidosETamanhoExcessivo() {
        assertThrows(ResponseStatusException.class, () -> service.validar(new byte[0], "image/png"));
        assertThrows(ResponseStatusException.class, () -> service.validar(svg(""), "image/jpeg"));
        assertThrows(ResponseStatusException.class, () -> service.validar(svg(""), "image/png"));
        assertThrows(ResponseStatusException.class, () -> service.validar("texto".getBytes(), "image/svg+xml"));
        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> service.validar(new byte[TrofeuImagemService.MAX_BYTES + 1], "image/png")).getStatusCode().value());
    }

    @Test
    void rejeitaConteudoAtivoEReferenciasExternasNoSvg() {
        for (String conteudo : java.util.List.of("<script>alert(1)</script>", "<foreignObject/>",
                "<path onclick='alert(1)'/>", "<use href='https://example.com/a.svg'/>",
                "<path fill='url(https://example.com/a.svg)'/>", "<path style='fill:red'/>",
                "<animate attributeName='href'/>")) {
            assertThrows(ResponseStatusException.class, () -> service.validar(svg(conteudo), "image/svg+xml"));
        }
        byte[] entidade = ("<!DOCTYPE svg [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>"
                + "<svg xmlns='http://www.w3.org/2000/svg'>&xxe;</svg>").getBytes(StandardCharsets.UTF_8);
        assertThrows(ResponseStatusException.class, () -> service.validar(entidade, "image/svg+xml"));
    }

    private byte[] svg(String conteudo) {
        return ("<svg xmlns='http://www.w3.org/2000/svg'>" + conteudo + "</svg>").getBytes(StandardCharsets.UTF_8);
    }
}
