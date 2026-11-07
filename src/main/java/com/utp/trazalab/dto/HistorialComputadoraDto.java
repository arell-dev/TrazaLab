package com.utp.trazalab.dto;

import org.springframework.data.domain.Page;

public record HistorialComputadoraDto(Integer computadoraId, String codigoQr, String numeroSerie, String marca, String modelo, String ubicacion, String estado, Page<IncidenciaRegistradaDto> incidencias) {
}
