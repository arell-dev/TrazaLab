package com.utp.trazalab.exception;

/**
 * Indica que un recurso solicitado no existe o no está disponible en el contexto indicado.
 */
public class RecursoNoEncontradoException extends IllegalArgumentException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
