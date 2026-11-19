package com.utp.trazalab.reportes.export;

import com.utp.trazalab.reportes.dto.FallaRecurrenteDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

@Component
public class PdfExporter {
    private static final PDType1Font FUENTE = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FUENTE_NEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    public byte[] exportar(List<FallaRecurrenteDto> recurrencias, LocalDate desde, LocalDate hasta) {
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
