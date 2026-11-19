package com.utp.trazalab.integration;


import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import com.utp.trazalab.trazabilidad.model.EstadoIncidencia;
import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.model.Incidencia;
import com.utp.trazalab.usuarios.model.Rol;
import com.utp.trazalab.trazabilidad.model.Sede;
import com.utp.trazalab.trazabilidad.model.Ubicacion;
import com.utp.trazalab.usuarios.model.Usuario;
import com.utp.trazalab.trazabilidad.repository.ComputadoraRepository;
import com.utp.trazalab.trazabilidad.repository.ComponenteRepository;
import com.utp.trazalab.trazabilidad.repository.IncidenciaRepository;
import com.utp.trazalab.trazabilidad.repository.TipoFallaRepository;
import com.utp.trazalab.usuarios.repository.RolRepository;
import com.utp.trazalab.trazabilidad.repository.SedeRepository;
import com.utp.trazalab.trazabilidad.repository.UbicacionRepository;
import com.utp.trazalab.usuarios.repository.UsuarioRepository;
import com.utp.trazalab.trazabilidad.service.RegistroIncidencia;
import com.utp.trazalab.trazabilidad.service.MantenimientoService;
import com.utp.trazalab.reportes.service.ReporteService;
import com.utp.trazalab.usuarios.service.UsuarioService;
import com.utp.trazalab.trazabilidad.service.HistorialService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@WithMockUser(username = "responsable@prueba.local", authorities = "ROLE_Responsable")
@Transactional
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
abstract class BaseIntegracion {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurarPostgres(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        propiedades.add("spring.datasource.username", POSTGRES::getUsername);
        propiedades.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired RegistroIncidencia servicio;
    @Autowired RolRepository roles;
    @Autowired SedeRepository sedes;
    @Autowired UsuarioRepository usuarios;
    @Autowired UbicacionRepository ubicaciones;
    @Autowired ComputadoraRepository computadoras;
    @Autowired IncidenciaRepository incidencias;
    @Autowired ComponenteRepository componentes;
    @Autowired TipoFallaRepository tiposFalla;
    @Autowired MantenimientoService servicioMantenimiento;
    @Autowired ReporteService servicioReportes;
    @Autowired UsuarioService servicioUsuarios;
    @Autowired HistorialService servicioHistorialTecnico;
    @Autowired PasswordEncoder codificadorContrasenas;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DataSource dataSource;
    @Autowired MockMvc mockMvc;

    Usuario reportante;
    Usuario tecnico;
    Computadora computadora;
    Sede sedePiura;

    @BeforeEach
    void prepararDatos() {
        Rol rol = roles.findByNombreIgnoreCase("Docente")
                .orElseGet(() -> roles.saveAndFlush(new Rol("Docente", "Reporta incidencias del laboratorio.")));
        reportante = usuarios.saveAndFlush(new Usuario(
                rol, "Ana", "Torres", "responsable@prueba.local", "activo"));
        Rol rolTecnico = roles.findByNombreIgnoreCase("Técnico de Soporte")
                .orElseGet(() -> roles.saveAndFlush(new Rol("Técnico de Soporte", "Pruebas de mantenimiento.")));
        tecnico = usuarios.saveAndFlush(new Usuario(
                rolTecnico, "Luis", "Pérez", "tecnico@prueba.local", "activo"));
        Rol rolAdministrador = roles.findByNombreIgnoreCase("Administrador").orElseThrow();
        usuarios.saveAndFlush(new Usuario(
                rolAdministrador,
                "Admin",
                "Pruebas",
                "admin-test@trazalab.local",
                "activo",
                codificadorContrasenas.encode("clave-segura-de-pruebas")));
        sedePiura = sedes.findByNombreIgnoreCase("UTP Sede Piura").orElseThrow();
        Ubicacion ubicacion = ubicaciones.findByNombreIgnoreCaseAndSede_NombreIgnoreCase("A0301", sedePiura.getNombre()).orElseThrow();
        computadora = computadoras.saveAndFlush(new Computadora("QR-TEST-001", "SERIE-TEST-001", "HP", "Mini", ubicacion, EstadoComputadora.OPERATIVA));
        Ubicacion ubicacionAnterior = ubicaciones.saveAndFlush(new Ubicacion("LAB-TEST", "Prueba de integración", sedePiura, "Torre A", 3, "LABORATORIO"));
        computadoras.saveAndFlush(new Computadora("QR-LEGACY-001", "SERIE-LEGACY-001", "Dell", "OptiPlex", ubicacionAnterior, EstadoComputadora.OPERATIVA));
    }

    void guardarIncidencias(int cantidad) {
        Instant fechaCompartida = Instant.parse("2026-11-05T12:00:00Z");
        for (int numero = 1; numero <= cantidad; numero++) {
            incidencias.saveAndFlush(new Incidencia(
                    computadora,
                    reportante,
                    "Falla número " + numero,
                    fechaCompartida,
                    EstadoIncidencia.REGISTRADA));
        }
    }

    void guardarIncidenciasJdbc(int cantidad) {
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
