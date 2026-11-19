package com.utp.trazalab.integration;

import com.utp.trazalab.reportes.dto.FallaRecurrenteDto;
import com.utp.trazalab.reportes.dto.DistribucionCategoriaDto;
import com.utp.trazalab.trazabilidad.model.TipoFalla;
import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReporteIntegracionTest extends BaseIntegracion {
    @Test
    void paginaResultadosYConservaIndicadoresYExportacionCompleta() throws Exception {
        Integer tipoFallaId = tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc().getFirst().getId();
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        for (int indice = 0; indice < 21; indice++) {
            String codigo = "QR-PAGINA-%02d".formatted(indice);
            Computadora equipo = computadoras.saveAndFlush(new Computadora(codigo, "SERIE-PAGINA-%02d".formatted(indice), "HP", "Mini", computadora.getUbicacion(), EstadoComputadora.OPERATIVA));
            servicio.registrar(reportante.getId(), equipo.getCodigoQr(), "Primer caso", tipoFallaId);
            servicio.registrar(reportante.getId(), equipo.getCodigoQr(), "Segundo caso", tipoFallaId);
        }

        var primera = mockMvc.perform(get("/reportes")
                        .param("desde", hoy.minusDays(29).toString())
                        .param("hasta", hoy.toString())
                        .param("tipoFallaId", tipoFallaId.toString()))
                .andExpect(status().isOk()).andReturn();
        String contenidoPrimera = primera.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(21L, primera.getModelAndView().getModel().get("cantidadPatrones"));
        assertEquals(21L, primera.getModelAndView().getModel().get("equiposAfectados"));
        assertEquals(42L, primera.getModelAndView().getModel().get("cantidadEventos"));
        @SuppressWarnings("unchecked")
        List<DistribucionCategoriaDto> categorias = (List<DistribucionCategoriaDto>) primera.getModelAndView().getModel().get("distribucionCategorias");
        assertEquals(1, categorias.size());
        assertEquals(42L, categorias.getFirst().frecuencia());
        assertEquals(100, categorias.getFirst().porcentaje());
        assertTrue(contenidoPrimera.contains("QR-PAGINA-00"));
        assertFalse(contenidoPrimera.contains("QR-PAGINA-20"));
        assertTrue(contenidoPrimera.contains("Siguiente"));

        var segunda = mockMvc.perform(get("/reportes")
                        .param("desde", hoy.minusDays(29).toString())
                        .param("hasta", hoy.toString())
                        .param("tipoFallaId", tipoFallaId.toString())
                        .param("pagina", "1"))
                .andExpect(status().isOk()).andReturn();
        String contenidoSegunda = segunda.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(21L, segunda.getModelAndView().getModel().get("cantidadPatrones"));
        assertEquals(42L, segunda.getModelAndView().getModel().get("cantidadEventos"));
        assertTrue(contenidoSegunda.contains("QR-PAGINA-20"));
        assertFalse(contenidoSegunda.contains("QR-PAGINA-00"));

        var ultima = servicioReportes.consultarPagina(hoy.minusDays(29), hoy, null, tipoFallaId, Integer.MAX_VALUE);
        assertEquals(1, ultima.recurrencias().getNumber());
        assertEquals(1, ultima.recurrencias().getNumberOfElements());
        var negativa = servicioReportes.consultarPagina(hoy.minusDays(29), hoy, null, tipoFallaId, -1);
        assertEquals(0, negativa.recurrencias().getNumber());
        assertEquals(20, negativa.recurrencias().getNumberOfElements());
        var filtrada = servicioReportes.consultarPagina(hoy.minusDays(29), hoy, "QR-PAGINA-20", tipoFallaId, 1);
        assertEquals(0, filtrada.recurrencias().getNumber());
        assertEquals(1, filtrada.resumen().cantidadPatrones());
        assertEquals(2, filtrada.resumen().cantidadEventos());

        String csv = mockMvc.perform(get("/reportes/exportar/csv")
                        .param("desde", hoy.minusDays(29).toString())
                        .param("hasta", hoy.toString())
                        .param("tipoFallaId", tipoFallaId.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("QR-PAGINA-00"));
        assertTrue(csv.contains("QR-PAGINA-20"));
    }

    @Test
    void clasificaFallasYCalculaRecurrenciaPorEquipoYTipoEnElPeriodo() throws Exception {
        List<TipoFalla> catalogo = tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc();
        Integer tipoFallaId = catalogo.getFirst().getId();
        Integer otroTipoFallaId = catalogo.get(1).getId();
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));

        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "Primer caso.", tipoFallaId);
        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "Segundo caso.", tipoFallaId);
        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "Falla de otro tipo.", otroTipoFallaId);

        List<FallaRecurrenteDto> recurrencias = servicioReportes.consultarRecurrencias(
                hoy.minusDays(29), hoy, computadora.getCodigoQr(), tipoFallaId);

        assertEquals(1, recurrencias.size());
        assertEquals(2, recurrencias.getFirst().frecuencia());
        assertEquals(computadora.getCodigoQr(), recurrencias.getFirst().codigoQr());
        assertTrue(servicioReportes.consultarRecurrencias(
                hoy.minusDays(29), hoy, computadora.getCodigoQr(), otroTipoFallaId).isEmpty());
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM falla", Integer.class));
        mockMvc.perform(get("/reportes")
                        .param("desde", hoy.minusDays(29).toString())
                        .param("hasta", hoy.toString())
                        .param("codigoQr", computadora.getCodigoQr())
                        .param("tipoFallaId", tipoFallaId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(computadora.getCodigoQr())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 resultado")));
    }

    @Test
    void exportaRecurrenciasFiltradasEnCsvExcelYPdfDesdeLaWeb() throws Exception {
        Integer tipoFallaId = tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc().getFirst().getId();
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "Primer caso de exportación.", tipoFallaId);
        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "Segundo caso de exportación.", tipoFallaId);

        org.springframework.mock.web.MockHttpServletResponse csv = mockMvc.perform(get("/reportes/exportar/csv")
                        .param("desde", hoy.minusDays(29).toString())
                        .param("hasta", hoy.toString())
                        .param("codigoQr", computadora.getCodigoQr())
                        .param("tipoFallaId", tipoFallaId.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        assertTrue(csv.getContentType().contains("text/csv"));
        assertTrue(csv.getContentAsString(StandardCharsets.UTF_8).contains(computadora.getCodigoQr()));

        org.springframework.mock.web.MockHttpServletResponse excel = mockMvc.perform(get("/reportes/exportar/excel")
                        .param("desde", hoy.minusDays(29).toString())
                        .param("hasta", hoy.toString())
                        .param("codigoQr", computadora.getCodigoQr())
                        .param("tipoFallaId", tipoFallaId.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        assertTrue(excel.getContentType().contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        try (Workbook libro = WorkbookFactory.create(new ByteArrayInputStream(excel.getContentAsByteArray()))) {
            assertEquals("TrazaLab | Informe de fallas recurrentes", libro.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
            assertEquals("Código QR", libro.getSheetAt(0).getRow(3).getCell(1).getStringCellValue());
            assertEquals(computadora.getCodigoQr(), libro.getSheetAt(0).getRow(4).getCell(1).getStringCellValue());
            assertEquals(2, libro.getSheetAt(0).getRow(4).getCell(5).getNumericCellValue());
        }

        org.springframework.mock.web.MockHttpServletResponse pdf = mockMvc.perform(get("/reportes/exportar/pdf")
                        .param("desde", hoy.minusDays(29).toString())
                        .param("hasta", hoy.toString())
                        .param("codigoQr", computadora.getCodigoQr())
                        .param("tipoFallaId", tipoFallaId.toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        try (PDDocument documento = Loader.loadPDF(pdf.getContentAsByteArray())) {
            assertTrue(new PDFTextStripper().getText(documento).contains(computadora.getCodigoQr()));
        }

        mockMvc.perform(get("/reportes/exportar/otro")
                        .param("desde", hoy.minusDays(29).toString())
                        .param("hasta", hoy.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ajustaLasFilasExtensasAlAnchoDePaginaPdf() throws Exception {
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        FallaRecurrenteDto extensa = new FallaRecurrenteDto(
                1,
                "W".repeat(100),
                "M".repeat(80),
                "Categoria de hardware ".repeat(5),
                1,
                "Tipo de falla recurrente ".repeat(8),
                12,
                LocalDateTime.of(2026, 11, 5, 12, 0));

        byte[] pdf = servicioReportes.exportarPdf(List.of(extensa), hoy.minusDays(1), hoy);

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper verificadorAncho = new PDFTextStripper() {
                @Override
                protected void writeString(String texto, List<TextPosition> posiciones) throws IOException {
                    double bordeDerecho = posiciones.stream()
                            .mapToDouble(posicion -> posicion.getXDirAdj() + posicion.getWidthDirAdj())
                            .max()
                            .orElse(0);
                    assertTrue(bordeDerecho <= PDRectangle.A4.getWidth() - 39);
                    super.writeString(texto, posiciones);
                }
            };
            String texto = verificadorAncho.getText(documento).replaceAll("\\s", "");

            assertTrue(texto.contains("W".repeat(100)));
            assertTrue(texto.contains("M".repeat(80)));
        }
    }

}
