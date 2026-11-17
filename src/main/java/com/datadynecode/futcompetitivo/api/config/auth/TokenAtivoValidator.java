package com.datadynecode.futcompetitivo.api.config.auth;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Objects;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

public class TokenAtivoValidator implements OAuth2TokenValidator<Jwt> {
    private final OAuth2AuthorizationService authorizationService;
    private final UserDetailsService userDetailsService;

    public TokenAtivoValidator(OAuth2AuthorizationService authorizationService,
            UserDetailsService userDetailsService) {
        this.authorizationService = authorizationService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        OAuth2Authorization authorization = authorizationService.findByToken(jwt.getTokenValue(), OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || authorization.getAccessToken() == null
                || !authorization.getAccessToken().isActive()
                || !Objects.equals(authorization.getPrincipalName(), jwt.getSubject())) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token invalido ou inativo", null));
        }
        try {
            if (userDetailsService.loadUserByUsername(jwt.getSubject()).isEnabled()) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (UsernameNotFoundException exception) {
            // Tokens de usuarios removidos tambem deixam de ser aceitos.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Usuario inativo ou inexistente", null));
    }
}
