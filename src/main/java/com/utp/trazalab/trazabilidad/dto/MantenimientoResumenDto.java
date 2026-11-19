package com.utp.trazalab.trazabilidad.dto;

import java.time.LocalDateTime;

public record MantenimientoResumenDto(Integer id, String codigoQr, String reportante, String tipo, String descripcion, LocalDateTime fecha) {
}
