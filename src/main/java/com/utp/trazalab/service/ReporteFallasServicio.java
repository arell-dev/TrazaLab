package com.utp.trazalab.service;

import com.utp.trazalab.dto.FallaRecurrenteDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReporteFallasServicio {
    private static final int MAXIMO_DIAS_FILTRO = 30;
    private static final PDType1Font FUENTE = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FUENTE_NEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final JdbcTemplate jdbcTemplate;
    private final Clock reloj;

    public ReporteFallasServicio(JdbcTemplate jdbcTemplate, Clock reloj) {
        this.jdbcTemplate = jdbcTemplate;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public List<FallaRecurrenteDto> consultarRecurrencias(LocalDate desde, LocalDate hasta, String codigoQr, Integer tipoFallaId) {
        validarRango(desde, hasta);
        StringBuilder sql = new StringBuilder("""
                SELECT c.computadora_id, c.codigo_qr, c.numero_serie,
                       cf.nombre AS categoria_falla, tf.tipo_falla_id, tf.nombre AS tipo_falla,
                       COUNT(*) AS frecuencia, MAX(f.fecha_registro) AS ultima_identificacion
                FROM falla f
                JOIN incidencia i ON i.incidencia_id = f.incidencia_id
                JOIN computadora c ON c.computadora_id = i.computadora_id
                JOIN tipo_falla tf ON tf.tipo_falla_id = f.tipo_falla_id
                JOIN categoria_falla cf ON cf.categoria_falla_id = tf.categoria_falla_id
                WHERE f.fecha_registro >= ? AND f.fecha_registro < ?
                """);
        List<Object> parametros = new ArrayList<>();
        parametros.add(desde.atStartOfDay());
        parametros.add(hasta.plusDays(1).atStartOfDay());
        if (codigoQr != null && !codigoQr.isBlank()) {
            sql.append(" AND UPPER(c.codigo_qr) = UPPER(?) ");
            parametros.add(codigoQr.trim());
        }
        if (tipoFallaId != null) {
            sql.append(" AND tf.tipo_falla_id = ? ");
            parametros.add(tipoFallaId);
        }
        sql.append("""
                 GROUP BY c.computadora_id, c.codigo_qr, c.numero_serie,
                          cf.nombre, tf.tipo_falla_id, tf.nombre
                HAVING COUNT(*) >= 2
                ORDER BY frecuencia DESC, c.codigo_qr, tf.nombre
                """);

        return jdbcTemplate.query(sql.toString(), (resultado, numeroFila) -> new FallaRecurrenteDto(
                resultado.getInt("computadora_id"),
                resultado.getString("codigo_qr"),
                resultado.getString("numero_serie"),
                resultado.getString("categoria_falla"),
                resultado.getInt("tipo_falla_id"),
                resultado.getString("tipo_falla"),
                resultado.getLong("frecuencia"),
                resultado.getObject("ultima_identificacion", LocalDateTime.class)),
                parametros.toArray());
    }

    public byte[] exportarCsv(List<FallaRecurrenteDto> recurrencias, LocalDate desde, LocalDate hasta) {
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

    public byte[] exportarPdf(List<FallaRecurrenteDto> recurrencias, LocalDate desde, LocalDate hasta) {
        try (PDDocument documento = new PDDocument(IOUtils.createTempFileOnlyStreamCache());
                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            documento.getDocumentInformation().setTitle("TrazaLab - Fallas recurrentes");
            documento.getDocumentInformation().setAuthor("TrazaLab");
            float ancho = PDRectangle.A4.getWidth();
            float alto = PDRectangle.A4.getHeight();
            PDPage pagina = new PDPage(PDRectangle.A4);
            documento.addPage(pagina);
            PDPageContentStream contenido = new PDPageContentStream(documento, pagina);
            float y = escribirEncabezado(contenido, alto, desde, hasta, false);

            for (FallaRecurrenteDto falla : recurrencias) {
                String fila = falla.codigoQr() + " / " + falla.numeroSerie() + " | "
                        + falla.categoria() + ": " + falla.tipoFalla() + " | "
                        + falla.frecuencia() + " | " + falla.ultimaIdentificacion();
                List<String> lineas = envolver(normalizarPdf(fila), FUENTE, 7.5f, ancho - 80);
                for (String linea : lineas) {
                    if (y < 45) {
                        contenido.close();
                        pagina = new PDPage(PDRectangle.A4);
                        documento.addPage(pagina);
                        contenido = new PDPageContentStream(documento, pagina);
                        y = escribirEncabezado(contenido, alto, desde, hasta, true);
                    }
                    escribir(contenido, linea, 40, y, FUENTE, 7.5f);
                    y -= 13;
                }
                y -= 3;
            }
            if (recurrencias.isEmpty()) {
                escribir(contenido, "No se encontraron fallas recurrentes para los filtros indicados.", 40, y, FUENTE, 9);
            }
            contenido.close();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo generar el PDF de recurrencias.", error);
        }
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw new IllegalArgumentException("Selecciona un periodo válido para el reporte.");
        }
        if (desde.isBefore(hasta.minusDays(MAXIMO_DIAS_FILTRO - 1L))) {
            throw new IllegalArgumentException("El reporte admite periodos de hasta 30 días.");
        }
        if (hasta.isAfter(LocalDate.now(reloj))) {
            throw new IllegalArgumentException("La fecha final no puede ser futura.");
        }
    }

    private String protegerFormula(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        return valor.matches("^[\\t\\r ]*[=+\\-@].*") ? "'" + valor : valor;
    }

    private String normalizarPdf(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\u0020-\\u007E]", "?");
    }

    private float escribirEncabezado(PDPageContentStream contenido, float alto, LocalDate desde, LocalDate hasta, boolean continuacion) throws IOException {
        float y = alto - 48;
        String titulo = continuacion
                ? "TrazaLab | Fallas recurrentes (continuacion)"
                : "TrazaLab | Fallas recurrentes";
        escribir(contenido, titulo, 40, y, FUENTE_NEGRITA, 14);
        y -= 18;
        escribir(contenido, "Periodo: " + desde + " a " + hasta + " | Criterio: dos o mas fallas del mismo tipo por equipo", 40, y, FUENTE, 8);
        y -= 24;
        escribir(contenido, "Equipo / serie | Categoria y tipo | Casos | Ultima fecha", 40, y, FUENTE_NEGRITA, 8);
        return y - 14;
    }

    private List<String> envolver(String texto, PDType1Font fuente, float tamano, float anchoMaximo) throws IOException {
        List<String> lineas = new ArrayList<>();
        StringBuilder linea = new StringBuilder();

        for (String palabra : texto.split("\\s+")) {
            String candidata = linea.isEmpty() ? palabra : linea + " " + palabra;
            if (anchoTexto(candidata, fuente, tamano) <= anchoMaximo) {
                linea.setLength(0);
                linea.append(candidata);
                continue;
            }

            if (!linea.isEmpty()) {
                lineas.add(linea.toString());
                linea.setLength(0);
            }

            StringBuilder fragmento = new StringBuilder();
            for (int indice = 0; indice < palabra.length(); indice++) {
                String candidato = fragmento.toString() + palabra.charAt(indice);
                if (!fragmento.isEmpty() && anchoTexto(candidato, fuente, tamano) > anchoMaximo) {
                    lineas.add(fragmento.toString());
                    fragmento.setLength(0);
                }
                fragmento.append(palabra.charAt(indice));
            }
            linea.append(fragmento);
        }

        if (!linea.isEmpty()) {
            lineas.add(linea.toString());
        }
        return lineas;
    }

    private float anchoTexto(String texto, PDType1Font fuente, float tamano) throws IOException {
        return fuente.getStringWidth(texto) * tamano / 1000;
    }

    private void escribir(PDPageContentStream contenido, String texto, float x, float y, PDType1Font fuente, float tamano) throws IOException {
        contenido.beginText();
        contenido.setFont(fuente, tamano);
        contenido.newLineAtOffset(x, y);
        contenido.showText(texto);
        contenido.endText();
    }
}
