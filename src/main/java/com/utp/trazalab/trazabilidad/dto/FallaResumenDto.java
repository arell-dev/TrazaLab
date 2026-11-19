package com.utp.trazalab.trazabilidad.dto;

import java.time.LocalDateTime;

public record FallaResumenDto(Integer id, String codigoQr, String tipoFalla, String descripcion, LocalDateTime fechaRegistro) {
}
