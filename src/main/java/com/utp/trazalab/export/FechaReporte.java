package com.utp.trazalab.export;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class FechaReporte {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.of("America/Lima"));

    private FechaReporte() {
    }

    public static String formatear(Instant fecha) {
        return FORMATO.format(fecha);
    }
}
