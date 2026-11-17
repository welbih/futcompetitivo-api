package com.datadynecode.futcompetitivo.api.config.auth;

import java.util.Set;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.*;

public class PasswordGrantAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationManager authenticationManager;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    public PasswordGrantAuthenticationProvider(AuthenticationManager authenticationManager,
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        this.authenticationManager = authenticationManager;
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        PasswordGrantAuthenticationToken grant = (PasswordGrantAuthenticationToken) authentication;
        try {
            if (!(grant.getPrincipal() instanceof OAuth2ClientAuthenticationToken client) || !client.isAuthenticated()) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
            }
            RegisteredClient registeredClient = client.getRegisteredClient();
            if (registeredClient == null || !registeredClient.getAuthorizationGrantTypes().contains(grant.getGrantType())) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
            }
            Set<String> scopes = grant.getScopes().isEmpty() ? registeredClient.getScopes() : grant.getScopes();
            if (!registeredClient.getScopes().containsAll(scopes)) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
            }
            Authentication user;
            try {
                user = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(grant.getUsername(), grant.getPassword()));
            }
            catch (AuthenticationException exception) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
            }
            DefaultOAuth2TokenContext context = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient).principal(user)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(scopes).tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrantType(grant.getGrantType()).authorizationGrant(grant).build();
            OAuth2Token generated = tokenGenerator.generate(context);
            if (!(generated instanceof Jwt jwt)) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
            }
            OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), scopes);
            OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(user.getName()).authorizationGrantType(grant.getGrantType())
                .authorizedScopes(scopes)
                .token(accessToken, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME, jwt.getClaims()))
                .build();
            authorizationService.save(authorization);
            return new OAuth2AccessTokenAuthenticationToken(registeredClient, client, accessToken);
        }
        finally {
            grant.eraseCredentials();
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
