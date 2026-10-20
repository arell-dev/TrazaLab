package com.utp.trazalab.export.pdf;

import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.export.ExportadorIncidencias;
import com.utp.trazalab.export.FechaReporte;
import com.utp.trazalab.export.FormatoExportacion;
import com.utp.trazalab.export.RutaReporte;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("persistencia")
public class ExportadorIncidenciasPdf implements ExportadorIncidencias {
    private static final float MARGEN = 40;
    private static final float MARGEN_INFERIOR = 40;
    private static final float TAMANO_TEXTO = 8;
    private static final float ALTO_LINEA = 10;
    private static final float[] ANCHOS_COLUMNAS = {42, 42, 95, 70, 280, 130, 95};
    private static final String[] ENCABEZADOS = {
            "ID", "Usuario", "Reportante", "Código QR", "Descripción", "Fecha de registro", "Estado"
    };
    private static final PDType1Font FUENTE = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FUENTE_NEGRITA = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.PDF;
    }

    @Override
    public Path exportar(List<IncidenciaRegistradaDto> incidencias, Path directorio, Instant generadoEn) throws IOException {
        Path archivo = RutaReporte.crear(directorio, formato(), generadoEn);
        List<FilaPdf> filas = incidencias.stream().map(this::crearFila).toList();
        List<List<FilaPdf>> paginas = dividirEnPaginas(filas);
        PDRectangle tamanoPagina = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

        try (PDDocument documento = new PDDocument()) {
            documento.getDocumentInformation().setTitle("TrazaLab - Reporte de incidencias");
            documento.getDocumentInformation().setAuthor("TrazaLab");
            for (int indice = 0; indice < paginas.size(); indice++) {
                PDPage pagina = new PDPage(tamanoPagina);
                documento.addPage(pagina);
                try (PDPageContentStream contenido = new PDPageContentStream(documento, pagina)) {
                    dibujarPagina(contenido, paginas.get(indice), incidencias.size(), generadoEn,
                            indice + 1, paginas.size(), tamanoPagina.getWidth(), tamanoPagina.getHeight());
                }
            }
            documento.save(archivo.toFile());
        }
        return archivo;
    }

    private FilaPdf crearFila(IncidenciaRegistradaDto incidencia) {
        List<String> valores = List.of(
                textoPdf(incidencia.id()),
                textoPdf(incidencia.usuarioId()),
                textoPdf(incidencia.reportante()),
                textoPdf(incidencia.codigoQr()),
                textoPdf(incidencia.descripcion()),
                FechaReporte.formatear(incidencia.fechaRegistro()),
                textoPdf(incidencia.estado()));
        List<List<String>> celdas = new ArrayList<>(valores.size());
        int cantidadLineas = 1;
        for (int columna = 0; columna < valores.size(); columna++) {
            List<String> lineas = envolver(valores.get(columna), ANCHOS_COLUMNAS[columna] - 8, FUENTE, TAMANO_TEXTO);
            celdas.add(lineas);
            cantidadLineas = Math.max(cantidadLineas, lineas.size());
        }
        return new FilaPdf(celdas, cantidadLineas * ALTO_LINEA + 8);
    }

    private List<List<FilaPdf>> dividirEnPaginas(List<FilaPdf> filas) {
        float espacioDisponible = PDRectangle.A4.getWidth() - 106 - MARGEN_INFERIOR;
        List<List<FilaPdf>> paginas = new ArrayList<>();
        List<FilaPdf> paginaActual = new ArrayList<>();
        float altoUsado = 0;
        for (FilaPdf fila : filas) {
            if (!paginaActual.isEmpty() && altoUsado + fila.alto() > espacioDisponible) {
                paginas.add(paginaActual);
                paginaActual = new ArrayList<>();
                altoUsado = 0;
            }
            paginaActual.add(fila);
            altoUsado += fila.alto();
        }
        if (!paginaActual.isEmpty() || paginas.isEmpty()) {
            paginas.add(paginaActual);
        }
        return paginas;
    }

    private void dibujarPagina(
            PDPageContentStream contenido,
            List<FilaPdf> filas,
            int total,
            Instant generadoEn,
            int numeroPagina,
            int totalPaginas,
            float anchoPagina,
            float altoPagina) throws IOException {
        escribirTexto(contenido, "TrazaLab | Reporte de incidencias", MARGEN, altoPagina - 38, FUENTE_NEGRITA, 15);
        escribirTexto(contenido, "Generado: " + FechaReporte.formatear(generadoEn), MARGEN, altoPagina - 55, FUENTE, 9);
        escribirTexto(contenido, "Total de incidencias: " + total, MARGEN, altoPagina - 69, FUENTE, 9);

        float inicioTabla = altoPagina - 82;
        dibujarLinea(contenido, MARGEN, inicioTabla, MARGEN + totalAnchoTabla(), inicioTabla);
        float x = MARGEN;
        float lineaEncabezado = inicioTabla - 15;
        for (int columna = 0; columna < ENCABEZADOS.length; columna++) {
            escribirTexto(contenido, ENCABEZADOS[columna], x + 4, lineaEncabezado, FUENTE_NEGRITA, 7.2f);
            x += ANCHOS_COLUMNAS[columna];
        }
        float y = inicioTabla - 24;
        dibujarLinea(contenido, MARGEN, y, MARGEN + totalAnchoTabla(), y);

        if (filas.isEmpty()) {
            escribirTexto(contenido, "No hay incidencias registradas.", MARGEN + 4, y - 16, FUENTE, TAMANO_TEXTO);
        }

        for (FilaPdf fila : filas) {
            dibujarFila(contenido, fila, y);
            y -= fila.alto();
        }
        dibujarLinea(contenido, MARGEN, y, MARGEN + totalAnchoTabla(), y);
        dibujarLinea(contenido, MARGEN, MARGEN_INFERIOR, anchoPagina - MARGEN, MARGEN_INFERIOR);
        escribirTexto(contenido, "TrazaLab", MARGEN, 25, FUENTE, 7);
        escribirTexto(contenido, "Página " + numeroPagina + " de " + totalPaginas,
                anchoPagina - MARGEN - 78, 25, FUENTE, 7);
    }

    private void dibujarFila(PDPageContentStream contenido, FilaPdf fila, float ySuperior) throws IOException {
        dibujarLinea(contenido, MARGEN, ySuperior, MARGEN + totalAnchoTabla(), ySuperior);
        float x = MARGEN;
        for (int columna = 0; columna < fila.celdas().size(); columna++) {
            List<String> lineas = fila.celdas().get(columna);
            for (int indiceLinea = 0; indiceLinea < lineas.size(); indiceLinea++) {
                escribirTexto(contenido, lineas.get(indiceLinea), x + 4,
                        ySuperior - 13 - indiceLinea * ALTO_LINEA, FUENTE, TAMANO_TEXTO);
            }
            x += ANCHOS_COLUMNAS[columna];
        }
        dibujarLinea(contenido, MARGEN, ySuperior - fila.alto(), MARGEN + totalAnchoTabla(), ySuperior - fila.alto());
    }

    private List<String> envolver(String texto, float anchoMaximo, PDType1Font fuente, float tamano) {
        String normalizado = texto.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalizado.isEmpty()) {
            return List.of("");
        }

        List<String> lineas = new ArrayList<>();
        StringBuilder linea = new StringBuilder();
        for (String palabra : normalizado.split("\\s+")) {
            String candidato = linea.isEmpty() ? palabra : linea + " " + palabra;
            if (anchoTexto(candidato, fuente, tamano) <= anchoMaximo) {
                linea.setLength(0);
                linea.append(candidato);
            } else if (linea.isEmpty()) {
                dividirPalabra(palabra, anchoMaximo, fuente, tamano, lineas, linea);
            } else {
                lineas.add(linea.toString());
                linea.setLength(0);
                if (anchoTexto(palabra, fuente, tamano) <= anchoMaximo) {
                    linea.append(palabra);
                } else {
                    dividirPalabra(palabra, anchoMaximo, fuente, tamano, lineas, linea);
                }
            }
        }
        if (!linea.isEmpty()) {
            lineas.add(linea.toString());
        }
        return lineas;
    }

    private void dividirPalabra(
            String palabra,
            float anchoMaximo,
            PDType1Font fuente,
            float tamano,
            List<String> lineas,
            StringBuilder linea) {
        for (int indice = 0; indice < palabra.length(); indice++) {
            char caracter = palabra.charAt(indice);
            String candidato = linea.toString() + caracter;
            if (anchoTexto(candidato, fuente, tamano) > anchoMaximo && !linea.isEmpty()) {
                lineas.add(linea.toString());
                linea.setLength(0);
            }
            linea.append(caracter);
        }
    }

    private float anchoTexto(String texto, PDType1Font fuente, float tamano) {
        try {
            return fuente.getStringWidth(texto) / 1000 * tamano;
        } catch (IOException error) {
            throw new IllegalArgumentException("El texto contiene un carácter que no admite el PDF.", error);
        }
    }

    private String textoPdf(Object valor) {
        return valor == null ? "" : valor.toString().replaceAll("[^\\u0020-\\u007E\\u00A0-\\u00FF]", "?");
    }

    private void escribirTexto(PDPageContentStream contenido, String texto, float x, float y, PDType1Font fuente, float tamano) throws IOException {
        contenido.beginText();
        contenido.setFont(fuente, tamano);
        contenido.setNonStrokingColor(Color.BLACK);
        contenido.newLineAtOffset(x, y);
        contenido.showText(texto);
        contenido.endText();
    }

    private void dibujarLinea(PDPageContentStream contenido, float inicioX, float y, float finX, float finY) throws IOException {
        contenido.setStrokingColor(Color.BLACK);
        contenido.setLineWidth(0.45f);
        contenido.moveTo(inicioX, y);
        contenido.lineTo(finX, finY);
        contenido.stroke();
    }

    private float totalAnchoTabla() {
        float total = 0;
        for (float ancho : ANCHOS_COLUMNAS) {
            total += ancho;
        }
        return total;
    }

    private record FilaPdf(List<List<String>> celdas, float alto) {
    }
}
