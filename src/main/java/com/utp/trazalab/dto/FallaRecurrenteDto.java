package com.utp.trazalab.dto;

import java.time.LocalDateTime;

public record FallaRecurrenteDto(
        Integer computadoraId,
        String codigoQr,
        String numeroSerie,
        String categoria,
        Integer tipoFallaId,
        String tipoFalla,
        long frecuencia,
        LocalDateTime ultimaIdentificacion) {
}
