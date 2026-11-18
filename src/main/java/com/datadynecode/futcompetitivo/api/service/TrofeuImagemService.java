package com.datadynecode.futcompetitivo.api.service;

import com.datadynecode.futcompetitivo.api.dto.TrofeuImagem;
import java.io.ByteArrayInputStream;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Element;

@Service
public class TrofeuImagemService {
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public TrofeuImagem validar(byte[] conteudo, String contentType) {
        if (conteudo == null || conteudo.length == 0) throw invalido("Informe a imagem do troféu.");
        if (conteudo.length > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O troféu deve ter no máximo 2 MB.");
        }
        try {
            if ("image/png".equals(contentType)) {
                byte[] assinatura = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
                if (conteudo.length < 8 || !java.util.Arrays.equals(assinatura, java.util.Arrays.copyOf(conteudo, 8))) {
                    throw invalido("Imagem PNG inválida.");
                }
                try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(conteudo))) {
                    var readers = ImageIO.getImageReaders(input);
                    if (!readers.hasNext()) throw invalido("Imagem PNG inválida.");
                    var reader = readers.next();
                    try {
                        reader.setInput(input);
                        if ((long) reader.getWidth(0) * reader.getHeight(0) > 16000000) {
                            throw invalido("A imagem deve ter no máximo 16 milhões de pixels.");
                        }
                        reader.read(0);
                    } finally { reader.dispose(); }
                }
            } else if ("image/svg+xml".equals(contentType)) {
                validarSvg(conteudo);
            } else throw invalido("O troféu deve ser PNG ou SVG.");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalido("Imagem do troféu inválida.");
        }
        return new TrofeuImagem(conteudo, contentType);
    }

    private void validarSvg(byte[] conteudo) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(conteudo));
        if (!"svg".equals(doc.getDocumentElement().getLocalName())
                || !"http://www.w3.org/2000/svg".equals(doc.getDocumentElement().getNamespaceURI())) {
            throw invalido("Imagem SVG inválida.");
        }
        var permitidos = Set.of("svg", "g", "path", "rect", "circle", "ellipse", "line", "polyline",
                "polygon", "text", "tspan", "title", "desc", "defs", "use", "symbol", "clipPath", "mask",
                "linearGradient", "radialGradient", "stop", "pattern", "image");
        var elementos = doc.getElementsByTagName("*");
        for (int i = 0; i < elementos.getLength(); i++) {
            Element elemento = (Element) elementos.item(i);
            if (!permitidos.contains(elemento.getLocalName())
                    || !"http://www.w3.org/2000/svg".equals(elemento.getNamespaceURI())) {
                throw invalido("O SVG deve conter apenas elementos gráficos estáticos.");
            }
            var atributos = elemento.getAttributes();
            for (int j = 0; j < atributos.getLength(); j++) {
                var atributo = atributos.item(j);
                String nome = atributo.getLocalName().toLowerCase(java.util.Locale.ROOT);
                String valor = atributo.getNodeValue().trim();
                if ("style".equals(nome)) {
                    throw invalido("O SVG contém estilo inline (atributo style). Exporte usando atributos de apresentação, como fill e stroke.");
                }
                if ("href".equals(nome) && "image".equals(elemento.getLocalName()) && !valor.startsWith("#")) {
                    validarPngEmbutido(valor);
                    continue;
                }
                if (nome.startsWith("on") || "base".equals(nome)
                        || ("href".equals(nome) && !valor.startsWith("#"))
                        || (valor.toLowerCase(java.util.Locale.ROOT).contains("url(")
                            && !valor.matches("url\\(#[a-zA-Z0-9_-]+\\)"))) {
                    throw invalido("O SVG não pode conter scripts, estilos ou referências externas.");
                }
            }
        }
    }

    private void validarPngEmbutido(String valor) {
        String prefixo = "data:image/png;base64,";
        if (!valor.startsWith(prefixo)) {
            throw invalido("Imagens dentro do SVG devem ser PNG embutido em Base64, sem referências externas.");
        }
        byte[] png;
        try {
            png = java.util.Base64.getDecoder().decode(valor.substring(prefixo.length()).replaceAll("\\s", ""));
        } catch (IllegalArgumentException exception) {
            throw invalido("O SVG contém uma imagem PNG com Base64 inválido.");
        }
        validar(png, "image/png");
    }

    private ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
