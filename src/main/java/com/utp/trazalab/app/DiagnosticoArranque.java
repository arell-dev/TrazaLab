package com.utp.trazalab.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticoArranque {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticoArranque.class);

    private final JdbcTemplate jdbcTemplate;

    public DiagnosticoArranque(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verificarConexionPostgresql() {
        String nombreBaseDatos = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        LOGGER.info("Conexión a PostgreSQL (base de datos: {}): OK", nombreBaseDatos);
    }
}
