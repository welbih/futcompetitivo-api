package com.datadynecode.futcompetitivo.api.config;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import com.datadynecode.futcompetitivo.api.config.auth.TokenAtivoValidator;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

@Configuration
public class JwtConfig {

    @Bean
    @Profile("!prod")
    public RSAKey developmentRsaKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
            .privateKey((RSAPrivateKey) pair.getPrivate()).keyID(UUID.randomUUID().toString()).build();
    }

    @Bean
    @Profile("prod")
    public RSAKey productionRsaKey(@Value("${jwt.jwk}") String jwk) throws Exception {
        RSAKey key = RSAKey.parse(jwk);
        if (!key.isPrivate() || key.size() < 2048 || key.getKeyID() == null) {
            throw new IllegalArgumentException("JWT_JWK deve conter uma chave RSA privada de ao menos 2048 bits e kid");
        }
        return key;
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey key) {
        return new ImmutableJWKSet<>(new JWKSet(key));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey key, @Value("${jwt.issuer}") String issuer,
            @Value("${security.client-id}") String clientId, OAuth2AuthorizationService authorizationService,
            UserDetailsService userDetailsService) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(clientId)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audience invalida", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audienceValidator, new TokenAtivoValidator(authorizationService, userDetailsService)));
        return decoder;
    }
}
