package com.datadynecode.futcompetitivo.api.controller;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import com.datadynecode.futcompetitivo.api.dto.UsuarioPageResponse;

import com.datadynecode.futcompetitivo.api.dto.UsuarioRequest;
import com.datadynecode.futcompetitivo.api.dto.AlterarSenhaRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.datadynecode.futcompetitivo.api.dto.UsuarioResponse;
import com.datadynecode.futcompetitivo.api.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UsuarioController {
    private final UsuarioService usuarioService;

    @GetMapping
    public UsuarioPageResponse listar(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return usuarioService.listar(page, size);
    }

    @PatchMapping("/{id}/inativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void inativar(@PathVariable @Positive Long id) {
        usuarioService.inativar(id);
    }

    @PatchMapping("/{id}/ativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ativar(@PathVariable @Positive Long id) {
        usuarioService.ativar(id);
    }

    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void alterarSenha(@Valid @RequestBody AlterarSenhaRequest request, @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new com.datadynecode.futcompetitivo.api.exception.UsuarioAutenticadoNaoEncontradoException();
        }
        usuarioService.alterarSenha(request, jwt.getSubject());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse cadastrar(@Valid @RequestBody UsuarioRequest request) {
        return usuarioService.cadastrar(request);
    }
}
