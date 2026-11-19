package com.utp.trazalab.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class HealthControllerTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final HealthController controller = new HealthController(jdbcTemplate);

    @Test
    void informaAplicacionDisponibleSinConsultarLaBaseDeDatos() {
        ResponseEntity<Map<String, String>> respuesta = controller.consultarAplicacion();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(Map.of("status", "UP"), respuesta.getBody());
        org.mockito.Mockito.verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void informaEstadoDeBaseDeDatosDisponible() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        ResponseEntity<Map<String, String>> respuesta = controller.consultarBaseDeDatos();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(Map.of("status", "UP"), respuesta.getBody());
    }

    @Test
    void informaEstadoDeBaseDeDatosNoDisponible() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("detalle interno"));

        ResponseEntity<Map<String, String>> respuesta = controller.consultarBaseDeDatos();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respuesta.getStatusCode());
        assertEquals(Map.of("status", "DOWN"), respuesta.getBody());
    }

    @Test
    void informaServicioDisponibleCuandoRespondeLaBaseDeDatos() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        ResponseEntity<Map<String, String>> respuesta = controller.consultarEstado();

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        assertEquals(Map.of("estado", "disponible"), respuesta.getBody());
    }

    @Test
    void informaServicioNoDisponibleCuandoFallaLaBaseDeDatos() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("detalle interno"));

        ResponseEntity<Map<String, String>> respuesta = controller.consultarEstado();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, respuesta.getStatusCode());
        assertEquals(Map.of("estado", "no disponible"), respuesta.getBody());
    }
}
