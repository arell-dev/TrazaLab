package com.utp.trazalab.trazabilidad.dto;

import java.time.Instant;

public record IncidenciaRegistradaDto(Integer id, Integer usuarioId, String reportante, String codigoQr, String descripcion, Instant fechaRegistro, String estado) {
}
