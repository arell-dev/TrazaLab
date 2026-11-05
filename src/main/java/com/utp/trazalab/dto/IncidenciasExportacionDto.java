package com.utp.trazalab.dto;

public record IncidenciasExportacionDto(long total, Iterable<IncidenciaRegistradaDto> incidencias) {
}
