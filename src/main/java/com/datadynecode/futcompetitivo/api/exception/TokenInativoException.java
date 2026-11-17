package com.datadynecode.futcompetitivo.api.exception;

public class TokenInativoException extends RuntimeException {
    public TokenInativoException() { super("Token invalido ou inativo"); }
}
