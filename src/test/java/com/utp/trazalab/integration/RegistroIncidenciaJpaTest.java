package com.utp.trazalab.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.utp.trazalab.dto.HistorialComputadoraDto;
import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.export.csv.ExportadorIncidenciasCsv;
import com.utp.trazalab.export.excel.ExportadorIncidenciasExcel;
import com.utp.trazalab.export.pdf.ExportadorIncidenciasPdf;
import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import com.utp.trazalab.domain.enums.EstadoComputadora;
import com.utp.trazalab.domain.enums.EstadoIncidencia;
import com.utp.trazalab.persistence.jpa.entity.IncidenciaEntidad;
import com.utp.trazalab.persistence.jpa.entity.RolEntidad;
import com.utp.trazalab.persistence.jpa.entity.UbicacionEntidad;
import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.ComputadoraJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.IncidenciaJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.RolJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UbicacionJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import com.utp.trazalab.service.RegistroIncidenciaPersistenteServicio;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
    @Autowired private ExportadorIncidenciasCsv exportadorCsv;
    @Autowired private ExportadorIncidenciasExcel exportadorExcel;
    @Autowired private ExportadorIncidenciasPdf exportadorPdf;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    @TempDir
    private Path directorioTemporal;

    private UsuarioEntidad reportante;
    private ComputadoraEntidad computadora;

    @BeforeEach
    void prepararDatos() {
        RolEntidad rol = roles.findByNombreIgnoreCase("Docente")
                .orElseGet(() -> roles.saveAndFlush(new RolEntidad("Docente", "Reporta incidencias del laboratorio.")));
        reportante = usuarios.saveAndFlush(new UsuarioEntidad(
                rol, "Ana", "Torres", "ana.torres.demo@trazalab.local", "activo"));
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
    void renderizaElFormularioWebConIdentidadDeDemostracion() throws Exception {
        mockMvc.perform(get("/incidencias/nueva"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Modo de demostración")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ana Torres")));
    }

    @Test
    void renderizaElListadoYLosPanelesProvisionales() throws Exception {
        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "La pantalla no enciende.");

        mockMvc.perform(get("/incidencias"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Historial de reportes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("La pantalla no enciende.")));

        mockMvc.perform(get("/mantenimientos"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mantenimientos")));

        mockMvc.perform(get("/reportes"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reportes")));
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
    void exportaTodosLosRegistrosPorLotesEnCsvExcelYPdf() throws Exception {
        guardarIncidencias(251);
        var datosExportacion = servicio.prepararExportacionIncidencias();
        guardarIncidencias(252, 1);

        Path csv = exportadorCsv.exportar(datosExportacion.incidencias(), datosExportacion.total(), directorioTemporal, Instant.parse("2026-11-05T12:00:00Z"));
        Path excel = exportadorExcel.exportar(datosExportacion.incidencias(), datosExportacion.total(), directorioTemporal, Instant.parse("2026-11-05T12:00:00Z"));
        Path pdf = exportadorPdf.exportar(datosExportacion.incidencias(), datosExportacion.total(), directorioTemporal, Instant.parse("2026-11-05T12:00:00Z"));

        assertEquals(251, datosExportacion.total());
        assertEquals(252, Files.readAllLines(csv, StandardCharsets.UTF_8).size());
        try (var libro = WorkbookFactory.create(excel.toFile())) {
            assertEquals(255, libro.getSheet("Incidencias").getLastRowNum());
        }
        try (var documento = Loader.loadPDF(pdf.toFile())) {
            String contenido = new PDFTextStripper().getText(documento);
            assertTrue(documento.getNumberOfPages() > 1);
            assertTrue(contenido.contains("Total de incidencias: 251"));
            assertTrue(contenido.contains("Falla número 1"));
            assertTrue(contenido.contains("Falla número 251"));
            assertFalse(contenido.contains("Falla número 252"));
            assertTrue(contenido.contains(
                    "Página " + documento.getNumberOfPages() + " de " + documento.getNumberOfPages()));
        }
    }

    @Test
    void comparaCargaCompletaConIteracionPorLotesSobreElMismoConjunto() throws Exception {
        int cantidad = 5_000;
        guardarIncidenciasJdbc(cantidad);
        entityManager.clear();
        System.gc();
        Thread.sleep(100);

        long memoriaAntesCargaCompleta = memoriaUsada();
        long inicioCargaCompleta = System.nanoTime();
        List<IncidenciaRegistradaDto> cargaCompleta = entityManager.createQuery(
                        "select incidencia from IncidenciaEntidad incidencia "
                                + "join fetch incidencia.usuario "
                                + "join fetch incidencia.computadora "
                                + "order by incidencia.fechaRegistro desc, incidencia.id desc",
                        IncidenciaEntidad.class)
                .getResultList()
                .stream()
                .map(this::convertirIncidencia)
                .toList();
        long tiempoCargaCompleta = System.nanoTime() - inicioCargaCompleta;
        System.gc();
        Thread.sleep(100);
        long memoriaRetenidaCompleta = Math.max(0, memoriaUsada() - memoriaAntesCargaCompleta);

        assertEquals(cantidad, cargaCompleta.size());
        cargaCompleta = null;
        entityManager.clear();
        System.gc();
        Thread.sleep(100);

        long memoriaAntesLotes = memoriaUsada();
        long inicioLotes = System.nanoTime();
        var exportacion = servicio.prepararExportacionIncidencias();
        long registrosLeidos = 0;
        for (IncidenciaRegistradaDto incidencia : exportacion.incidencias()) {
            registrosLeidos++;
            if (registrosLeidos % 250 == 0) {
                entityManager.clear();
            }
        }
        long tiempoLotes = System.nanoTime() - inicioLotes;
        System.gc();
        Thread.sleep(100);
        long memoriaRetenidaLotes = Math.max(0, memoriaUsada() - memoriaAntesLotes);

        assertEquals(cantidad, registrosLeidos);
        assertEquals(cantidad, exportacion.total());
        System.out.printf(
                "BENCHMARK OBS-01: filas=%d, cargaCompletaMs=%.2f, lotesMs=%.2f, "
                        + "heapRetenidoCompletaBytes=%d, heapRetenidoLotesBytes=%d%n",
                cantidad,
                tiempoCargaCompleta / 1_000_000.0,
                tiempoLotes / 1_000_000.0,
                memoriaRetenidaCompleta,
                memoriaRetenidaLotes);
    }

    private void guardarIncidencias(int cantidad) {
        guardarIncidencias(1, cantidad);
    }

    private void guardarIncidencias(int primerNumero, int cantidad) {
        Instant fechaCompartida = Instant.parse("2026-11-05T12:00:00Z");
        for (int numero = primerNumero; numero < primerNumero + cantidad; numero++) {
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

    private IncidenciaRegistradaDto convertirIncidencia(IncidenciaEntidad incidencia) {
        return new IncidenciaRegistradaDto(
                incidencia.getId(),
                incidencia.getUsuario().getId(),
                incidencia.getUsuario().getNombres() + " " + incidencia.getUsuario().getApellidos(),
                incidencia.getComputadora().getCodigoQr(),
                incidencia.getDescripcion(),
                incidencia.getFechaRegistro(),
                incidencia.getEstado().name().toLowerCase(Locale.ROOT));
    }

    private long memoriaUsada() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
