package com.utp.trazalab.reportes.dto;

import org.springframework.data.domain.Page;

public record ReportePaginaDto(Page<FallaRecurrenteDto> recurrencias, ResumenReporteDto resumen) {
}
