package com.utp.trazalab.database.converter;

import com.utp.trazalab.model.EstadoIncidencia;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EstadoIncidenciaJpaConverter implements AttributeConverter<EstadoIncidencia, String> {
    @Override
    public String convertToDatabaseColumn(EstadoIncidencia estado) {
        return estado == null ? null : estado.name().toLowerCase();
    }

    @Override
    public EstadoIncidencia convertToEntityAttribute(String valor) {
        return valor == null ? null : EstadoIncidencia.valueOf(valor.toUpperCase());
    }
}
