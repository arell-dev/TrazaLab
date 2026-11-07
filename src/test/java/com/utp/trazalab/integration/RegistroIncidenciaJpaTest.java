package com.utp.trazalab.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.utp.trazalab.dto.HistorialComputadoraDto;
import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.dto.FallaRecurrenteDto;
import java.io.ByteArrayInputStream;
import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import com.utp.trazalab.domain.enums.EstadoComputadora;
import com.utp.trazalab.domain.enums.EstadoIncidencia;
import com.utp.trazalab.persistence.jpa.entity.IncidenciaEntidad;
import com.utp.trazalab.persistence.jpa.entity.ComponenteEntidad;
import com.utp.trazalab.persistence.jpa.entity.TipoFallaEntidad;
import com.utp.trazalab.persistence.jpa.entity.RolEntidad;
import com.utp.trazalab.persistence.jpa.entity.UbicacionEntidad;
import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.ComputadoraJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.ComponenteJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.IncidenciaJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.TipoFallaJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.RolJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UbicacionJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import com.utp.trazalab.service.RegistroIncidenciaPersistenteServicio;
import com.utp.trazalab.service.RegistroMantenimientoServicio;
import com.utp.trazalab.service.ReporteFallasServicio;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "trazalab.seed.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "logging.file.name="
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class RegistroIncidenciaJpaTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurarPostgres(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        propiedades.add("spring.datasource.username", POSTGRES::getUsername);
        propiedades.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private RegistroIncidenciaPersistenteServicio servicio;
    @Autowired private RolJpaRepositorio roles;
    @Autowired private UsuarioJpaRepositorio usuarios;
    @Autowired private UbicacionJpaRepositorio ubicaciones;
    @Autowired private ComputadoraJpaRepositorio computadoras;
    @Autowired private IncidenciaJpaRepositorio incidencias;
    @Autowired private ComponenteJpaRepositorio componentes;
    @Autowired private TipoFallaJpaRepositorio tiposFalla;
    @Autowired private RegistroMantenimientoServicio servicioMantenimiento;
    @Autowired private ReporteFallasServicio servicioReportes;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    private UsuarioEntidad reportante;
    private UsuarioEntidad tecnico;
    private ComputadoraEntidad computadora;

    @BeforeEach
    void prepararDatos() {
        RolEntidad rol = roles.findByNombreIgnoreCase("Docente")
                .orElseGet(() -> roles.saveAndFlush(new RolEntidad("Docente", "Reporta incidencias del laboratorio.")));
        reportante = usuarios.saveAndFlush(new UsuarioEntidad(
                rol, "Ana", "Torres", "ana.torres.demo@trazalab.local", "activo"));
        RolEntidad rolTecnico = roles.findByNombreIgnoreCase("Técnico de Soporte")
                .orElseGet(() -> roles.saveAndFlush(new RolEntidad("Técnico de Soporte", "Pruebas de mantenimiento.")));
        tecnico = usuarios.saveAndFlush(new UsuarioEntidad(
                rolTecnico, "Luis", "Pérez", "tecnico.trazalab.demo@trazalab.local", "activo"));
        UbicacionEntidad ubicacion = ubicaciones.saveAndFlush(new UbicacionEntidad("LAB-TEST", "Prueba de integración"));
        computadora = computadoras.saveAndFlush(new ComputadoraEntidad("QR-TEST-001", "SERIE-TEST-001", "HP", "Mini", ubicacion, EstadoComputadora.OPERATIVA));
    }

    @Test
    void registraYRecuperaLaIncidenciaEnPostgresql() {
        IncidenciaRegistradaDto creada = servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "No enciende.");

        List<IncidenciaEntidad> guardadas = incidencias
                .findAllByComputadora_IdOrderByFechaRegistroAscIdAsc(computadora.getId(), PageRequest.of(0, 10))
                .getContent();
        assertEquals(1, guardadas.size());
        assertEquals(creada.id(), guardadas.getFirst().getId());
        assertEquals("No enciende.", guardadas.getFirst().getDescripcion());
        assertEquals("activo", guardadas.getFirst().getUsuario().getEstado());
        assertEquals("QR-TEST-001", guardadas.getFirst().getComputadora().getCodigoQr());
    }

    @Test
    void rechazaDescripcionVaciaSinGuardar() {
        assertThrows(IllegalArgumentException.class, () -> servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "  "));
        assertEquals(0, incidencias.count());
    }

    @Test
    void renderizaElFormularioWebSinTextoDeIdentidadDemostracion() throws Exception {
        mockMvc.perform(get("/incidencias/nueva"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar incidencia")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Confirmación")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Incidencias recientes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verificar equipo")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Sin autenticación"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("identidad de demostración"))));
    }

    @Test
    void renderizaLosPanelesWebActivos() throws Exception {
        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "La pantalla no enciende.");

        mockMvc.perform(get("/incidencias"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Historial de reportes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("La pantalla no enciende.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/colores.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/componentes.css")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/incidencias/exportar/"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("CSV"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("PDF"))));

        mockMvc.perform(get("/mantenimientos"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar intervención")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Intervenciones recientes")));

        mockMvc.perform(get("/reportes"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fallas recurrentes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Descargar CSV")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Descargar Excel")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Descargar PDF")));

        mockMvc.perform(get("/incidencias/exportar/excel"))
                .andExpect(status().isNotFound());
    }

    @Test
    void informaElEstadoDeLaApiConConexionPostgresql() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"estado\":\"disponible\"}"));
    }

    @Test
    void consultaEquipoYUltimasCincoIncidenciasPorCodigoQr() throws Exception {
        for (int numero = 1; numero <= 6; numero++) {
            servicio.registrar(
                    reportante.getId(),
                    computadora.getCodigoQr(),
                    "Incidencia de prueba " + numero + ".");
        }

        mockMvc.perform(get("/api/computadoras/historial")
                        .param("codigoQr", computadora.getCodigoQr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoQr").value("QR-TEST-001"))
                .andExpect(jsonPath("$.numeroSerie").value("SERIE-TEST-001"))
                .andExpect(jsonPath("$.marca").value("HP"))
                .andExpect(jsonPath("$.ubicacion").value("LAB-TEST / Prueba de integración"))
                .andExpect(jsonPath("$.incidencias.length()").value(5))
                .andExpect(jsonPath("$.incidencias[0].descripcion")
                        .value("Incidencia de prueba 6."))
                .andExpect(jsonPath("$.incidencias[4].descripcion")
                        .value("Incidencia de prueba 2."))
                .andExpect(jsonPath("$.incidencias[0].reportante").doesNotExist());
    }

    @Test
    void responde404CuandoElQrDelEquipoNoExiste() throws Exception {
        mockMvc.perform(get("/api/computadoras/historial").param("codigoQr", "QR-INEXISTENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("No existe una computadora con ese código QR."));
    }

    @Test
    void registraLaIncidenciaConUsuarioFijadoPorElServidor() throws Exception {
        mockMvc.perform(post("/incidencias")
                        .with(csrf())
                        .param("codigoQr", computadora.getCodigoQr())
                        .param("descripcion", "La pantalla no enciende.")
                        .param("usuarioId", "9999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/incidencias"));

        List<IncidenciaEntidad> guardadas = incidencias.findAll();
        assertEquals(1, guardadas.size());
        assertEquals(reportante.getId(), guardadas.getFirst().getUsuario().getId());
    }

    @Test
    void mantieneElFormularioYValidaLosCamposObligatorios() throws Exception {
        mockMvc.perform(post("/incidencias")
                        .with(csrf())
                        .param("codigoQr", " ")
                        .param("descripcion", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ingresa el código QR del equipo.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("La descripción de la falla es obligatoria.")));
    }

    @Test
    void mantieneElFormularioCuandoElCodigoQrNoExiste() throws Exception {
        mockMvc.perform(post("/incidencias")
                        .with(csrf())
                        .param("codigoQr", "QR-INEXISTENTE")
                        .param("descripcion", "La computadora no inicia."))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "No existe una computadora con ese código QR.")));

        assertEquals(0, incidencias.count());
    }

    @Test
    void registraMantenimientoSinCambiarEstadoNiCerrarIncidencia() throws Exception {
        IncidenciaRegistradaDto incidencia = servicio.registrar(
                reportante.getId(), computadora.getCodigoQr(), "Pantalla intermitente.");

        servicioMantenimiento.registrar(
                tecnico.getId(),
                computadora.getCodigoQr(),
                "preventivo",
                null,
                "Se revisó el cable de video.",
                null,
                null,
                null,
                null,
                null);

        assertEquals("operativa", jdbcTemplate.queryForObject(
                "SELECT estado FROM computadora WHERE computadora_id = ?", String.class, computadora.getId()));
        assertEquals("registrada", jdbcTemplate.queryForObject(
                "SELECT estado FROM incidencia WHERE incidencia_id = ?", String.class, incidencia.id()));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mantenimiento", Integer.class));
        mockMvc.perform(get("/mantenimientos"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Se revisó el cable de video.")));
    }

    @Test
    void instalarYReemplazarComponenteMantieneHistorialDeAsignaciones() {
        servicioMantenimiento.registrar(
                tecnico.getId(), computadora.getCodigoQr(), "preventivo", null, "Instalación inicial.",
                "instalado", null, "Memoria 8 GB", "RAM", "RAM-OLD");
        ComponenteEntidad anterior = componentes.findByNumeroSerieIgnoreCase("RAM-OLD").orElseThrow();

        servicioMantenimiento.registrar(
                tecnico.getId(), computadora.getCodigoQr(), "preventivo", null, "Se reemplazó la memoria.",
                "reemplazado", "RAM-OLD", "Memoria 16 GB", "RAM", "RAM-NEW");

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM computadora_componente "
                        + "WHERE computadora_id = ? AND componente_id = ? AND fecha_retiro IS NOT NULL",
                Integer.class,
                computadora.getId(),
                anterior.getId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM computadora_componente cc "
                        + "JOIN componente c ON c.componente_id = cc.componente_id "
                        + "WHERE cc.computadora_id = ? AND c.numero_serie = 'RAM-NEW' AND cc.fecha_retiro IS NULL",
                Integer.class,
                computadora.getId()));
        assertEquals("operativa", jdbcTemplate.queryForObject(
                "SELECT estado FROM computadora WHERE computadora_id = ?", String.class, computadora.getId()));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mantenimiento_componente", Integer.class));
    }

    @Test
    void mantenimientoCorrectivoExigeFallaDelMismoEquipo() {
        Integer tipoFallaId = tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc().getFirst().getId();
        IncidenciaRegistradaDto incidencia = servicio.registrar(
                reportante.getId(), computadora.getCodigoQr(), "Falla para correctivo.", tipoFallaId);
        Integer fallaId = jdbcTemplate.queryForObject(
                "SELECT falla_id FROM falla WHERE incidencia_id = ?", Integer.class, incidencia.id());

        UbicacionEntidad otraUbicacion = ubicaciones.saveAndFlush(new UbicacionEntidad("LAB-OTRO", "Equipo separado"));
        ComputadoraEntidad otroEquipo = computadoras.saveAndFlush(new ComputadoraEntidad(
                "QR-TEST-002", "SERIE-TEST-002", "Dell", "OptiPlex", otraUbicacion, EstadoComputadora.OPERATIVA));

        assertThrows(IllegalArgumentException.class, () -> servicioMantenimiento.registrar(
                tecnico.getId(), otroEquipo.getCodigoQr(), "correctivo", fallaId, "Correctivo cruzado.",
                null, null, null, null, null));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mantenimiento", Integer.class));

        servicioMantenimiento.registrar(
                tecnico.getId(), computadora.getCodigoQr(), "correctivo", fallaId, "Correctivo asociado.",
                null, null, null, null, null);
        assertEquals(fallaId, jdbcTemplate.queryForObject(
                "SELECT falla_id FROM mantenimiento", Integer.class));
    }

    @Test
    void clasificaFallasYCalculaRecurrenciaPorEquipoYTipoEnElPeriodo() throws Exception {
        List<TipoFallaEntidad> catalogo = tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc();
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

    @Test
    void paginaIncidenciasSinDuplicadosConDesempatePorId() {
        guardarIncidencias(11);

        Page<IncidenciaRegistradaDto> primera = servicio.consultarIncidencias(PageRequest.of(0, 10));
        Page<IncidenciaRegistradaDto> ultima = servicio.consultarIncidencias(PageRequest.of(1, 10));
        Page<IncidenciaRegistradaDto> vacia = servicio.consultarIncidencias(PageRequest.of(2, 10));

        assertEquals(11, primera.getTotalElements());
        assertEquals(10, primera.getNumberOfElements());
        assertEquals(1, ultima.getNumberOfElements());
        assertTrue(ultima.isLast());
        assertTrue(vacia.isEmpty());
        List<Integer> idsPagina = Stream.concat(
                primera.getContent().stream(),
                ultima.getContent().stream())
                .map(IncidenciaRegistradaDto::id)
                .toList();

        assertEquals(11, new HashSet<>(idsPagina).size());
        assertTrue(primera.getContent().getFirst().id() > primera.getContent().getLast().id());
        assertTrue(primera.getContent().getLast().id() > ultima.getContent().getFirst().id());
    }

    @Test
    void limitaElTamanoSolicitadoParaMantenerConsultasAcotadas() {
        guardarIncidencias(11);

        Page<IncidenciaRegistradaDto> pagina = servicio.consultarIncidencias(PageRequest.of(0, 1_000));
        Page<IncidenciaRegistradaDto> paginaSinLimite = servicio.consultarIncidencias(Pageable.unpaged());

        assertEquals(100, pagina.getSize());
        assertEquals(11, pagina.getNumberOfElements());
        assertEquals(11, pagina.getTotalElements());
        assertEquals(100, paginaSinLimite.getSize());
        assertEquals(11, paginaSinLimite.getNumberOfElements());
    }

    @Test
    void paginaElHistorialDeLaComputadoraEnOrdenAscendente() {
        guardarIncidencias(11);

        HistorialComputadoraDto primera = servicio.consultarHistorialComputadora(
                computadora.getCodigoQr(), PageRequest.of(0, 10));
        HistorialComputadoraDto ultima = servicio.consultarHistorialComputadora(
                computadora.getCodigoQr(), PageRequest.of(1, 10));

        assertEquals(11, primera.incidencias().getTotalElements());
        assertEquals(10, primera.incidencias().getNumberOfElements());
        assertEquals(1, ultima.incidencias().getNumberOfElements());
        assertTrue(primera.incidencias().getContent().getFirst().id()
                < primera.incidencias().getContent().getLast().id());
        assertTrue(primera.incidencias().getContent().getLast().id()
                < ultima.incidencias().getContent().getFirst().id());
    }

    @Test
    void recorrePorPaginasUnHistorialDeCincoMilIncidencias() {
        int cantidad = 5_000;
        guardarIncidenciasJdbc(cantidad);
        int numeroPagina = 0;
        long registrosLeidos = 0;
        Integer idAnterior = null;
        Page<IncidenciaRegistradaDto> pagina;

        do {
            pagina = servicio.consultarIncidencias(PageRequest.of(numeroPagina, 100));
            assertEquals(cantidad, pagina.getTotalElements());
            for (IncidenciaRegistradaDto incidencia : pagina.getContent()) {
                if (idAnterior != null) {
                    assertTrue(idAnterior > incidencia.id());
                }
                idAnterior = incidencia.id();
                registrosLeidos++;
            }
            numeroPagina++;
        } while (pagina.hasNext());

        assertEquals(cantidad, registrosLeidos);
        assertEquals(50, numeroPagina);
    }

    private void guardarIncidencias(int cantidad) {
        Instant fechaCompartida = Instant.parse("2026-11-05T12:00:00Z");
        for (int numero = 1; numero <= cantidad; numero++) {
            incidencias.saveAndFlush(new IncidenciaEntidad(
                    computadora,
                    reportante,
                    "Falla número " + numero,
                    fechaCompartida,
                    EstadoIncidencia.REGISTRADA));
        }
    }

    private void guardarIncidenciasJdbc(int cantidad) {
        String sql = "insert into incidencia "
                + "(computadora_id, usuario_id, descripcion, fecha_registro, estado) "
                + "values (?, ?, ?, ?, ?)";
        String descripcionBase = "x".repeat(440);
        Timestamp fecha = Timestamp.from(Instant.parse("2026-11-05T12:00:00Z"));

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int indice) throws java.sql.SQLException {
                statement.setInt(1, computadora.getId());
                statement.setInt(2, reportante.getId());
                statement.setString(3, "Incidencia sintética " + indice + " " + descripcionBase);
                statement.setTimestamp(4, fecha);
                statement.setString(5, "registrada");
            }

            @Override
            public int getBatchSize() {
                return cantidad;
            }
        });
    }

}
