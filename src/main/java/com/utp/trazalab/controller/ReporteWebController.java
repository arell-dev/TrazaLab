package com.utp.trazalab.controller;

import com.utp.trazalab.dto.DistribucionCategoriaDto;
import com.utp.trazalab.dto.FallaRecurrenteDto;
import com.utp.trazalab.export.FormatoExportacion;
import com.utp.trazalab.export.excel.ExportadorReporteFallasExcel;
import com.utp.trazalab.service.IncidenciaAplicacionServicio;
import com.utp.trazalab.service.ReporteFallasServicio;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/reportes")
public class ReporteWebController {
    private final ReporteFallasServicio reportes;
    private final ExportadorReporteFallasExcel exportadorExcel;
    private final IncidenciaAplicacionServicio incidencias;
    private final Clock reloj;

    public ReporteWebController(ReporteFallasServicio reportes, ExportadorReporteFallasExcel exportadorExcel, IncidenciaAplicacionServicio incidencias, Clock reloj) {
        this.reportes = reportes;
        this.exportadorExcel = exportadorExcel;
        this.incidencias = incidencias;
        this.reloj = reloj;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta, @RequestParam(required = false) String codigoQr, @RequestParam(required = false) Integer tipoFallaId, Model model) {
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate fechaDesde = desde == null ? hoy.minusDays(29) : desde;
        LocalDate fechaHasta = hasta == null ? hoy : hasta;
        model.addAttribute("seccion", "reportes");
        model.addAttribute("titulo", "Reportes de fallas");
        model.addAttribute("desde", fechaDesde);
        model.addAttribute("hasta", fechaHasta);
        model.addAttribute("codigoQr", codigoQr);
        model.addAttribute("tipoFallaId", tipoFallaId);
        model.addAttribute("tiposFalla", incidencias.consultarTiposFalla());
        List<FallaRecurrenteDto> recurrencias = List.of();
        try {
            recurrencias = reportes.consultarRecurrencias(fechaDesde, fechaHasta, codigoQr, tipoFallaId);
        } catch (IllegalArgumentException error) {
            model.addAttribute("errorReporte", error.getMessage());
        }
        long cantidadEventos = recurrencias.stream().mapToLong(FallaRecurrenteDto::frecuencia).sum();
        long equiposAfectados = recurrencias.stream().map(FallaRecurrenteDto::computadoraId).distinct().count();
        model.addAttribute("recurrencias", recurrencias);
        model.addAttribute("cantidadPatrones", recurrencias.size());
        model.addAttribute("equiposAfectados", equiposAfectados);
        model.addAttribute("cantidadEventos", cantidadEventos);
        model.addAttribute("distribucionCategorias", resumirCategorias(recurrencias, cantidadEventos));
        return "reportes/listado";
    }

    private List<DistribucionCategoriaDto> resumirCategorias(List<FallaRecurrenteDto> recurrencias, long totalEventos) {
        if (totalEventos == 0) {
            return List.of();
        }

        Map<String, Long> frecuencias = recurrencias.stream().collect(Collectors.groupingBy(
                FallaRecurrenteDto::categoria,
                LinkedHashMap::new,
                Collectors.summingLong(FallaRecurrenteDto::frecuencia)));
        return frecuencias.entrySet().stream()
                .map(entrada -> new DistribucionCategoriaDto(
                        entrada.getKey(),
                        entrada.getValue(),
                        (int) Math.round(entrada.getValue() * 100.0 / totalEventos)))
                .toList();
    }

    @GetMapping("/exportar/{formato}")
    public ResponseEntity<byte[]> exportar(@PathVariable String formato, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta, @RequestParam(required = false) String codigoQr, @RequestParam(required = false) Integer tipoFallaId) {
        List<FallaRecurrenteDto> recurrencias;
        try {
            recurrencias = reportes.consultarRecurrencias(desde, hasta, codigoQr, tipoFallaId);
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().build();
        }
        FormatoExportacion formatoExportacion;
        try {
            formatoExportacion = FormatoExportacion.valueOf(formato.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().build();
        }
        byte[] contenido = switch (formatoExportacion) {
            case CSV -> reportes.exportarCsv(recurrencias, desde, hasta);
            case EXCEL -> exportadorExcel.exportar(recurrencias, desde, hasta);
            case PDF -> reportes.exportarPdf(recurrencias, desde, hasta);
        };
        MediaType tipoContenido = switch (formatoExportacion) {
            case CSV -> new MediaType("text", "csv", StandardCharsets.UTF_8);
            case EXCEL -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case PDF -> MediaType.APPLICATION_PDF;
        };
        String nombreBase = "trazalab-fallas-recurrentes-" + desde + "-" + hasta;
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(nombreBase + "." + formatoExportacion.extension(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(tipoContenido)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .body(contenido);
    }
}
