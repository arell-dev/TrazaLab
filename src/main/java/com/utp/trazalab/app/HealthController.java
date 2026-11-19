package com.utp.trazalab.app;

import java.util.Map;
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
    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
}
