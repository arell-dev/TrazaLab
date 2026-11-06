package com.utp.trazalab.export.excel;

import com.utp.trazalab.dto.FallaRecurrenteDto;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class ExportadorReporteFallasExcel {
    private static final String[] ENCABEZADOS = {
            "ID de equipo", "Código QR", "Número de serie", "Categoría", "Tipo de falla", "Frecuencia", "Última identificación"
    };
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int FILA_ENCABEZADOS = 3;

    public byte[] exportar(List<FallaRecurrenteDto> recurrencias, LocalDate desde, LocalDate hasta) {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream();
                SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            Sheet hoja = workbook.createSheet("Fallas recurrentes");
            CellStyle estiloTitulo = crearEstiloTitulo(workbook);
            CellStyle estiloMetadato = crearEstiloMetadato(workbook);
            CellStyle estiloEncabezado = crearEstiloEncabezado(workbook);
            CellStyle estiloCuerpo = crearEstiloCuerpo(workbook);

            crearCelda(hoja.createRow(0), 0, "TrazaLab | Informe de fallas recurrentes", estiloTitulo);
            crearCelda(hoja.createRow(1), 0, "Periodo: " + desde + " a " + hasta, estiloMetadato);
            crearCelda(hoja.createRow(2), 0, "Criterio: dos o más incidencias del mismo tipo por equipo", estiloMetadato);
            hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, ENCABEZADOS.length - 1));
            hoja.addMergedRegion(new CellRangeAddress(1, 1, 0, ENCABEZADOS.length - 1));
            hoja.addMergedRegion(new CellRangeAddress(2, 2, 0, ENCABEZADOS.length - 1));

            Row filaEncabezado = hoja.createRow(FILA_ENCABEZADOS);
            for (int columna = 0; columna < ENCABEZADOS.length; columna++) {
                crearCelda(filaEncabezado, columna, ENCABEZADOS[columna], estiloEncabezado);
            }

            escribirRecurrencias(hoja, recurrencias, estiloCuerpo);
            configurarHoja(hoja, recurrencias.size());
            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("No se pudo generar el Excel de fallas recurrentes.", error);
        }
    }

    private CellStyle crearEstiloTitulo(Workbook workbook) {
        Font fuente = workbook.createFont();
        fuente.setFontName("Arial");
        fuente.setFontHeightInPoints((short) 15);
        fuente.setBold(true);
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private CellStyle crearEstiloMetadato(Workbook workbook) {
        Font fuente = workbook.createFont();
        fuente.setFontName("Arial");
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private CellStyle crearEstiloEncabezado(Workbook workbook) {
        Font fuente = workbook.createFont();
        fuente.setFontName("Arial");
        fuente.setBold(true);
        fuente.setColor(IndexedColors.WHITE.getIndex());
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
        estilo.setBorderTop(BorderStyle.THIN);
        return estilo;
    }

    private CellStyle crearEstiloCuerpo(Workbook workbook) {
        Font fuente = workbook.createFont();
        fuente.setFontName("Arial");
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuente);
        estilo.setBorderBottom(BorderStyle.THIN);
        estilo.setBorderLeft(BorderStyle.THIN);
        estilo.setBorderRight(BorderStyle.THIN);
        estilo.setBorderTop(BorderStyle.THIN);
        return estilo;
    }

    private void escribirRecurrencias(Sheet hoja, List<FallaRecurrenteDto> recurrencias, CellStyle estiloCuerpo) {
        int numeroFila = FILA_ENCABEZADOS + 1;
        for (FallaRecurrenteDto falla : recurrencias) {
            Row fila = hoja.createRow(numeroFila++);
            crearCelda(fila, 0, falla.computadoraId(), estiloCuerpo);
            crearCelda(fila, 1, falla.codigoQr(), estiloCuerpo);
            crearCelda(fila, 2, falla.numeroSerie(), estiloCuerpo);
            crearCelda(fila, 3, falla.categoria(), estiloCuerpo);
            crearCelda(fila, 4, falla.tipoFalla(), estiloCuerpo);
            crearCelda(fila, 5, falla.frecuencia(), estiloCuerpo);
            crearCelda(fila, 6, FORMATO_FECHA.format(falla.ultimaIdentificacion()), estiloCuerpo);
        }
    }

    private void crearCelda(Row fila, int columna, String valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor == null ? "" : valor);
        celda.setCellStyle(estilo);
    }

    private void crearCelda(Row fila, int columna, int valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        celda.setCellStyle(estilo);
    }

    private void crearCelda(Row fila, int columna, long valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        celda.setCellStyle(estilo);
    }

    private void configurarHoja(Sheet hoja, int cantidadRecurrencias) {
        hoja.createFreezePane(0, FILA_ENCABEZADOS + 1);
        hoja.setColumnWidth(0, 14 * 256);
        hoja.setColumnWidth(1, 20 * 256);
        hoja.setColumnWidth(2, 24 * 256);
        hoja.setColumnWidth(3, 24 * 256);
        hoja.setColumnWidth(4, 32 * 256);
        hoja.setColumnWidth(5, 14 * 256);
        hoja.setColumnWidth(6, 25 * 256);
        if (cantidadRecurrencias > 0) {
            hoja.setAutoFilter(new CellRangeAddress(
                    FILA_ENCABEZADOS,
                    FILA_ENCABEZADOS + cantidadRecurrencias,
                    0,
                    ENCABEZADOS.length - 1));
        }
    }
}
