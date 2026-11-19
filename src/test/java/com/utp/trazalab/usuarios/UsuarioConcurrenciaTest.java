package com.utp.trazalab.usuarios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.utp.trazalab.usuarios.model.Rol;
import com.utp.trazalab.usuarios.model.Usuario;
import com.utp.trazalab.usuarios.repository.RolRepository;
import com.utp.trazalab.usuarios.repository.UsuarioRepository;
import com.utp.trazalab.usuarios.service.UsuarioService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {"trazalab.seed.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "logging.file.name="})
@Testcontainers(disabledWithoutDocker = true)
class UsuarioConcurrenciaTest {
    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void configurarPostgres(DynamicPropertyRegistry propiedades) {
        propiedades.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        propiedades.add("spring.datasource.username", POSTGRES::getUsername);
        propiedades.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private UsuarioService servicio;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private RolRepository roles;

    @Test
    void desactivacionesSimultaneasConservanUnAdministradorActivo() throws Exception {
        Rol administrador = roles.findByNombreIgnoreCase("Administrador").orElseThrow();
        Usuario primero = usuarios.saveAndFlush(new Usuario(administrador, "Admin", "Uno", "uno@prueba.local", "activo"));
        Usuario segundo = usuarios.saveAndFlush(new Usuario(administrador, "Admin", "Dos", "dos@prueba.local", "activo"));
        CountDownLatch preparados = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);

        try (var ejecutor = Executors.newFixedThreadPool(2)) {
            var primera = ejecutor.submit(() -> cambiarEstado(primero.getId(), preparados, iniciar));
            var segunda = ejecutor.submit(() -> cambiarEstado(segundo.getId(), preparados, iniciar));
            assertTrue(preparados.await(10, TimeUnit.SECONDS));
            iniciar.countDown();
            assertEquals(1, (primera.get(20, TimeUnit.SECONDS) ? 1 : 0) + (segunda.get(20, TimeUnit.SECONDS) ? 1 : 0));
        }

        assertEquals(1, usuarios.countByEstadoIgnoreCaseAndRol_NombreIgnoreCase("activo", "Administrador"));
    }

    private boolean cambiarEstado(Integer usuarioId, CountDownLatch preparados, CountDownLatch iniciar) throws InterruptedException {
        preparados.countDown();
        if (!iniciar.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("No se iniciaron las operaciones concurrentes.");
        }
        try {
            servicio.cambiarEstado(usuarioId, "operador@prueba.local");
            return true;
        } catch (IllegalArgumentException error) {
            assertEquals("Debe permanecer al menos un administrador activo.", error.getMessage());
            return false;
        }
    }
}
