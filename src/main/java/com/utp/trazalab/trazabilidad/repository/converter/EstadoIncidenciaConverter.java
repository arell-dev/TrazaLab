package com.utp.trazalab.trazabilidad.repository.converter;

import com.utp.trazalab.trazabilidad.model.EstadoIncidencia;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EstadoIncidenciaConverter implements AttributeConverter<EstadoIncidencia, String> {
    @Override
    public String convertToDatabaseColumn(EstadoIncidencia estado) {
        return estado == null ? null : estado.name().toLowerCase();
    }

    @Override
    public EstadoIncidencia convertToEntityAttribute(String valor) {
        return valor == null ? null : EstadoIncidencia.valueOf(valor.toUpperCase());
    }
}
