package com.utp.trazalab.export.csv;

import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.export.ExportadorIncidencias;
import com.utp.trazalab.export.FechaReporte;
import com.utp.trazalab.export.FormatoExportacion;
import com.utp.trazalab.export.RutaReporte;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
public class ExportadorIncidenciasCsv implements ExportadorIncidencias {
    private static final String[] ENCABEZADOS = {
            "ID incidencia", "ID usuario", "Reportante", "Código QR", "Descripción", "Fecha de registro", "Estado"
    };

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.CSV;
    }

    @Override
    public Path exportar(List<IncidenciaRegistradaDto> incidencias, Path directorio, Instant generadoEn) throws IOException {
        Path archivo = RutaReporte.crear(directorio, formato(), generadoEn);
        try (BufferedWriter writer = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            try (CSVPrinter csv = new CSVPrinter(writer, CSVFormat.RFC4180)) {
                csv.printRecord((Object[]) ENCABEZADOS);
                for (IncidenciaRegistradaDto incidencia : incidencias) {
                    csv.printRecord(
                            incidencia.id(),
                            incidencia.usuarioId(),
                            protegerFormula(incidencia.reportante()),
                            protegerFormula(incidencia.codigoQr()),
                            protegerFormula(incidencia.descripcion()),
                            FechaReporte.formatear(incidencia.fechaRegistro()),
                            protegerFormula(incidencia.estado()));
                }
            }
        }
        return archivo;
    }

    private String protegerFormula(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        return valor.matches("^[\\t\\r ]*[=+\\-@].*") ? "'" + valor : valor;
    }
}
