package com.utp.trazalab.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.utp.trazalab.exception.ConflictoNegocioException;
import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import com.utp.trazalab.trazabilidad.model.Ubicacion;
import com.utp.trazalab.trazabilidad.repository.ComputadoraRepository;
import com.utp.trazalab.trazabilidad.repository.AsignacionComponenteRepository;
import com.utp.trazalab.trazabilidad.repository.UbicacionRepository;
import com.utp.trazalab.trazabilidad.service.MantenimientoService;
import com.utp.trazalab.usuarios.model.Rol;
import com.utp.trazalab.usuarios.model.Usuario;
import com.utp.trazalab.usuarios.repository.RolRepository;
import com.utp.trazalab.usuarios.repository.UsuarioRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"trazalab.seed.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "logging.file.name="})
@Testcontainers(disabledWithoutDocker = true)
class RetiroComponenteConcurrenciaTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurarPostgres(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        propiedades.add("spring.datasource.username", POSTGRES::getUsername);
        propiedades.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MantenimientoService mantenimientos;
    @Autowired private RolRepository roles;
    @Autowired private UsuarioRepository usuarios;
    @Autowired private UbicacionRepository ubicaciones;
    @Autowired private ComputadoraRepository computadoras;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoSpyBean private AsignacionComponenteRepository asignaciones;

    @Test
    void retirosSimultaneosRegistranUnaSolaIntervencion() throws Exception {
        Rol rol = roles.findByNombreIgnoreCase("Técnico de Soporte").orElseThrow();
        Usuario tecnico = usuarios.saveAndFlush(new Usuario(rol, "Tecnico", "Prueba", "tecnico@prueba.local", "activo"));
        Ubicacion ubicacion = ubicaciones.findByNombreIgnoreCaseAndSede_NombreIgnoreCase("A0301", "UTP Sede Piura").orElseThrow();
        Computadora computadora = computadoras.saveAndFlush(new Computadora("QR-CONCURRENCIA", "SERIE-CONCURRENCIA", "HP", "Mini", ubicacion, EstadoComputadora.OPERATIVA));
        mantenimientos.registrar(tecnico.getId(), computadora.getCodigoQr(), "preventivo", null, "Instalación inicial", "instalado", null, "Memoria", "RAM", "RAM-CONCURRENCIA");
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        CyclicBarrier antesDelRetiro = new CyclicBarrier(2);
        doAnswer(invocacion -> {
            antesDelRetiro.await(10, TimeUnit.SECONDS);
            return invocacion.callRealMethod();
        }).when(asignaciones).retirarDeComputadora(any(), any(), any());

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primero = ejecutor.submit(() -> retirar(tecnico.getId(), computadora.getCodigoQr(), preparados, iniciar));
            var segundo = ejecutor.submit(() -> retirar(tecnico.getId(), computadora.getCodigoQr(), preparados, iniciar));
            assertTrue(preparados.await(10, TimeUnit.SECONDS));
            iniciar.countDown();
            assertEquals(1, (primero.get(20, TimeUnit.SECONDS) ? 1 : 0) + (segundo.get(20, TimeUnit.SECONDS) ? 1 : 0));
        }

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM computadora_componente WHERE computadora_id = ? AND fecha_retiro IS NOT NULL", Integer.class, computadora.getId()));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mantenimiento_componente WHERE accion = 'retirado'", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mantenimiento", Integer.class));
    }

    private boolean retirar(Integer usuarioId, String codigoQr, CountDownLatch preparados, CountDownLatch iniciar) throws InterruptedException {
        preparados.countDown();
        if (!iniciar.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("No se iniciaron los retiros concurrentes.");
        }
        try {
            mantenimientos.registrar(usuarioId, codigoQr, "preventivo", null, "Retiro concurrente", "retirado", "RAM-CONCURRENCIA", null, null, null);
            return true;
        } catch (ConflictoNegocioException error) {
            assertEquals("El componente ya no está instalado en el equipo indicado.", error.getMessage());
            return false;
        }
    }
}
