package com.datadynecode.futcompetitivo.api.config.auth;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);
    private final OAuth2ErrorHttpMessageConverter converter = new OAuth2ErrorHttpMessageConverter();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        SecurityContextHolder.clearContext();
        OAuth2Error error = exception instanceof OAuth2AuthenticationException oauth
            ? oauth.getError() : new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR);
        String reference = UUID.randomUUID().toString();
        // Somente categorias fixas; nunca registra descricao livre, cabecalhos ou credenciais.
        LOGGER.warn("OAuth2 authentication rejected: reference={} reason={}", reference, reason(request, error));
        response.setHeader("X-Auth-Error-Id", reference);
        ServletServerHttpResponse output = new ServletServerHttpResponse(response);
        output.setStatusCode(OAuth2ErrorCodes.INVALID_CLIENT.equals(error.getErrorCode())
            ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST);
        converter.write(new OAuth2Error(error.getErrorCode()), null, output);
    }

    private String reason(HttpServletRequest request, OAuth2Error error) {
        if (Collections.list(request.getHeaders("Authorization")).size() > 1) {
            return "AUTHORIZATION_DUPLICADO";
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return "BASIC_AUTH_AUSENTE_OU_INCORRETO";
        }
        String description = error.getDescription();
        if (description == null) {
            return OAuth2ErrorCodes.INVALID_REQUEST.equals(error.getErrorCode())
                ? "REQUISICAO_OU_BASIC_AUTH_MALFORMADO" : "AUTENTICACAO_REJEITADA";
        }
        return switch (description) {
            case "Client authentication failed: client_id" -> "CLIENT_ID_NAO_ENCONTRADO";
            case "Client authentication failed: client_secret" -> "CLIENT_SECRET_NAO_CONFERE";
            case "Client authentication failed: authentication_method" -> "METODO_CLIENTE_NAO_PERMITIDO";
            case "Client authentication failed: credentials" -> "CREDENCIAL_CLIENTE_AUSENTE";
            case "Client authentication failed: client_secret_expires_at" -> "CLIENT_SECRET_EXPIRADO";
            default -> "AUTENTICACAO_REJEITADA";
        };
    }
}
