package com.utp.trazalab.export;

import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public interface ExportadorIncidencias {
    FormatoExportacion formato();

    Path exportar(List<IncidenciaRegistradaDto> incidencias, Path directorio, Instant generadoEn) throws IOException;
}
