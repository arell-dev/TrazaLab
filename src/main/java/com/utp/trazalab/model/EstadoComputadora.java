package com.utp.trazalab.model;

public enum EstadoComputadora {
    OPERATIVA("operativa"),
    EN_MANTENIMIENTO("en mantenimiento"),
    FUERA_DE_SERVICIO("fuera de servicio");

    private final String etiqueta;

    EstadoComputadora(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
