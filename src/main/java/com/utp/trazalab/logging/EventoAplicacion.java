package com.utp.trazalab.logging;

public record EventoAplicacion(
        String accion,
        String recurso,
        ResultadoEvento resultado,
        String referencia,
        Integer cantidad,
        String detalle) {
}
