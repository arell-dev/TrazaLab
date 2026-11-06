package com.utp.trazalab.controller;

import com.utp.trazalab.dto.ArchivoExportadoDto;
import com.utp.trazalab.export.FormatoExportacion;
import com.utp.trazalab.service.ExportacionIncidenciasServicio;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/incidencias/exportar")
public class ExportacionWebController {
    private final ExportacionIncidenciasServicio servicio;

    public ExportacionWebController(ExportacionIncidenciasServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{formato}")
    public ResponseEntity<Resource> exportar(@PathVariable FormatoExportacion formato) throws IOException {
        ArchivoExportadoDto archivo = servicio.exportar(formato);
        Path ruta = Path.of(archivo.ruta());
        FileSystemResource recurso = new FileSystemResource(ruta);
        MediaType tipo = switch (formato) {
            case PDF -> MediaType.APPLICATION_PDF;
            case CSV -> new MediaType("text", "csv", StandardCharsets.UTF_8);
        };
        ContentDisposition disposicion = ContentDisposition.attachment()
                .filename(archivo.nombreArchivo(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .contentLength(recurso.contentLength())
                .body(recurso);
    }
}
