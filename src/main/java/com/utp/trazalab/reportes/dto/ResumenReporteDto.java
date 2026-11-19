package com.utp.trazalab.reportes.dto;

import java.util.List;

public record ResumenReporteDto(long cantidadPatrones, long equiposAfectados, long cantidadEventos, List<DistribucionCategoriaDto> distribucionCategorias) {
}
