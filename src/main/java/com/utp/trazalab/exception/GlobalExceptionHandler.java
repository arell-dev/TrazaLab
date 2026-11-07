package com.utp.trazalab.exception;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Convierte errores de las API REST en respuestas Problem Details consistentes.
 */
@RestControllerAdvice(basePackages = "com.utp.trazalab.controller.api")
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> manejarValidacion(MethodArgumentNotValidException error) {
        Map<String, List<String>> erroresPorCampo = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(errorCampo ->
                erroresPorCampo.computeIfAbsent(errorCampo.getField(), campo -> new ArrayList<>())
                        .add(errorCampo.getDefaultMessage()));

        ProblemDetail problema = crearProblema(
                HttpStatus.BAD_REQUEST,
                "Solicitud inválida",
                "Uno o más campos no cumplen las reglas de validación.");
        problema.setProperty("errores", erroresPorCampo);
        return ResponseEntity.badRequest().body(problema);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> manejarRecursoNoEncontrado(RecursoNoEncontradoException error) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado", error.getMessage());
    }

    @ExceptionHandler(ConflictoNegocioException.class)
    public ResponseEntity<ProblemDetail> manejarConflictoNegocio(ConflictoNegocioException error) {
        return responder(HttpStatus.CONFLICT, "Conflicto de negocio", error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> manejarSolicitudInvalida(IllegalArgumentException error) {
        return responder(HttpStatus.BAD_REQUEST, "Solicitud inválida", error.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejarConflictoPersistencia(DataIntegrityViolationException error) {
        return responder(
                HttpStatus.CONFLICT,
                "Conflicto de persistencia",
                "La operación entra en conflicto con datos existentes.");
    }

    private ResponseEntity<ProblemDetail> responder(HttpStatus estado, String titulo, String detalle) {
        return ResponseEntity.status(estado).body(crearProblema(estado, titulo, detalle));
    }

    private ProblemDetail crearProblema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}
