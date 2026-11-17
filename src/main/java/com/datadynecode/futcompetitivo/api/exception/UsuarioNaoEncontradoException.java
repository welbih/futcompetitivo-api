package com.datadynecode.futcompetitivo.api.exception;

public class UsuarioNaoEncontradoException extends RuntimeException {
    public UsuarioNaoEncontradoException() {
        super("Usuario nao encontrado");
    }
}
