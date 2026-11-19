package com.utp.trazalab.reportes.controller;

import com.utp.trazalab.reportes.dto.ReportePaginaDto;
import com.utp.trazalab.reportes.dto.ResumenReporteDto;
import com.utp.trazalab.reportes.dto.FallaRecurrenteDto;
import com.utp.trazalab.reportes.export.FormatoExportacion;
import com.utp.trazalab.reportes.export.ExcelExporter;
import com.utp.trazalab.trazabilidad.service.IncidenciaService;
import com.utp.trazalab.reportes.service.ReporteService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
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
public class ReporteController {
    private final ReporteService reportes;
    private final ExcelExporter exportadorExcel;
    private final IncidenciaService incidencias;
    private final Clock reloj;

    public ReporteController(ReporteService reportes, ExcelExporter exportadorExcel, IncidenciaService incidencias, Clock reloj) {
        this.reportes = reportes;
        this.exportadorExcel = exportadorExcel;
        this.incidencias = incidencias;
        this.reloj = reloj;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta, @RequestParam(required = false) String codigoQr, @RequestParam(required = false) Integer tipoFallaId, @RequestParam(defaultValue = "0") int pagina, Model model) {
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
        ReportePaginaDto resultado = new ReportePaginaDto(Page.empty(), new ResumenReporteDto(0, 0, 0, List.of()));
        try {
            resultado = reportes.consultarPagina(fechaDesde, fechaHasta, codigoQr, tipoFallaId, pagina);
        } catch (IllegalArgumentException error) {
            model.addAttribute("errorReporte", error.getMessage());
        }
        model.addAttribute("paginaRecurrencias", resultado.recurrencias());
        model.addAttribute("recurrencias", resultado.recurrencias().getContent());
        model.addAttribute("cantidadPatrones", resultado.resumen().cantidadPatrones());
        model.addAttribute("equiposAfectados", resultado.resumen().equiposAfectados());
        model.addAttribute("cantidadEventos", resultado.resumen().cantidadEventos());
        model.addAttribute("distribucionCategorias", resultado.resumen().distribucionCategorias());
        return "reportes/listado";
    }

    @GetMapping("/exportar/{formato}")
    public ResponseEntity<byte[]> exportar(@PathVariable String formato, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta, @RequestParam(required = false) String codigoQr, @RequestParam(required = false) Integer tipoFallaId) {
        FormatoExportacion formatoExportacion;
        try {
            formatoExportacion = FormatoExportacion.valueOf(formato.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().build();
        }
        List<FallaRecurrenteDto> recurrencias;
        try {
            recurrencias = reportes.consultarRecurrencias(desde, hasta, codigoQr, tipoFallaId);
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
