package com.utp.trazalab.dto;

import java.time.LocalDateTime;

public record FallaResumenDto(
        Integer id,
        String codigoQr,
        String tipoFalla,
        String descripcion,
        LocalDateTime fechaRegistro) {
}
