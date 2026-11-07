package com.utp.trazalab.dto;

import java.time.Instant;

public record IncidenciaHistorialRecienteDto(Integer id, String descripcion, Instant fechaRegistro, String estado) {
}
