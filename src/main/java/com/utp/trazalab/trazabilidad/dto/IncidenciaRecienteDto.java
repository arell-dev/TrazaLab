package com.utp.trazalab.trazabilidad.dto;

import java.time.Instant;

public record IncidenciaRecienteDto(Integer id, String descripcion, Instant fechaRegistro, String estado) {
}
