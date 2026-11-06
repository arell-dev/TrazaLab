package com.utp.trazalab.controller;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class EstadoApiController {
    private final JdbcTemplate jdbcTemplate;

    public EstadoApiController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<Map<String, String>> consultarEstado() {
        jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return ResponseEntity.ok(Map.of("estado", "disponible"));
    }
}
