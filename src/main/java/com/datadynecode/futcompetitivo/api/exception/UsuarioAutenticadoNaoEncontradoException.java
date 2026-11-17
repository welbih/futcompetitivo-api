package com.datadynecode.futcompetitivo.api.exception;

public class UsuarioAutenticadoNaoEncontradoException extends RuntimeException {
    public UsuarioAutenticadoNaoEncontradoException() {
        super("Usuario autenticado nao esta cadastrado");
    }
}
