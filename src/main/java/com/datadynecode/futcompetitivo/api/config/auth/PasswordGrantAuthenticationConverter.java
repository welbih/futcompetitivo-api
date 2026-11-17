package com.datadynecode.futcompetitivo.api.config.auth;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

public class PasswordGrantAuthenticationConverter implements AuthenticationConverter {

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!PasswordGrantAuthenticationToken.GRANT_TYPE.getValue().equals(request.getParameter("grant_type"))) {
            return null;
        }
        required(request, "grant_type");
        String username = required(request, "username");
        String password = required(request, "password");
        String[] scopeValues = request.getParameterValues("scope");
        if (scopeValues != null && scopeValues.length != 1) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        Set<String> scopes = new HashSet<>();
        if (scopeValues != null && StringUtils.hasText(scopeValues[0])) {
            scopes.addAll(Arrays.asList(scopeValues[0].trim().split("\\s+")));
        }
        return new PasswordGrantAuthenticationToken(
            SecurityContextHolder.getContext().getAuthentication(), username, password, scopes);
    }

    private String required(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        if (values == null || values.length != 1 || !StringUtils.hasText(values[0])) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
        }
        return values[0];
    }
}
