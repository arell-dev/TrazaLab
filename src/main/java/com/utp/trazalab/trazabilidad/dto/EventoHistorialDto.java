package com.utp.trazalab.trazabilidad.dto;

import java.time.LocalDateTime;

public record EventoHistorialDto(Long id, String tipo, String titulo, String descripcion, String estado, LocalDateTime fecha) {
}
