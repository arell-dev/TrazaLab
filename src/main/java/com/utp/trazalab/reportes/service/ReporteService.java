package com.utp.trazalab.reportes.service;

import com.utp.trazalab.reportes.dto.FallaRecurrenteDto;
import com.utp.trazalab.reportes.dto.ReportePaginaDto;
import com.utp.trazalab.reportes.dto.ResumenReporteDto;
import com.utp.trazalab.reportes.export.CsvExporter;
import com.utp.trazalab.reportes.export.PdfExporter;
import com.utp.trazalab.reportes.repository.ReporteRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReporteService {
    private static final int MAXIMO_DIAS_FILTRO = 30;

    private final ReporteRepository reportes;
    private final CsvExporter csvExporter;
    private final PdfExporter pdfExporter;
    private final Clock reloj;

    public ReporteService(ReporteRepository reportes, CsvExporter csvExporter, PdfExporter pdfExporter, Clock reloj) {
        this.reportes = reportes;
        this.csvExporter = csvExporter;
        this.pdfExporter = pdfExporter;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<FallaRecurrenteDto> consultarRecurrencias(LocalDate desde, LocalDate hasta, String codigoQr, Integer tipoFallaId) {
        validarRango(desde, hasta);
        return reportes.consultarRecurrencias(desde, hasta, codigoQr, tipoFallaId);
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ReportePaginaDto consultarPagina(LocalDate desde, LocalDate hasta, String codigoQr, Integer tipoFallaId, int pagina) {
        validarRango(desde, hasta);
        int tamano = 20;
        ResumenReporteDto resumen = reportes.consultarResumen(desde, hasta, codigoQr, tipoFallaId);
        long ultimaPagina = resumen.cantidadPatrones() == 0 ? 0 : (resumen.cantidadPatrones() - 1) / tamano;
        int paginaActual = (int) Math.min(Math.max(0L, pagina), ultimaPagina);
        var contenido = reportes.consultarPagina(desde, hasta, codigoQr, tipoFallaId, paginaActual, tamano);
        return new ReportePaginaDto(new PageImpl<>(contenido, PageRequest.of(paginaActual, tamano), resumen.cantidadPatrones()), resumen);
    }

    public byte[] exportarCsv(List<FallaRecurrenteDto> recurrencias, LocalDate desde, LocalDate hasta) {
        return csvExporter.exportar(recurrencias, desde, hasta);
    }

    public byte[] exportarPdf(List<FallaRecurrenteDto> recurrencias, LocalDate desde, LocalDate hasta) {
        return pdfExporter.exportar(recurrencias, desde, hasta);
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw new IllegalArgumentException("Selecciona un periodo válido para el reporte.");
        }
        if (desde.isBefore(hasta.minusDays(MAXIMO_DIAS_FILTRO - 1L))) {
            throw new IllegalArgumentException("El reporte admite periodos de hasta 30 días.");
        }
        if (hasta.isAfter(LocalDate.now(reloj))) {
            throw new IllegalArgumentException("La fecha final no puede ser futura.");
        }
    }

}
