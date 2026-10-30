package com.umc.pediupatrao.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestControllerAdvice(assignableTypes = DashboardController.class)
public class DashboardExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DashboardExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> tratarResponseStatus(ResponseStatusException exception,
                                                              HttpServletRequest request) {
        String detalhe = exception.getReason() == null
                ? "Erro ao processar a consulta"
                : exception.getReason();
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), detalhe);
        problema.setTitle(titulo(exception.getStatusCode()));
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(exception.getStatusCode()).body(problema);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> tratarAcessoNegado(AccessDeniedException exception,
                                                            HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Acesso negado");
        problema.setTitle("Acesso negado");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> tratarTipoInvalido(MethodArgumentTypeMismatchException exception,
                                                            HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Parametro '" + exception.getName() + "' invalido");
        problema.setTitle("Parametro invalido");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.badRequest().body(problema);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> tratarErroInesperado(Exception exception,
                                                              HttpServletRequest request) {
        log.error("Erro ao processar consulta do dashboard", exception);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno ao processar a consulta");
        problema.setTitle("Erro interno");
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problema);
    }

    private String titulo(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Parametro invalido";
            case 401 -> "Nao autenticado";
            case 403 -> "Acesso negado";
            case 404 -> "Nao encontrado";
            default -> "Erro na requisicao";
        };
    }
}
