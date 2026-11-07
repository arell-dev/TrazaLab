package com.utp.trazalab.service;

import com.utp.trazalab.dto.FallaResumenDto;
import com.utp.trazalab.dto.MantenimientoResumenDto;
import com.utp.trazalab.exception.ConflictoNegocioException;
import com.utp.trazalab.exception.RecursoNoEncontradoException;
import com.utp.trazalab.persistence.jpa.entity.ComponenteEntidad;
import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import com.utp.trazalab.persistence.jpa.entity.MantenimientoEntidad;
import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.ComponenteJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.ComputadoraJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.MantenimientoJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroMantenimientoServicio {
    private static final List<String> ACCIONES_COMPONENTE = List.of(
            "intervenido", "instalado", "reemplazado", "retirado");

    private final UsuarioJpaRepositorio usuarios;
    private final ComputadoraJpaRepositorio computadoras;
    private final ComponenteJpaRepositorio componentes;
    private final MantenimientoJpaRepositorio mantenimientos;
    private final JdbcTemplate jdbcTemplate;
    private final Clock reloj;

    public RegistroMantenimientoServicio(UsuarioJpaRepositorio usuarios, ComputadoraJpaRepositorio computadoras, ComponenteJpaRepositorio componentes, MantenimientoJpaRepositorio mantenimientos, JdbcTemplate jdbcTemplate, Clock reloj) {
        this.usuarios = usuarios;
        this.computadoras = computadoras;
        this.componentes = componentes;
        this.mantenimientos = mantenimientos;
        this.jdbcTemplate = jdbcTemplate;
        this.reloj = reloj;
    }

    @Transactional
    public MantenimientoResumenDto registrar(Integer usuarioId, String codigoQr, String tipo, Integer fallaId, String descripcion, String accionComponente, String serieComponenteActual, String nombreComponenteNuevo, String tipoComponenteNuevo, String serieComponenteNuevo) {
        UsuarioEntidad usuario = usuarios.findById(usuarioId)
                .filter(persona -> "activo".equalsIgnoreCase(persona.getEstado()))
                .orElseThrow(() -> new IllegalArgumentException("El usuario de demostración no existe o está inactivo."));
        String rol = usuario.getRol().getNombre();
        if (!"Técnico de Soporte".equalsIgnoreCase(rol)
                && !"Técnico de Laboratorio".equalsIgnoreCase(rol)) {
            throw new IllegalArgumentException("La identidad de demostración del mantenimiento debe tener un rol técnico.");
        }
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new IllegalArgumentException("Ingresa el código QR del equipo.");
        }
        if (tipo == null || !(tipo.equals("preventivo") || tipo.equals("correctivo"))) {
            throw new IllegalArgumentException("Selecciona mantenimiento preventivo o correctivo.");
        }
        if (descripcion == null || descripcion.isBlank() || descripcion.trim().length() > 1000) {
            throw new IllegalArgumentException("La descripción debe tener entre 1 y 1000 caracteres.");
        }

        ComputadoraEntidad computadora = computadoras.findByCodigoQrIgnoreCase(codigoQr.trim())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una computadora con ese código QR."));
        if (tipo.equals("correctivo") && fallaId == null) {
            throw new IllegalArgumentException("Un mantenimiento correctivo debe asociarse a una falla.");
        }
        if (fallaId != null) {
            Boolean fallaDelEquipo = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM falla f JOIN incidencia i "
                            + "ON i.incidencia_id = f.incidencia_id "
                            + "WHERE f.falla_id = ? AND i.computadora_id = ?)",
                    Boolean.class,
                    fallaId,
                    computadora.getId());
            if (!Boolean.TRUE.equals(fallaDelEquipo)) {
                throw new ConflictoNegocioException(
                        "La falla seleccionada no existe o pertenece a otro equipo.");
            }
        }
        LocalDateTime ahora = LocalDateTime.now(reloj);
        MantenimientoEntidad mantenimiento = mantenimientos.saveAndFlush(new MantenimientoEntidad(
                computadora, usuario, fallaId, tipo, descripcion.trim(), ahora));

        String accion = normalizarAccion(accionComponente);
        if (accion != null) {
            registrarAccionComponente(
                    mantenimiento.getId(),
                    computadora.getId(),
                    accion,
                    serieComponenteActual,
                    nombreComponenteNuevo,
                    tipoComponenteNuevo,
                    serieComponenteNuevo,
                    ahora);
        }

        return new MantenimientoResumenDto(
                mantenimiento.getId(),
                computadora.getCodigoQr(),
                usuario.getNombres() + " " + usuario.getApellidos(),
                mantenimiento.getTipo(),
                mantenimiento.getDescripcion(),
                mantenimiento.getFecha());
    }

    @Transactional(readOnly = true)
    public List<MantenimientoResumenDto> consultarRecientes() {
        return mantenimientos.findTop50ByOrderByFechaDescIdDesc().stream()
                .map(mantenimiento -> new MantenimientoResumenDto(
                        mantenimiento.getId(),
                        mantenimiento.getComputadora().getCodigoQr(),
                        mantenimiento.getUsuario().getNombres() + " " + mantenimiento.getUsuario().getApellidos(),
                        mantenimiento.getTipo(),
                        mantenimiento.getDescripcion(),
                        mantenimiento.getFecha()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FallaResumenDto> consultarFallasRecientes() {
        return jdbcTemplate.query("""
                SELECT f.falla_id, c.codigo_qr, tf.nombre AS tipo_falla,
                       f.descripcion_tecnica, f.fecha_registro
                FROM falla f
                JOIN incidencia i ON i.incidencia_id = f.incidencia_id
                JOIN computadora c ON c.computadora_id = i.computadora_id
                JOIN tipo_falla tf ON tf.tipo_falla_id = f.tipo_falla_id
                ORDER BY f.fecha_registro DESC, f.falla_id DESC
                LIMIT 50
                """, (resultado, numeroFila) -> new FallaResumenDto(
                resultado.getInt("falla_id"),
                resultado.getString("codigo_qr"),
                resultado.getString("tipo_falla"),
                resultado.getString("descripcion_tecnica"),
                resultado.getObject("fecha_registro", LocalDateTime.class)));
    }

    private void registrarAccionComponente(Integer mantenimientoId, Integer computadoraId, String accion, String serieActual, String nombreNuevo, String tipoNuevo, String serieNueva, LocalDateTime ahora) {
        if (!ACCIONES_COMPONENTE.contains(accion)) {
            throw new IllegalArgumentException("Selecciona una acción válida para el componente.");
        }

        if (accion.equals("intervenido") || accion.equals("retirado") || accion.equals("reemplazado")) {
            ComponenteEntidad actual = buscarComponenteActual(serieActual, computadoraId);
            if (accion.equals("intervenido")) {
                enlazarComponente(mantenimientoId, actual.getId(), accion);
                return;
            }

            retirarDeComputadora(computadoraId, actual.getId(), ahora);
            enlazarComponente(mantenimientoId, actual.getId(), accion);
            if (accion.equals("retirado")) {
                return;
            }
        }

        ComponenteEntidad nuevo = crearComponenteNuevo(nombreNuevo, tipoNuevo, serieNueva);
        if (estaAsignado(nuevo.getId())) {
            throw new ConflictoNegocioException("El componente ya está instalado en otra computadora.");
        }
        instalarEnComputadora(computadoraId, nuevo.getId(), ahora);
        enlazarComponente(mantenimientoId, nuevo.getId(), "instalado");
    }

    private ComponenteEntidad buscarComponenteActual(String numeroSerie, Integer computadoraId) {
        if (numeroSerie == null || numeroSerie.isBlank()) {
            throw new IllegalArgumentException("Ingresa la serie del componente instalado que se va a intervenir.");
        }
        ComponenteEntidad componente = componentes.findByNumeroSerieIgnoreCase(numeroSerie.trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un componente con esa serie."));
        Boolean asignado = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM computadora_componente "
                        + "WHERE computadora_id = ? AND componente_id = ? AND fecha_retiro IS NULL)",
                Boolean.class,
                computadoraId,
                componente.getId());
        if (!Boolean.TRUE.equals(asignado)) {
            throw new RecursoNoEncontradoException(
                    "El componente no está instalado actualmente en el equipo indicado.");
        }
        return componente;
    }

    private ComponenteEntidad crearComponenteNuevo(String nombre, String tipo, String serie) {
        if (nombre == null || nombre.isBlank() || tipo == null || tipo.isBlank()
                || serie == null || serie.isBlank()) {
            throw new IllegalArgumentException("Para instalar un componente, completa nombre, tipo y número de serie.");
        }
        if (nombre.trim().length() > 100 || tipo.trim().length() > 60 || serie.trim().length() > 80) {
            throw new IllegalArgumentException("Los datos del componente superan la longitud permitida.");
        }
        if (componentes.findByNumeroSerieIgnoreCase(serie.trim()).isPresent()) {
            throw new ConflictoNegocioException("Ya existe un componente registrado con ese número de serie.");
        }
        return componentes.saveAndFlush(new ComponenteEntidad(nombre.trim(), tipo.trim(), serie.trim()));
    }

    private boolean estaAsignado(Integer componenteId) {
        Boolean asignado = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM computadora_componente "
                        + "WHERE componente_id = ? AND fecha_retiro IS NULL)",
                Boolean.class,
                componenteId);
        return Boolean.TRUE.equals(asignado);
    }

    private void retirarDeComputadora(Integer computadoraId, Integer componenteId, LocalDateTime ahora) {
        jdbcTemplate.update(
                "UPDATE computadora_componente SET fecha_retiro = ? "
                        + "WHERE computadora_id = ? AND componente_id = ? AND fecha_retiro IS NULL",
                ahora,
                computadoraId,
                componenteId);
    }

    private void instalarEnComputadora(Integer computadoraId, Integer componenteId, LocalDateTime ahora) {
        jdbcTemplate.update(
                "INSERT INTO computadora_componente "
                        + "(computadora_id, componente_id, fecha_instalacion, fecha_retiro) VALUES (?, ?, ?, NULL)",
                computadoraId,
                componenteId,
                ahora);
    }

    private void enlazarComponente(Integer mantenimientoId, Integer componenteId, String accion) {
        jdbcTemplate.update(
                "INSERT INTO mantenimiento_componente (mantenimiento_id, componente_id, accion, cantidad) "
                        + "VALUES (?, ?, ?, 1)",
                mantenimientoId,
                componenteId,
                accion);
    }

    private String normalizarAccion(String accion) {
        if (accion == null || accion.isBlank()) {
            return null;
        }
        return accion.trim().toLowerCase(Locale.ROOT);
    }
}
