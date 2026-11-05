package com.utp.trazalab.export;

import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

public interface ExportadorIncidencias {
    FormatoExportacion formato();

    Path exportar(
            Iterable<IncidenciaRegistradaDto> incidencias,
            long totalIncidencias,
            Path directorio,
            Instant generadoEn) throws IOException;
}
