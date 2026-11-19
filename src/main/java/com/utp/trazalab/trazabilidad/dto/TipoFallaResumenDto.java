package com.utp.trazalab.trazabilidad.dto;

public record TipoFallaResumenDto(Integer id, String categoria, String nombre) {
    @Override
    public String toString() {
        return categoria + " · " + nombre;
    }
}
