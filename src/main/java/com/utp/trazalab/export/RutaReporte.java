package com.utp.trazalab.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class RutaReporte {
    private static final ZoneId ZONA_HORARIA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter SUFIJO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
            .withZone(ZONA_HORARIA);

    private RutaReporte() {
    }

    public static Path crear(Path directorio, FormatoExportacion formato, Instant generadoEn) throws IOException {
        Files.createDirectories(directorio);
        String nombreBase = "incidencias_" + SUFIJO.format(generadoEn);
        Path destino = directorio.resolve(nombreBase + "." + formato.extension());
        int consecutivo = 2;
        while (Files.exists(destino)) {
            destino = directorio.resolve(nombreBase + "_" + consecutivo++ + "." + formato.extension());
        }
        return destino;
    }
}
