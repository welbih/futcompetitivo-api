package com.datadynecode.futcompetitivo.api.exception;

public class EmailJaCadastradoException extends RuntimeException {
    public EmailJaCadastradoException() {
        super("Email ja cadastrado");
    }
}
