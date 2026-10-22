package com.utp.trazalab.service;

import com.utp.trazalab.model.EstadoIncidencia;
import com.utp.trazalab.model.ComputadoraEntidad;
import com.utp.trazalab.model.IncidenciaEntidad;
import com.utp.trazalab.model.UsuarioEntidad;
import com.utp.trazalab.repository.ComputadoraJpaRepositorio;
import com.utp.trazalab.repository.IncidenciaJpaRepositorio;
import com.utp.trazalab.repository.UsuarioJpaRepositorio;
import com.utp.trazalab.dto.HistorialComputadoraDto;
import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.dto.UsuarioResumenDto;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroIncidenciaPersistenteServicio {
    private final UsuarioJpaRepositorio usuarios;
    private final ComputadoraJpaRepositorio computadoras;
    private final IncidenciaJpaRepositorio incidencias;
    private final Clock reloj;

    public RegistroIncidenciaPersistenteServicio(UsuarioJpaRepositorio usuarios, ComputadoraJpaRepositorio computadoras, IncidenciaJpaRepositorio incidencias, Clock reloj) {
        this.usuarios = usuarios;
        this.computadoras = computadoras;
        this.incidencias = incidencias;
        this.reloj = reloj;
    }

    @Transactional
    public IncidenciaRegistradaDto registrar(long usuarioId, String codigoQr, String descripcion) {
        UsuarioEntidad usuario = usuarios.findById(Math.toIntExact(usuarioId))
                .orElseThrow(() -> new IllegalArgumentException("El usuario indicado no existe."));
        if (!"activo".equalsIgnoreCase(usuario.getEstado())) {
            throw new IllegalArgumentException("El usuario está inactivo.");
        }
        if (!rolPuedeReportar(usuario.getRol().getNombre())) {
            throw new IllegalArgumentException("El rol del usuario no puede registrar incidencias.");
        }
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new IllegalArgumentException("Ingresa un código QR de computadora.");
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripción de la incidencia es obligatoria.");
        }
        if (descripcion.trim().length() > 500) {
            throw new IllegalArgumentException("La descripción no puede superar 500 caracteres.");
        }

        ComputadoraEntidad computadora = computadoras.findByCodigoQrIgnoreCase(codigoQr.trim())
                .orElseThrow(() -> new IllegalArgumentException("No existe una computadora con ese código QR."));
        IncidenciaEntidad guardada = incidencias.saveAndFlush(new IncidenciaEntidad(
                computadora, usuario, descripcion.trim(), Instant.now(reloj), EstadoIncidencia.REGISTRADA));
        return convertirIncidencia(guardada);
    }

    @Transactional(readOnly = true)
    public List<IncidenciaRegistradaDto> consultarIncidencias() {
        return incidencias.findAllByOrderByFechaRegistroDesc().stream()
                .map(this::convertirIncidencia)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UsuarioResumenDto> consultarUsuarios() {
        return usuarios.findAll().stream()
                .map(usuario -> new UsuarioResumenDto(
                        usuario.getId(),
                        usuario.getNombres(),
                        usuario.getApellidos(),
                        usuario.getRol().getNombre(),
                        usuario.getEstado()))
                .toList();
    }

    @Transactional(readOnly = true)
    public HistorialComputadoraDto consultarHistorialComputadora(String codigoQr) {
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new IllegalArgumentException("Ingresa el código QR de la computadora.");
        }

        ComputadoraEntidad computadora = computadoras.findByCodigoQrIgnoreCase(codigoQr.trim())
                .orElseThrow(() -> new IllegalArgumentException("No existe una computadora con ese código QR."));
        List<IncidenciaRegistradaDto> historial = incidencias
                .findAllByComputadora_IdOrderByFechaRegistroAsc(computadora.getId()).stream()
                .map(this::convertirIncidencia)
                .toList();
        String ubicacion = computadora.getUbicacion().getNombre();
        String detalle = computadora.getUbicacion().getDetalle();
        if (detalle != null && !detalle.isBlank()) {
            ubicacion += " / " + detalle;
        }

        return new HistorialComputadoraDto(
                computadora.getId(),
                computadora.getCodigoQr(),
                computadora.getNumeroSerie(),
                computadora.getMarca(),
                computadora.getModelo(),
                ubicacion,
                computadora.getEstado().etiqueta(),
                historial);
    }

    private IncidenciaRegistradaDto convertirIncidencia(IncidenciaEntidad incidencia) {
        return new IncidenciaRegistradaDto(
                incidencia.getId(),
                incidencia.getUsuario().getId(),
                incidencia.getUsuario().getNombres() + " " + incidencia.getUsuario().getApellidos(),
                incidencia.getComputadora().getCodigoQr(),
                incidencia.getDescripcion(),
                incidencia.getFechaRegistro(),
                incidencia.getEstado().name().toLowerCase(Locale.ROOT));
    }

    private boolean rolPuedeReportar(String nombreRol) {
        String normalizado = nombreRol.toLowerCase(Locale.ROOT);
        return normalizado.equals("docente") || normalizado.equals("responsable")
                || normalizado.equals("técnico de soporte") || normalizado.equals("técnico de laboratorio");
    }
}
