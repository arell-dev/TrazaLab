package com.utp.trazalab.dto;

import java.util.List;

public record HistorialComputadoraDto(
        Integer computadoraId,
        String codigoQr,
        String numeroSerie,
        String marca,
        String modelo,
        String ubicacion,
        String estado,
        List<IncidenciaRegistradaDto> incidencias) {
}
