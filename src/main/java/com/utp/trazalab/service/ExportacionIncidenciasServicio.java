package com.utp.trazalab.service;

import com.utp.trazalab.dto.ArchivoExportadoDto;
import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.export.ExportadorIncidencias;
import com.utp.trazalab.export.FormatoExportacion;
import com.utp.trazalab.logging.EventoAplicacion;
import com.utp.trazalab.logging.LoggingAplicacion;
import com.utp.trazalab.logging.ResultadoEvento;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExportacionIncidenciasServicio {
    private final RegistroIncidenciaPersistenteServicio servicio;
    private final Map<FormatoExportacion, ExportadorIncidencias> exportadores;
    private final LoggingAplicacion logging;
    private final Path directorioEvidencias;
    private final Clock reloj;

    public ExportacionIncidenciasServicio(
            RegistroIncidenciaPersistenteServicio servicio,
            List<ExportadorIncidencias> exportadores,
            LoggingAplicacion logging,
            Clock reloj,
            @Value("${trazalab.export.directorio}") String directorioEvidencias) {
        this.servicio = servicio;
        this.logging = logging;
        this.reloj = reloj;
        this.directorioEvidencias = Path.of(directorioEvidencias).toAbsolutePath().normalize();
        this.exportadores = new EnumMap<>(FormatoExportacion.class);
        exportadores.forEach(exportador -> {
            ExportadorIncidencias anterior = this.exportadores.put(exportador.formato(), exportador);
            if (anterior != null) {
                throw new IllegalStateException("Hay más de un exportador para " + exportador.formato());
            }
        });
    }

    public ArchivoExportadoDto exportar(FormatoExportacion formato) {
        if (formato == null) {
            throw new IllegalArgumentException("Selecciona un formato de exportación.");
        }

        var datosExportacion = servicio.prepararExportacionIncidencias();
        long totalIncidencias = datosExportacion.total();
        Iterable<IncidenciaRegistradaDto> incidencias = datosExportacion.incidencias();
        ExportadorIncidencias exportador = exportadores.get(formato);
        if (exportador == null) {
            throw new IllegalStateException("No hay un exportador configurado para " + formato.etiqueta() + ".");
        }

        try {
            Path archivo = exportador.exportar(
                    incidencias,
                    totalIncidencias,
                    directorioEvidencias,
                    reloj.instant());
            logging.registrar(new EventoAplicacion(
                    "incidencia.exportar", "incidencia", ResultadoEvento.EXITOSO,
                    formato.name() + ":" + archivo.getFileName(), Math.toIntExact(totalIncidencias), null));
            return new ArchivoExportadoDto(
                    formato.etiqueta(), archivo.toString(), archivo.getFileName().toString(), Math.toIntExact(totalIncidencias));
        } catch (IOException | RuntimeException error) {
            logging.registrar(new EventoAplicacion(
                    "incidencia.exportar", "incidencia", ResultadoEvento.ERROR,
                    formato.name(), Math.toIntExact(totalIncidencias), error.getClass().getSimpleName()));
            throw new IllegalStateException("No se pudo generar el reporte " + formato.etiqueta() + ".", error);
        }
    }
}
