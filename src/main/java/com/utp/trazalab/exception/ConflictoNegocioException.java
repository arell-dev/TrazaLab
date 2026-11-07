package com.utp.trazalab.exception;

/**
 * Indica que una operación válida por formato entra en conflicto con las reglas del dominio.
 */
public class ConflictoNegocioException extends IllegalArgumentException {
    public ConflictoNegocioException(String mensaje) {
        super(mensaje);
    }
}
