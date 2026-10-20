package com.utp.trazalab.database.converter;

import com.utp.trazalab.model.EstadoComputadora;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EstadoComputadoraJpaConverter implements AttributeConverter<EstadoComputadora, String> {
    @Override
    public String convertToDatabaseColumn(EstadoComputadora estado) {
        return estado == null ? null : estado.etiqueta();
    }

    @Override
    public EstadoComputadora convertToEntityAttribute(String valor) {
        if (valor == null) {
            return null;
        }
        return switch (valor) {
            case "operativa" -> EstadoComputadora.OPERATIVA;
            case "en mantenimiento" -> EstadoComputadora.EN_MANTENIMIENTO;
            case "fuera de servicio" -> EstadoComputadora.FUERA_DE_SERVICIO;
            default -> throw new IllegalArgumentException("Estado de computadora no reconocido: " + valor);
        };
    }
}
