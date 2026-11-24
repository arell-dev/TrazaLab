package com.utp.trazalab.app;

import java.util.Map;
import java.util.Optional;

import com.utp.trazalab.operacion.ControlActividad;
import com.utp.trazalab.operacion.EstadoActividad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private static final Logger LOGGER = LoggerFactory.getLogger("trazalab.chequeos");
    private static final long UMBRAL_APAGADO_SEGUNDOS = 1800;
    private final JdbcTemplate jdbcTemplate;
    private final Optional<ControlActividad> controlActividad;

    public HealthController(JdbcTemplate jdbcTemplate, Optional<ControlActividad> controlActividad) {
        this.jdbcTemplate = jdbcTemplate;
        this.controlActividad = controlActividad;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> consultarAplicacion() {
        LOGGER.info("Health check: aplicación disponible");
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/db-health")
    public ResponseEntity<Map<String, String>> consultarBaseDeDatos() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            LOGGER.info("Health check: base de datos disponible");
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (DataAccessException error) {
            LOGGER.error("Health check: base de datos no disponible", error);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
        }
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> consultarEstado() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            LOGGER.info("Health check: aplicación y base de datos disponibles");
            return ResponseEntity.ok(Map.of("estado", "disponible"));
        } catch (DataAccessException error) {
            LOGGER.error("Health check: base de datos no disponible", error);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("estado", "no disponible"));
        }
    }

    @GetMapping("/api/temporizador")
    public ResponseEntity<Map<String, String>> consultarTemporizador() {
        EstadoActividad estado = controlActividad.map(ControlActividad::estado).orElse(null);
        if (estado == null || !estado.relojValido()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Cache-Control", "no-store")
                    .body(Map.of("estado", "no disponible"));
        }

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .header("X-TrazaLab-Clock-Valid", "true")
                .header("X-TrazaLab-Idle-Nanos", Long.toString(estado.inactividadNanos()))
                .header("X-TrazaLab-Request-Active", Boolean.toString(estado.solicitudesActivas() > 0))
                .header("X-TrazaLab-Draining", Boolean.toString(estado.drenando()))
                .header("X-TrazaLab-Timeout-Seconds", Long.toString(UMBRAL_APAGADO_SEGUNDOS))
                .body(Map.of("estado", "disponible"));
    }
}
