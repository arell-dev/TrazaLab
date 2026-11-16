package com.utp.trazalab.service;

import com.utp.trazalab.dto.EventoHistorialTecnicoDto;
import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import com.utp.trazalab.persistence.jpa.springdata.ComputadoraJpaRepositorio;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistorialTecnicoConsultaServicio {
    private static final int TAMANO_PAGINA_MAXIMO = 50;

    private final ComputadoraJpaRepositorio computadoras;
    private final JdbcTemplate jdbcTemplate;
    private final String sedeActiva;

    public HistorialTecnicoConsultaServicio(ComputadoraJpaRepositorio computadoras, JdbcTemplate jdbcTemplate, @Value("${trazalab.sede-activa}") String sedeActiva) {
        this.computadoras = computadoras;
        this.jdbcTemplate = jdbcTemplate;
        this.sedeActiva = sedeActiva;
    }

    @Transactional(readOnly = true)
    public Page<EventoHistorialTecnicoDto> consultar(String codigoQr, int pagina, int tamano) {
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new IllegalArgumentException("Ingresa el código QR de la computadora.");
        }

        ComputadoraEntidad computadora = computadoras.findByCodigoQrIgnoreCase(codigoQr.strip())
                .filter(equipo -> equipo.getUbicacion().getSede().getNombre().equalsIgnoreCase(sedeActiva))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el equipo en la sede activa."));
        int numeroPagina = Math.max(0, pagina);
        int limite = Math.max(1, Math.min(tamano, TAMANO_PAGINA_MAXIMO));
        PageRequest pageable = PageRequest.of(numeroPagina, limite);
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM (
                    SELECT i.incidencia_id
                    FROM incidencia i
                    WHERE i.computadora_id = ?
                    UNION ALL
                    SELECT f.falla_id
                    FROM falla f
                    JOIN incidencia i ON i.incidencia_id = f.incidencia_id
                    WHERE i.computadora_id = ?
                    UNION ALL
                    SELECT m.mantenimiento_id
                    FROM mantenimiento m
                    WHERE m.computadora_id = ?
                    UNION ALL
                    SELECT mc.mantenimiento_id
                    FROM mantenimiento_componente mc
                    JOIN mantenimiento m ON m.mantenimiento_id = mc.mantenimiento_id
                    WHERE m.computadora_id = ?
                ) historial
                """, Long.class, computadora.getId(), computadora.getId(), computadora.getId(), computadora.getId());

        List<EventoHistorialTecnicoDto> contenido = jdbcTemplate.query("""
                SELECT historial.id, historial.tipo, historial.titulo, historial.descripcion,
                       historial.estado, historial.fecha
                FROM (
                    SELECT i.incidencia_id::BIGINT AS id,
                           'INCIDENCIA'::VARCHAR AS tipo,
                           'Incidencia reportada'::VARCHAR AS titulo,
                           i.descripcion::VARCHAR AS descripcion,
                           i.estado::VARCHAR AS estado,
                           i.fecha_registro AS fecha
                    FROM incidencia i
                    WHERE i.computadora_id = ?
                    UNION ALL
                    SELECT f.falla_id::BIGINT AS id,
                           'FALLA'::VARCHAR AS tipo,
                           tf.nombre::VARCHAR AS titulo,
                           f.descripcion_tecnica::VARCHAR AS descripcion,
                           'Diagnosticada'::VARCHAR AS estado,
                           f.fecha_registro AS fecha
                    FROM falla f
                    JOIN incidencia i ON i.incidencia_id = f.incidencia_id
                    JOIN tipo_falla tf ON tf.tipo_falla_id = f.tipo_falla_id
                    WHERE i.computadora_id = ?
                    UNION ALL
                    SELECT m.mantenimiento_id::BIGINT AS id,
                           'MANTENIMIENTO'::VARCHAR AS tipo,
                           ('Mantenimiento ' || m.tipo)::VARCHAR AS titulo,
                           m.descripcion::VARCHAR AS descripcion,
                           'Registrado'::VARCHAR AS estado,
                           m.fecha AS fecha
                    FROM mantenimiento m
                    WHERE m.computadora_id = ?
                    UNION ALL
                    SELECT (m.mantenimiento_id::BIGINT * 1000000 + mc.componente_id::BIGINT) AS id,
                           'COMPONENTE'::VARCHAR AS tipo,
                           ('Componente ' || mc.accion)::VARCHAR AS titulo,
                           concat_ws(' · ', c.nombre, c.tipo, c.numero_serie)::VARCHAR AS descripcion,
                           mc.accion::VARCHAR AS estado,
                           m.fecha AS fecha
                    FROM mantenimiento_componente mc
                    JOIN mantenimiento m ON m.mantenimiento_id = mc.mantenimiento_id
                    JOIN componente c ON c.componente_id = mc.componente_id
                    WHERE m.computadora_id = ?
                ) historial
                ORDER BY historial.fecha DESC, historial.tipo ASC, historial.id DESC
                LIMIT ? OFFSET ?
                """, this::mapearEvento, computadora.getId(), computadora.getId(), computadora.getId(), computadora.getId(), limite, pageable.getOffset());

        return new PageImpl<>(contenido, pageable, total == null ? 0 : total);
    }

    private EventoHistorialTecnicoDto mapearEvento(ResultSet resultado, int numeroFila) throws SQLException {
        return new EventoHistorialTecnicoDto(
                resultado.getLong("id"),
                resultado.getString("tipo"),
                resultado.getString("titulo"),
                resultado.getString("descripcion"),
                resultado.getString("estado"),
                resultado.getObject("fecha", LocalDateTime.class));
    }
}
