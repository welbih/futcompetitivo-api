package com.datadynecode.futcompetitivo.api.config.auth;

import java.util.Map;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

public class PasswordGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    public static final AuthorizationGrantType GRANT_TYPE =
        new AuthorizationGrantType("urn:futcompetitivo:params:oauth:grant-type:password");

    private final String username;
    private String password;
    private final Set<String> scopes;

    public PasswordGrantAuthenticationToken(Authentication client, String username, String password, Set<String> scopes) {
        super(GRANT_TYPE, client, Map.of());
        this.username = username;
        this.password = password;
        this.scopes = Set.copyOf(scopes);
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Set<String> getScopes() { return scopes; }

    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        password = null;
    }
}
