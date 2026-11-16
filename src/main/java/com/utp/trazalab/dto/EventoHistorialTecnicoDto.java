package com.utp.trazalab.dto;

import java.time.LocalDateTime;

public record EventoHistorialTecnicoDto(Long id, String tipo, String titulo, String descripcion, String estado, LocalDateTime fecha) {
}
