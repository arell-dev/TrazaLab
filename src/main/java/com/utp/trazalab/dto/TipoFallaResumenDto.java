package com.utp.trazalab.dto;

public record TipoFallaResumenDto(Integer id, String categoria, String nombre) {
    @Override
    public String toString() {
        return categoria + " · " + nombre;
    }
}
