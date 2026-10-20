package com.utp.trazalab.export.excel;

import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.export.ExportadorIncidencias;
import com.utp.trazalab.export.FechaReporte;
import com.utp.trazalab.export.FormatoExportacion;
import com.utp.trazalab.export.RutaReporte;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("persistencia")
public class ExportadorIncidenciasExcel implements ExportadorIncidencias {
    private static final String[] ENCABEZADOS = {
            "ID incidencia", "ID usuario", "Reportante", "Código QR", "Descripción", "Fecha de registro", "Estado"
    };
    private static final int FILA_ENCABEZADOS = 4;

    @Override
    public FormatoExportacion formato() {
        return FormatoExportacion.EXCEL;
    }

    @Override
    public Path exportar(List<IncidenciaRegistradaDto> incidencias, Path directorio, Instant generadoEn) throws IOException {
        Path archivo = RutaReporte.crear(directorio, formato(), generadoEn);
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet hoja = workbook.createSheet("Incidencias");
            estilos(workbook, hoja, incidencias, generadoEn);
            escribirFilas(hoja, incidencias);
            configurarHoja(hoja, incidencias.size());
            try (OutputStream salida = Files.newOutputStream(archivo)) {
                workbook.write(salida);
            }
        }
        return archivo;
    }

    private void estilos(XSSFWorkbook workbook, XSSFSheet hoja, List<IncidenciaRegistradaDto> incidencias, Instant generadoEn) {
        Font fuenteNormal = workbook.createFont();
        fuenteNormal.setFontName("Arial");
        fuenteNormal.setColor(IndexedColors.BLACK.getIndex());

        Font fuenteTitulo = workbook.createFont();
        fuenteTitulo.setFontName("Arial");
        fuenteTitulo.setFontHeightInPoints((short) 15);
        fuenteTitulo.setBold(true);
        fuenteTitulo.setColor(IndexedColors.BLACK.getIndex());

        Font fuenteEncabezado = workbook.createFont();
        fuenteEncabezado.setFontName("Arial");
        fuenteEncabezado.setBold(true);
        fuenteEncabezado.setColor(IndexedColors.BLACK.getIndex());

        CellStyle titulo = workbook.createCellStyle();
        titulo.setFont(fuenteTitulo);
        titulo.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        titulo.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        CellStyle metadato = estiloCelda(workbook, fuenteNormal, false);
        CellStyle encabezado = estiloCelda(workbook, fuenteEncabezado, true);
        crearCelda(hoja.createRow(0), 0, "TrazaLab | Reporte de incidencias", titulo);
        hoja.getRow(0).setHeightInPoints(26);
        crearCelda(hoja.createRow(1), 0, "Generado: " + FechaReporte.formatear(generadoEn), metadato);
        crearCelda(hoja.createRow(2), 0, "Total de incidencias: " + incidencias.size(), metadato);
        hoja.addMergedRegion(new CellRangeAddress(0, 0, 0, ENCABEZADOS.length - 1));
        hoja.addMergedRegion(new CellRangeAddress(1, 1, 0, ENCABEZADOS.length - 1));
        hoja.addMergedRegion(new CellRangeAddress(2, 2, 0, ENCABEZADOS.length - 1));

        Row filaEncabezado = hoja.createRow(FILA_ENCABEZADOS);
        filaEncabezado.setHeightInPoints(24);
        for (int columna = 0; columna < ENCABEZADOS.length; columna++) {
            crearCelda(filaEncabezado, columna, ENCABEZADOS[columna], encabezado);
        }

        hoja.setColumnWidth(0, 15 * 256);
        hoja.setColumnWidth(1, 13 * 256);
        hoja.setColumnWidth(2, 25 * 256);
        hoja.setColumnWidth(3, 20 * 256);
        hoja.setColumnWidth(4, 72 * 256);
        hoja.setColumnWidth(5, 23 * 256);
        hoja.setColumnWidth(6, 20 * 256);
        hoja.createFreezePane(0, FILA_ENCABEZADOS + 1);
        hoja.setRepeatingRows(CellRangeAddress.valueOf("1:5"));
    }

    private CellStyle estiloCelda(XSSFWorkbook workbook, Font fuente, boolean bordes) {
        CellStyle estilo = workbook.createCellStyle();
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.WHITE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setVerticalAlignment(VerticalAlignment.TOP);
        if (bordes) {
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
            estilo.setBorderTop(BorderStyle.THIN);
        }
        return estilo;
    }

    private void escribirFilas(XSSFSheet hoja, List<IncidenciaRegistradaDto> incidencias) {
        Font fuenteCuerpo = hoja.getWorkbook().createFont();
        fuenteCuerpo.setFontName("Arial");
        fuenteCuerpo.setColor(IndexedColors.BLACK.getIndex());
        CellStyle estiloCuerpo = estiloCelda((XSSFWorkbook) hoja.getWorkbook(), fuenteCuerpo, true);
        estiloCuerpo.setWrapText(true);

        int numeroFila = FILA_ENCABEZADOS + 1;
        for (IncidenciaRegistradaDto incidencia : incidencias) {
            Row fila = hoja.createRow(numeroFila++);
            fila.setHeightInPoints(alturaDescripcion(incidencia.descripcion()));
            Object[] valores = {
                    incidencia.id(), incidencia.usuarioId(), incidencia.reportante(), incidencia.codigoQr(),
                    incidencia.descripcion(), FechaReporte.formatear(incidencia.fechaRegistro()), incidencia.estado()
            };
            for (int columna = 0; columna < valores.length; columna++) {
                Object valor = valores[columna];
                Cell celda = fila.createCell(columna);
                if (valor instanceof Number numero) {
                    celda.setCellValue(numero.doubleValue());
                } else {
                    celda.setCellValue(valor == null ? "" : valor.toString());
                }
                celda.setCellStyle(estiloCuerpo);
            }
        }
    }

    private void crearCelda(Row fila, int columna, String valor, CellStyle estilo) {
        Cell celda = fila.createCell(columna);
        celda.setCellValue(valor);
        celda.setCellStyle(estilo);
    }

    private float alturaDescripcion(String descripcion) {
        int lineas = Math.max(1, (int) Math.ceil((descripcion == null ? 0 : descripcion.length()) / 72.0));
        return Math.min(lineas, 10) * 15.0f + 6.0f;
    }

    private void configurarHoja(XSSFSheet hoja, int cantidad) {
        if (cantidad > 0) {
            hoja.setAutoFilter(new CellRangeAddress(FILA_ENCABEZADOS, FILA_ENCABEZADOS + cantidad, 0, ENCABEZADOS.length - 1));
        }
        hoja.setFitToPage(true);
        hoja.getPrintSetup().setLandscape(true);
        hoja.getPrintSetup().setFitWidth((short) 1);
        hoja.getPrintSetup().setFitHeight((short) 0);
        hoja.setMargin(PageMargin.LEFT, 0.35);
        hoja.setMargin(PageMargin.RIGHT, 0.35);
    }
}
