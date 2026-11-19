package com.utp.trazalab.trazabilidad.repository;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AsignacionComponenteRepository {
    private final JdbcTemplate jdbcTemplate;

    public AsignacionComponenteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int retirarDeComputadora(Integer computadoraId, Integer componenteId, LocalDateTime ahora) {
        return jdbcTemplate.update(
                "UPDATE computadora_componente SET fecha_retiro = ? "
                        + "WHERE computadora_id = ? AND componente_id = ? AND fecha_retiro IS NULL",
                ahora,
                computadoraId,
                componenteId);
    }

    public void instalarEnComputadora(Integer computadoraId, Integer componenteId, LocalDateTime ahora) {
        jdbcTemplate.update(
                "INSERT INTO computadora_componente "
                        + "(computadora_id, componente_id, fecha_instalacion, fecha_retiro) VALUES (?, ?, ?, NULL)",
                computadoraId,
                componenteId,
                ahora);
    }

    public void enlazarComponente(Integer mantenimientoId, Integer componenteId, String accion) {
        jdbcTemplate.update(
                "INSERT INTO mantenimiento_componente (mantenimiento_id, componente_id, accion, cantidad) "
                        + "VALUES (?, ?, ?, 1)",
                mantenimientoId,
                componenteId,
                accion);
    }

}
