package com.utp.trazalab.reportes.repository;

import com.utp.trazalab.reportes.dto.FallaRecurrenteDto;
import com.utp.trazalab.reportes.dto.DistribucionCategoriaDto;
import com.utp.trazalab.reportes.dto.ResumenReporteDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReporteRepository {
    private final JdbcTemplate jdbcTemplate;

    public ReporteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FallaRecurrenteDto> consultarRecurrencias(LocalDate desde, LocalDate hasta, String codigoQr, Integer tipoFallaId) {
        List<Object> parametros = new ArrayList<>();
        String sql = consultaAgrupada(desde, hasta, codigoQr, tipoFallaId, parametros);
        sql += " ORDER BY frecuencia DESC, codigo_qr, tipo_falla, computadora_id, tipo_falla_id";

        return jdbcTemplate.query(sql, this::mapearFalla, parametros.toArray());
    }

    public ResumenReporteDto consultarResumen(LocalDate desde, LocalDate hasta, String codigoQr, Integer tipoFallaId) {
        List<Object> parametros = new ArrayList<>();
        String agrupada = consultaAgrupada(desde, hasta, codigoQr, tipoFallaId, parametros);
        String resumenSql = "SELECT COUNT(*) AS patrones, COUNT(DISTINCT computadora_id) AS equipos, "
                + "COALESCE(SUM(frecuencia), 0) AS eventos FROM (" + agrupada + ") recurrencias";
        ResumenReporteDto resumen = jdbcTemplate.queryForObject(resumenSql, (resultado, numeroFila) -> new ResumenReporteDto(
                resultado.getLong("patrones"), resultado.getLong("equipos"), resultado.getLong("eventos"), List.of()), parametros.toArray());
        String categoriasSql = """
                WITH recurrencias AS (
                """ + agrupada + """
                ), ordenadas AS (
                    SELECT categoria_falla, frecuencia,
                           ROW_NUMBER() OVER (ORDER BY frecuencia DESC, codigo_qr, tipo_falla, computadora_id, tipo_falla_id) AS orden
                    FROM recurrencias
                )
                SELECT categoria_falla, SUM(frecuencia) AS frecuencia
                FROM ordenadas
                GROUP BY categoria_falla
                ORDER BY MIN(orden)
                """;
        List<DistribucionCategoriaDto> categorias = jdbcTemplate.query(categoriasSql, (resultado, numeroFila) -> {
            long frecuencia = resultado.getLong("frecuencia");
            int porcentaje = resumen.cantidadEventos() == 0 ? 0 : (int) Math.round(frecuencia * 100.0 / resumen.cantidadEventos());
            return new DistribucionCategoriaDto(resultado.getString("categoria_falla"), frecuencia, porcentaje);
        }, parametros.toArray());
        return new ResumenReporteDto(resumen.cantidadPatrones(), resumen.equiposAfectados(), resumen.cantidadEventos(), categorias);
    }

    public List<FallaRecurrenteDto> consultarPagina(LocalDate desde, LocalDate hasta, String codigoQr, Integer tipoFallaId, int pagina, int tamano) {
        List<Object> parametros = new ArrayList<>();
        String sql = consultaAgrupada(desde, hasta, codigoQr, tipoFallaId, parametros);
        sql += " ORDER BY frecuencia DESC, codigo_qr, tipo_falla, computadora_id, tipo_falla_id LIMIT ? OFFSET ?";
        parametros.add(tamano);
        parametros.add((long) pagina * tamano);
        return jdbcTemplate.query(sql, this::mapearFalla, parametros.toArray());
    }

    private FallaRecurrenteDto mapearFalla(ResultSet resultado, int numeroFila) throws SQLException {
        return new FallaRecurrenteDto(
                resultado.getInt("computadora_id"),
                resultado.getString("codigo_qr"),
                resultado.getString("numero_serie"),
                resultado.getString("categoria_falla"),
                resultado.getInt("tipo_falla_id"),
                resultado.getString("tipo_falla"),
                resultado.getLong("frecuencia"),
                resultado.getObject("ultima_identificacion", LocalDateTime.class));
    }

    private String consultaAgrupada(LocalDate desde, LocalDate hasta, String codigoQr, Integer tipoFallaId, List<Object> parametros) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.computadora_id, c.codigo_qr, c.numero_serie,
                       cf.nombre AS categoria_falla, tf.tipo_falla_id, tf.nombre AS tipo_falla,
                       COUNT(*) AS frecuencia, MAX(f.fecha_registro) AS ultima_identificacion
                FROM falla f
                JOIN incidencia i ON i.incidencia_id = f.incidencia_id
                JOIN computadora c ON c.computadora_id = i.computadora_id
                JOIN tipo_falla tf ON tf.tipo_falla_id = f.tipo_falla_id
                JOIN categoria_falla cf ON cf.categoria_falla_id = tf.categoria_falla_id
                WHERE f.fecha_registro >= ? AND f.fecha_registro < ?
                """);
        parametros.add(desde.atStartOfDay());
        parametros.add(hasta.plusDays(1).atStartOfDay());
        if (codigoQr != null && !codigoQr.isBlank()) {
            sql.append(" AND UPPER(c.codigo_qr) = UPPER(?) ");
            parametros.add(codigoQr.trim());
        }
        if (tipoFallaId != null) {
            sql.append(" AND tf.tipo_falla_id = ? ");
            parametros.add(tipoFallaId);
        }
        sql.append("""
                 GROUP BY c.computadora_id, c.codigo_qr, c.numero_serie,
                          cf.nombre, tf.tipo_falla_id, tf.nombre
                HAVING COUNT(*) >= 2
                """);

        return sql.toString();
    }

}
