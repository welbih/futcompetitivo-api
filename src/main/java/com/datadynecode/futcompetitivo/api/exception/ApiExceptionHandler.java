package com.datadynecode.futcompetitivo.api.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail tratarStatus(ResponseStatusException exception, HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(exception.getStatusCode(),
                exception.getReason() == null ? "Não foi possível processar a solicitação." : exception.getReason());
        registrarRejeicao(request, problema);
        return problema;
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail tratarParteAusente(MissingServletRequestPartException exception, HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parte multipart obrigatória ausente: " + exception.getRequestPartName()
                + ". Envie 'dados' como JSON (application/json) e 'trofeu' como arquivo PNG ou SVG.");
        registrarRejeicao(request, problema);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail tratarJsonInvalido(HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "JSON inválido. Verifique os dados e os tipos dos campos enviados.");
        registrarRejeicao(request, problema);
        return problema;
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail tratarTipoNaoSuportado(HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type não suportado. No upload do troféu, envie multipart/form-data com a parte 'dados' em application/json.");
        registrarRejeicao(request, problema);
        return problema;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tratarTamanhoUpload(HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "O troféu deve ter no máximo 2 MB e a requisição completa no máximo 3 MB.");
        registrarRejeicao(request, problema);
        return problema;
    }

    private void registrarRejeicao(HttpServletRequest request, ProblemDetail problema) {
        LOGGER.warn("Requisição rejeitada: method={} path={} status={} motivo={}",
                request.getMethod(), request.getRequestURI(), problema.getStatus(), problema.getDetail());
    }

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ProblemDetail tratarEmailDuplicado(EmailJaCadastradoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(CadastroInvalidoException.class)
    public ProblemDetail tratarCadastro(CadastroInvalidoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(UsuarioNaoEncontradoException.class)
    public ProblemDetail tratarUsuarioNaoEncontrado(UsuarioNaoEncontradoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({TokenInativoException.class, UsuarioAutenticadoNaoEncontradoException.class})
    public ProblemDetail tratarAutenticacao(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(RegraConfrontoPessoalException.class)
    public ProblemDetail tratarRegraConfrontoPessoal(RegraConfrontoPessoalException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(RegraCampeonatoException.class)
    public ProblemDetail tratarRegraCampeonato(RegraCampeonatoException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacao(MethodArgumentNotValidException exception, HttpServletRequest request) {
        Map<String, String> erros = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(erro -> erros.putIfAbsent(erro.getField(), erro.getDefaultMessage()));

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais atributos são inválidos.");
        problema.setTitle("Erro de validação");
        problema.setProperty("erros", erros);
        LOGGER.warn("Validação rejeitada: method={} path={} campos={}",
                request.getMethod(), request.getRequestURI(), erros.keySet());
        return problema;
    }
}
