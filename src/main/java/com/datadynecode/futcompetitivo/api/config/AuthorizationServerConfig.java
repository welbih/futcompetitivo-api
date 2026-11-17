package com.datadynecode.futcompetitivo.api.config;

import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import com.datadynecode.futcompetitivo.api.config.auth.PasswordGrantAuthenticationConverter;
import com.datadynecode.futcompetitivo.api.config.auth.PasswordGrantAuthenticationProvider;
import com.datadynecode.futcompetitivo.api.config.auth.PasswordGrantAuthenticationToken;
import com.datadynecode.futcompetitivo.api.config.auth.OAuth2AuthenticationFailureHandler;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.settings.*;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
            AuthenticationManager authenticationManager, OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator, PasswordEncoder passwordEncoder) throws Exception {
        OAuth2AuthenticationFailureHandler failureHandler = new OAuth2AuthenticationFailureHandler();
        http.oauth2AuthorizationServer(server -> {
            http.securityMatcher(server.getEndpointsMatcher());
            server.clientAuthentication(client -> client
                .authenticationProviders(providers -> providers.forEach(provider -> {
                    if (provider instanceof ClientSecretAuthenticationProvider secretProvider) {
                        secretProvider.setPasswordEncoder(passwordEncoder);
                    }
                }))
                .errorResponseHandler(failureHandler));
            server.tokenEndpoint(endpoint -> endpoint
                .errorResponseHandler(failureHandler)
                .accessTokenRequestConverter(new PasswordGrantAuthenticationConverter())
                .authenticationProvider(new PasswordGrantAuthenticationProvider(
                    authenticationManager, authorizationService, tokenGenerator)));
        });
        http.cors(Customizer.withDefaults())
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                (request, response, exception) -> failureHandler.onAuthenticationFailure(request, response,
                    new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT)),
                PathPatternRequestMatcher.withDefaults().matcher("/oauth2/token")))
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            @Value("${security.client-id}") String clientId,
            @Value("${security.client-secret}") String clientSecret,
            @Value("${jwt.duration}") long duration, PasswordEncoder encoder) {
        if (duration <= 0 || clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Client ID, secret e duracao positiva do JWT sao obrigatorios");
        }
        RegisteredClient client = RegisteredClient.withId(clientId)
            .clientId(clientId).clientSecret(encoder.encode(clientSecret))
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(PasswordGrantAuthenticationToken.GRANT_TYPE)
            .scope("api.read").scope("api.write")
            .tokenSettings(TokenSettings.builder().accessTokenTimeToLive(Duration.ofSeconds(duration)).build())
            .build();
        return new InMemoryRegisteredClientRepository(client);
    }

    @Bean
    @Profile("!prod")
    public OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    @Bean
    @Profile("prod")
    public OAuth2AuthorizationService persistentAuthorizationService(JdbcOperations jdbc,
            RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${jwt.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
        JwtGenerator generator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        generator.setJwtCustomizer(context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().claim("authorities", context.getPrincipal().getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .collect(Collectors.toCollection(ArrayList::new)));
                context.getClaims().audience(new ArrayList<>(List.of(context.getRegisteredClient().getClientId())));
                context.getClaims().claim("scope", new ArrayList<>(context.getAuthorizedScopes()));
                if (context.getPrincipal().getPrincipal() instanceof com.datadynecode.futcompetitivo.api.entity.Usuario usuario) {
                    context.getClaims().claim("nome", usuario.getNome());
                    context.getClaims().claim("sobrenome", usuario.getSobrenome());
                }
            }
        });
        return generator;
    }
}
