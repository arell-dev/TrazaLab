package com.utp.trazalab.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import com.utp.trazalab.domain.enums.EstadoComputadora;
import com.utp.trazalab.domain.enums.EstadoIncidencia;
import com.utp.trazalab.dto.EventoHistorialTecnicoDto;
import com.utp.trazalab.dto.FallaRecurrenteDto;
import com.utp.trazalab.dto.HistorialComputadoraDto;
import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.dto.UsuarioAltaFormulario;
import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import com.utp.trazalab.persistence.jpa.entity.ComponenteEntidad;
import com.utp.trazalab.persistence.jpa.entity.IncidenciaEntidad;
import com.utp.trazalab.persistence.jpa.entity.RolEntidad;
import com.utp.trazalab.persistence.jpa.entity.SedeEntidad;
import com.utp.trazalab.persistence.jpa.entity.TipoFallaEntidad;
import com.utp.trazalab.persistence.jpa.entity.UbicacionEntidad;
import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.ComputadoraJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.ComponenteJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.IncidenciaJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.TipoFallaJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.RolJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.SedeJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UbicacionJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import com.utp.trazalab.service.RegistroIncidenciaPersistenteServicio;
import com.utp.trazalab.service.RegistroMantenimientoServicio;
import com.utp.trazalab.service.ReporteFallasServicio;
import com.utp.trazalab.service.AdministracionUsuariosServicio;
import com.utp.trazalab.service.HistorialTecnicoConsultaServicio;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
@WithMockUser(username = "ana.torres.demo@trazalab.local", authorities = "ROLE_Responsable")
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
    @Autowired private SedeJpaRepositorio sedes;
    @Autowired private UsuarioJpaRepositorio usuarios;
    @Autowired private UbicacionJpaRepositorio ubicaciones;
    @Autowired private ComputadoraJpaRepositorio computadoras;
    @Autowired private IncidenciaJpaRepositorio incidencias;
    @Autowired private ComponenteJpaRepositorio componentes;
    @Autowired private TipoFallaJpaRepositorio tiposFalla;
    @Autowired private RegistroMantenimientoServicio servicioMantenimiento;
    @Autowired private ReporteFallasServicio servicioReportes;
    @Autowired private AdministracionUsuariosServicio servicioUsuarios;
    @Autowired private HistorialTecnicoConsultaServicio servicioHistorialTecnico;
    @Autowired private PasswordEncoder codificadorContrasenas;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;
    @Autowired private MockMvc mockMvc;

    private UsuarioEntidad reportante;
    private UsuarioEntidad tecnico;
    private ComputadoraEntidad computadora;
    private SedeEntidad sedePiura;

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
        RolEntidad rolAdministrador = roles.findByNombreIgnoreCase("Administrador").orElseThrow();
        usuarios.saveAndFlush(new UsuarioEntidad(
                rolAdministrador,
                "Admin",
                "Pruebas",
                "admin-test@trazalab.local",
                "activo",
                codificadorContrasenas.encode("clave-segura-de-pruebas")));
        sedePiura = sedes.findByNombreIgnoreCase("UTP Sede Piura").orElseThrow();
        UbicacionEntidad ubicacion = ubicaciones.findByNombreIgnoreCaseAndSede_NombreIgnoreCase("A0301", sedePiura.getNombre()).orElseThrow();
        computadora = computadoras.saveAndFlush(new ComputadoraEntidad("QR-TEST-001", "SERIE-TEST-001", "HP", "Mini", ubicacion, EstadoComputadora.OPERATIVA));
        UbicacionEntidad ubicacionAnterior = ubicaciones.saveAndFlush(new UbicacionEntidad("LAB-TEST", "Prueba de integración", sedePiura, "Torre A", 3, "LABORATORIO"));
        computadoras.saveAndFlush(new ComputadoraEntidad("QR-LEGACY-001", "SERIE-LEGACY-001", "Dell", "OptiPlex", ubicacionAnterior, EstadoComputadora.OPERATIVA));
    }

    @Test
    @WithMockUser(username = "docente@trazalab.local", authorities = "ROLE_Docente")
    void docenteNoPuedeConsultarReportesNiHistorialTecnico() throws Exception {
        mockMvc.perform(get("/reportes"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/computadoras/historial")
                        .param("codigoQr", "QR-TEST-001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void usuarioAnonimoDebeIniciarSesionParaConsultarIncidencias() throws Exception {
        mockMvc.perform(get("/incidencias"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/login"));
    }

    @Test
    @WithAnonymousUser
    void loginDeUsuariosMuestraLaMarcaInstitucional() throws Exception {
        mockMvc.perform(get("/usuarios/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/images/utp-labtrace.svg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/usuarios/login\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rel=\"icon\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Trazabilidad técnica para los laboratorios de la UTP.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Iniciar sesión</button>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("autofocus"))));
    }

    @Test
    @WithAnonymousUser
    void muestraElErrorDeAutenticacionEnElFormularioDeAcceso() throws Exception {
        mockMvc.perform(get("/usuarios/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Correo o contraseña incorrectos, o cuenta inactiva.")));
    }

    @Test
    @WithAnonymousUser
    void loginCorrectoRedirigeAlPanelDeDisponibilidad() throws Exception {
        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .param("username", "admin-test@trazalab.local")
                        .param("password", "clave-segura-de-pruebas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/disponibilidad"));
    }

    @Test
    @WithAnonymousUser
    void rechazaContrasenaIncorrectaYCuentaInactiva() throws Exception {
        RolEntidad rolDocente = roles.findByNombreIgnoreCase("Docente").orElseThrow();
        usuarios.saveAndFlush(new UsuarioEntidad(
                rolDocente,
                "Cuenta",
                "Inactiva",
                "cuenta.inactiva@trazalab.local",
                "inactivo",
                codificadorContrasenas.encode("contrasena-valida-123")));

        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .param("username", "admin-test@trazalab.local")
                        .param("password", "incorrecta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/login?error"));

        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .param("username", "cuenta.inactiva@trazalab.local")
                        .param("password", "contrasena-valida-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/login?error"));
    }

    @Test
    void limitaLaAdministracionDeUsuariosAlRolAdministradorYExigeCsrf() throws Exception {
        mockMvc.perform(get("/usuarios")
                        .with(user("tecnico@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Técnico de Soporte"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/usuarios")
                        .with(user("admin-test@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Administrador"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Administración de usuarios")));

        mockMvc.perform(post("/usuarios")
                        .with(user("admin-test@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Administrador")))
                        .param("nombres", "Nueva")
                        .param("apellidos", "Cuenta")
                        .param("correo", "nueva.cuenta@trazalab.local")
                        .param("contrasena", "clave-de-prueba-123")
                        .param("rolId", roles.findByNombreIgnoreCase("Docente").orElseThrow().getId().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/usuarios")
                        .with(user("tecnico@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Técnico de Soporte")))
                        .with(csrf())
                        .param("nombres", "Nueva")
                        .param("apellidos", "Cuenta")
                        .param("correo", "nueva.cuenta@trazalab.local")
                        .param("contrasena", "clave-de-prueba-123")
                        .param("rolId", roles.findByNombreIgnoreCase("Docente").orElseThrow().getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void formularioAdministradorCreaUnaCuentaConCredencialCifrada() throws Exception {
        Integer rolDocenteId = roles.findByNombreIgnoreCase("Docente").orElseThrow().getId();

        mockMvc.perform(post("/usuarios")
                        .with(user("admin-test@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Administrador")))
                        .with(csrf())
                        .param("nombres", "Elena")
                        .param("apellidos", "Paredes")
                        .param("correo", "elena.paredes@trazalab.local")
                        .param("contrasena", "clave-de-prueba-123")
                        .param("rolId", rolDocenteId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios"));

        UsuarioEntidad creada = usuarios.findByCorreoIgnoreCase("elena.paredes@trazalab.local").orElseThrow();
        assertEquals("activo", creada.getEstado());
        assertTrue(codificadorContrasenas.matches("clave-de-prueba-123", creada.getPasswordHash()));
    }

    @Test
    void creaCuentasConContrasenaBcryptYRechazaCorreoDuplicado() {
        RolEntidad rolDocente = roles.findByNombreIgnoreCase("Docente").orElseThrow();
        UsuarioAltaFormulario formulario = new UsuarioAltaFormulario();
        formulario.setNombres("  Carla ");
        formulario.setApellidos(" Ríos ");
        formulario.setCorreo(" Carla.Rios@trazalab.local ");
        formulario.setContrasena("clave-de-prueba-123");
        formulario.setRolId(rolDocente.getId());

        servicioUsuarios.crear(formulario);

        UsuarioEntidad creada = usuarios.findByCorreoIgnoreCase("carla.rios@trazalab.local").orElseThrow();
        assertEquals("Carla", creada.getNombres());
        assertEquals("Ríos", creada.getApellidos());
        assertEquals("activo", creada.getEstado());
        assertNotEquals("clave-de-prueba-123", creada.getPasswordHash());
        assertTrue(codificadorContrasenas.matches("clave-de-prueba-123", creada.getPasswordHash()));

        formulario.setCorreo("CARLA.RIOS@TRAZALAB.LOCAL");
        assertThrows(IllegalArgumentException.class, () -> servicioUsuarios.crear(formulario));
    }

    @Test
    void evitaContrasenasQueSuperanElLimiteUtf8YDesactivarAlUltimoAdministrador() {
        RolEntidad rolDocente = roles.findByNombreIgnoreCase("Docente").orElseThrow();
        UsuarioAltaFormulario formulario = new UsuarioAltaFormulario();
        formulario.setNombres("Cuenta");
        formulario.setApellidos("Con límite");
        formulario.setCorreo("limite.contrasena@trazalab.local");
        formulario.setContrasena("é".repeat(37));
        formulario.setRolId(rolDocente.getId());
        assertThrows(IllegalArgumentException.class, () -> servicioUsuarios.crear(formulario));

        UsuarioEntidad administrador = usuarios.findByCorreoIgnoreCase("admin-test@trazalab.local").orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> servicioUsuarios.cambiarEstado(
                administrador.getId(), "otro-administrador@trazalab.local"));
        assertThrows(IllegalArgumentException.class, () -> servicioUsuarios.cambiarEstado(
                administrador.getId(), administrador.getCorreo()));
        assertFalse("inactivo".equalsIgnoreCase(administrador.getEstado()));
    }

    @Test
    void cerrarSesionRegresaAlFormularioDeAcceso() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/login?salio"));
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Revisa antes de guardar")))
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cerrar sesión")))
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

        mockMvc.perform(get("/mantenimientos")
                        .with(user(tecnico.getCorreo()).authorities(new SimpleGrantedAuthority("ROLE_Técnico de Soporte"))))
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
                .andExpect(jsonPath("$.ubicacion").value("A0301 · Torre A, piso 3"))
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
    void buscaElEquipoPorQrYConservaLaEntradaManualDelEscaner() throws Exception {
        mockMvc.perform(get("/equipos/escaneo"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-lector-iniciar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-lector-entrada")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/equipos/buscar")));

        mockMvc.perform(get("/equipos/buscar").param("codigoQr", " "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipos/escaneo"));

        mockMvc.perform(get("/equipos/buscar").param("codigoQr", computadora.getCodigoQr()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipos/QR-TEST-001"));
    }

    @Test
    void fichaDeEquipoMuestraElHistorialTecnicoYLaUbicacion() throws Exception {
        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "La fuente de poder no responde.");

        mockMvc.perform(get("/equipos/{codigoQr}", computadora.getCodigoQr()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Historial técnico")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("La fuente de poder no responde.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("UTP Sede Piura")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar mantenimiento")));
    }

    @Test
    void panelDeDisponibilidadMuestraElEquipoDeLaUbicacionSeleccionada() throws Exception {
        mockMvc.perform(get("/disponibilidad")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Disponibilidad de equipos")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(computadora.getCodigoQr())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A0301 · Torre A, piso 3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 equipo conserva una ubicación anterior")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("QR-LEGACY-001"))));
    }

    @Test
    void panelDeDisponibilidadOmiteAulasSinTorreOPisoYAdvierteQueRequierenValidacion() throws Exception {
        UbicacionEntidad aulaIncompleta = ubicaciones.saveAndFlush(new UbicacionEntidad(
                "A0901", null, sedePiura, null, null, "AULA"));
        computadoras.saveAndFlush(new ComputadoraEntidad(
                "QR-AULA-INCOMPLETA", "SERIE-AULA-INCOMPLETA", "Dell", "OptiPlex", aulaIncompleta, EstadoComputadora.OPERATIVA));

        mockMvc.perform(get("/disponibilidad"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "2 equipos conservan ubicaciones anteriores y requieren validar su aula.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Aula 0901"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("QR-AULA-INCOMPLETA"))));
    }

    @Test
    void panelDeDisponibilidadMuestraSoloLasAulasDelCatalogoYNoSeleccionaUnaPorDefecto() throws Exception {
        assertEquals(List.of(
                        "A0301", "A0302", "A0303", "A0305", "A0307", "A0308", "A0310", "A0311", "A0312",
                        "B0605", "B0606", "B0607", "B0608"),
                ubicaciones.findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc(sedePiura.getNombre()).stream()
                        .filter(ubicacion -> "AULA".equals(ubicacion.getTipo()))
                        .map(UbicacionEntidad::getNombre)
                        .toList());

        mockMvc.perform(get("/disponibilidad"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"edificio\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"piso\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"ubicacionId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Selecciona una torre")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Aula 01")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Aula 08")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("LAB-TEST</option>"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(computadora.getCodigoQr()))));
    }

    @Test
    void aceptaElAulaCuandoCoincideConLaTorreYElPisoSeleccionados() throws Exception {
        mockMvc.perform(get("/disponibilidad")
                        .param("edificio", "Torre A")
                        .param("piso", "3")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A0301 · Torre A, piso 3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(computadora.getCodigoQr())));
    }

    @Test
    void rechazaElAulaCuandoNoCoincideConLaTorreSeleccionada() throws Exception {
        mockMvc.perform(get("/disponibilidad")
                        .param("edificio", "Torre B")
                        .param("piso", "6")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "La combinación de torre, piso y aula no está disponible.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(computadora.getCodigoQr()))));

        mockMvc.perform(get("/disponibilidad")
                        .param("edificio", "Torre A")
                        .param("piso", "6")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "La combinación de torre, piso y aula no está disponible.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(computadora.getCodigoQr()))));
    }

    @Test
    void impideRegistrarDosVecesElMismoCodigoDeAulaEnUnaSede() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO ubicacion (nombre, detalle, sede_id, edificio, piso, tipo) VALUES (?, NULL, ?, ?, ?, 'AULA')",
                "A0301",
                sedePiura.getId(),
                "Torre A",
                3));
    }

    @Test
    void migraDesdeVersionCincoYReasignaSoloElEquipoDemoA0301() throws Exception {
        String esquema = "prueba_rf04_" + UUID.randomUUID().toString().replace("-", "");

        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(esquema)
                    .defaultSchema(esquema)
                    .target(MigrationVersion.fromVersion("5"))
                    .load()
                    .migrate();

            try (Connection conexion = dataSource.getConnection()) {
                try (Statement sentencia = conexion.createStatement()) {
                    sentencia.execute("SET search_path TO \"" + esquema + "\"");
                }

                JdbcTemplate jdbcEsquema = new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
                jdbcEsquema.update(
                        "INSERT INTO computadora (codigo_qr, numero_serie, marca, modelo, ubicacion_id) "
                                + "SELECT ?, ?, ?, ?, ubicacion_id FROM ubicacion WHERE nombre = ?",
                        "PC-001",
                        "TRAZALAB-DEMO-001",
                        "HP",
                        "EliteDesk",
                        "LAB-01");
                jdbcEsquema.update(
                        "INSERT INTO computadora (codigo_qr, numero_serie, marca, modelo, ubicacion_id) "
                                + "SELECT ?, ?, ?, ?, ubicacion_id FROM ubicacion WHERE nombre = ?",
                        "PC-LEGACY-001",
                        "SERIE-LEGACY-001",
                        "Dell",
                        "OptiPlex",
                        "LAB-01");
            }

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(esquema)
                    .defaultSchema(esquema)
                    .load()
                    .migrate();

            try (Connection conexion = dataSource.getConnection()) {
                try (Statement sentencia = conexion.createStatement()) {
                    sentencia.execute("SET search_path TO \"" + esquema + "\"");
                }

                JdbcTemplate jdbcEsquema = new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
                assertEquals("A0301", jdbcEsquema.queryForObject(
                        "SELECT ubicacion.nombre FROM computadora "
                                + "JOIN ubicacion USING (ubicacion_id) WHERE codigo_qr = ?",
                        String.class,
                        "PC-001"));
                assertEquals("LAB-01", jdbcEsquema.queryForObject(
                        "SELECT ubicacion.nombre FROM computadora "
                                + "JOIN ubicacion USING (ubicacion_id) WHERE codigo_qr = ?",
                        String.class,
                        "PC-LEGACY-001"));
            }
        } finally {
            try (Connection conexion = dataSource.getConnection(); Statement sentencia = conexion.createStatement()) {
                sentencia.execute("DROP SCHEMA IF EXISTS \"" + esquema + "\" CASCADE");
            }
        }
    }

    @Test
    void rechazaComoFiltroLasUbicacionesAnterioresAlCatalogoDeAulas() throws Exception {
        Integer ubicacionAnteriorId = ubicaciones.findByNombreIgnoreCaseAndSede_NombreIgnoreCase("LAB-TEST", sedePiura.getNombre())
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/disponibilidad").param("ubicacionId", ubicacionAnteriorId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "La combinación de torre, piso y aula no está disponible.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("QR-LEGACY-001"))));
    }

    @Test
    void panelDeDisponibilidadSeparaLasAulasPorTorre() throws Exception {
        UbicacionEntidad aulaTorreB = ubicaciones.findByNombreIgnoreCaseAndSede_NombreIgnoreCase("B0605", sedePiura.getNombre()).orElseThrow();
        ComputadoraEntidad equipoTorreB = computadoras.saveAndFlush(new ComputadoraEntidad(
                "QR-TORRE-B-001", "SERIE-TORRE-B-001", "Lenovo", "ThinkCentre", aulaTorreB, EstadoComputadora.OPERATIVA));

        mockMvc.perform(get("/disponibilidad").param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(computadora.getCodigoQr())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(equipoTorreB.getCodigoQr()))));

        mockMvc.perform(get("/disponibilidad").param("ubicacionId", aulaTorreB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(equipoTorreB.getCodigoQr())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(computadora.getCodigoQr()))));
    }

    @Test
    void consolidaIncidenciaFallaMantenimientoYComponenteConPaginacion() {
        Integer tipoFallaId = tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc().getFirst().getId();
        IncidenciaRegistradaDto incidencia = servicio.registrar(
                reportante.getId(), computadora.getCodigoQr(), "Equipo reinicia sin aviso.", tipoFallaId);
        Integer fallaId = jdbcTemplate.queryForObject(
                "SELECT falla_id FROM falla WHERE incidencia_id = ?", Integer.class, incidencia.id());

        servicioMantenimiento.registrar(
                tecnico.getId(),
                computadora.getCodigoQr(),
                "correctivo",
                fallaId,
                "Se reemplazó el módulo de memoria.",
                "instalado",
                null,
                "Memoria 16 GB",
                "RAM",
                "RAM-HIST-001");

        Page<EventoHistorialTecnicoDto> primeraPagina = servicioHistorialTecnico.consultar(
                computadora.getCodigoQr(), 0, 2);
        Page<EventoHistorialTecnicoDto> segundaPagina = servicioHistorialTecnico.consultar(
                computadora.getCodigoQr(), 1, 2);
        List<EventoHistorialTecnicoDto> eventos = Stream.concat(
                        primeraPagina.getContent().stream(), segundaPagina.getContent().stream())
                .toList();
        HashSet<String> clavesEvento = new HashSet<>();
        HashSet<String> tiposEvento = new HashSet<>();
        for (EventoHistorialTecnicoDto evento : eventos) {
            clavesEvento.add(evento.tipo() + ":" + evento.id());
            tiposEvento.add(evento.tipo());
        }

        assertEquals(4, primeraPagina.getTotalElements());
        assertEquals(4, segundaPagina.getTotalElements());
        assertEquals(2, primeraPagina.getContent().size());
        assertEquals(2, segundaPagina.getContent().size());
        assertEquals(4, clavesEvento.size());
        assertTrue(tiposEvento.containsAll(List.of("INCIDENCIA", "FALLA", "MANTENIMIENTO", "COMPONENTE")));
        assertTrue(eventos.stream().anyMatch(evento -> evento.descripcion().contains("RAM-HIST-001")));
    }

    @Test
    void limitaElTamanoDePaginaDelHistorialTecnico() {
        Page<EventoHistorialTecnicoDto> paginaNegativa = servicioHistorialTecnico.consultar(
                computadora.getCodigoQr(), -2, 500);
        Page<EventoHistorialTecnicoDto> paginaMinima = servicioHistorialTecnico.consultar(
                computadora.getCodigoQr(), 0, 0);

        assertEquals(0, paginaNegativa.getNumber());
        assertEquals(50, paginaNegativa.getSize());
        assertEquals(1, paginaMinima.getSize());
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
        mockMvc.perform(get("/mantenimientos")
                        .with(user(tecnico.getCorreo()).authorities(new SimpleGrantedAuthority("ROLE_Técnico de Soporte"))))
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

        UbicacionEntidad otraUbicacion = ubicaciones.saveAndFlush(new UbicacionEntidad("LAB-OTRO", "Equipo separado", sedePiura, "Torre A", 4, "LABORATORIO"));
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
