package com.datadynecode.futcompetitivo.api.controller;

import com.datadynecode.futcompetitivo.api.service.LogoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AutenticacaoController {
    private final LogoutService logoutService;

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deslogar(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new com.datadynecode.futcompetitivo.api.exception.TokenInativoException();
        }
        logoutService.deslogar(jwt.getTokenValue(), jwt.getSubject());
    }
}
