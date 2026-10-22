package com.utp.trazalab.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.utp.trazalab.model.EstadoComputadora;
import com.utp.trazalab.model.ComputadoraEntidad;
import com.utp.trazalab.model.IncidenciaEntidad;
import com.utp.trazalab.model.RolEntidad;
import com.utp.trazalab.model.UbicacionEntidad;
import com.utp.trazalab.model.UsuarioEntidad;
import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.repository.ComputadoraJpaRepositorio;
import com.utp.trazalab.repository.IncidenciaJpaRepositorio;
import com.utp.trazalab.repository.RolJpaRepositorio;
import com.utp.trazalab.repository.UbicacionJpaRepositorio;
import com.utp.trazalab.repository.UsuarioJpaRepositorio;
import com.utp.trazalab.service.RegistroIncidenciaPersistenteServicio;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "trazalab.console.enabled=false",
        "trazalab.seed.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.file.name="
})
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

    private UsuarioEntidad reportante;
    private ComputadoraEntidad computadora;

    @BeforeEach
    void prepararDatos() {
        RolEntidad rol = roles.saveAndFlush(new RolEntidad("Docente", "Reporta incidencias del laboratorio."));
        reportante = usuarios.saveAndFlush(new UsuarioEntidad(rol, "Ana", "Torres", "ana@pruebas.local", "activo"));
        UbicacionEntidad ubicacion = ubicaciones.saveAndFlush(new UbicacionEntidad("LAB-TEST", "Prueba de integración"));
        computadora = computadoras.saveAndFlush(new ComputadoraEntidad("QR-TEST-001", "SERIE-TEST-001", "HP", "Mini", ubicacion, EstadoComputadora.OPERATIVA));
    }

    @Test
    void registraYRecuperaLaIncidenciaEnPostgresql() {
        IncidenciaRegistradaDto creada = servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "No enciende.");

        List<IncidenciaEntidad> guardadas = incidencias.findAllByComputadora_IdOrderByFechaRegistroAsc(computadora.getId());
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
}
