package com.utp.trazalab.reportes.export;

import com.utp.trazalab.reportes.dto.FallaRecurrenteDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

@Component
public class CsvExporter {
    public byte[] exportar(List<FallaRecurrenteDto> recurrencias, LocalDate desde, LocalDate hasta) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(salida, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            try (CSVPrinter csv = new CSVPrinter(writer, CSVFormat.RFC4180)) {
                csv.printRecord("Código QR", "Serie", "Categoría", "Tipo de falla", "Frecuencia", "Última identificación");
                for (FallaRecurrenteDto falla : recurrencias) {
                    csv.printRecord(
                            protegerFormula(falla.codigoQr()),
                            protegerFormula(falla.numeroSerie()),
                            protegerFormula(falla.categoria()),
                            protegerFormula(falla.tipoFalla()),
                            falla.frecuencia(),
                            falla.ultimaIdentificacion());
                }
            }
            return salida.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo generar el CSV de recurrencias.", error);
        }
    }


    private String protegerFormula(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        return valor.matches("^[\\t\\r ]*[=+\\-@].*") ? "'" + valor : valor;
    }

}
