package com.datadynecode.futcompetitivo.api.service;

import java.util.Objects;

import com.datadynecode.futcompetitivo.api.exception.TokenInativoException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogoutService {
    private final OAuth2AuthorizationService authorizationService;

    @Transactional
    public void deslogar(String token, String usuarioAutenticado) {
        OAuth2Authorization authorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null || authorization.getAccessToken() == null
                || !authorization.getAccessToken().isActive()
                || !Objects.equals(authorization.getPrincipalName(), usuarioAutenticado)) {
            throw new TokenInativoException();
        }
        OAuth2AccessToken accessToken = authorization.getAccessToken().getToken();
        OAuth2Authorization revogada = OAuth2Authorization.from(authorization)
            .token(accessToken, metadata -> metadata.put(OAuth2Authorization.Token.INVALIDATED_METADATA_NAME, true))
            .build();
        authorizationService.save(revogada);
    }
}
