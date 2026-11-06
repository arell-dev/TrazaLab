package com.utp.trazalab.service;

import com.utp.trazalab.dto.HistorialComputadoraDto;
import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.dto.IncidenciasExportacionDto;
import com.utp.trazalab.dto.UsuarioResumenDto;
import com.utp.trazalab.domain.enums.EstadoIncidencia;
import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import com.utp.trazalab.persistence.jpa.entity.IncidenciaEntidad;
import com.utp.trazalab.persistence.jpa.entity.FallaEntidad;
import com.utp.trazalab.persistence.jpa.entity.TipoFallaEntidad;
import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.ComputadoraJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.FallaJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.IncidenciaJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.TipoFallaJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroIncidenciaPersistenteServicio {
    private static final int TAMANO_LOTE_EXPORTACION = 250;
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    private final UsuarioJpaRepositorio usuarios;
    private final ComputadoraJpaRepositorio computadoras;
    private final IncidenciaJpaRepositorio incidencias;
    private final FallaJpaRepositorio fallas;
    private final TipoFallaJpaRepositorio tiposFalla;
    private final Clock reloj;

    public RegistroIncidenciaPersistenteServicio(
            UsuarioJpaRepositorio usuarios,
            ComputadoraJpaRepositorio computadoras,
            IncidenciaJpaRepositorio incidencias,
            FallaJpaRepositorio fallas,
            TipoFallaJpaRepositorio tiposFalla,
            Clock reloj) {
        this.usuarios = usuarios;
        this.computadoras = computadoras;
        this.incidencias = incidencias;
        this.fallas = fallas;
        this.tiposFalla = tiposFalla;
        this.reloj = reloj;
    }

    @Transactional
    public IncidenciaRegistradaDto registrar(long usuarioId, String codigoQr, String descripcion) {
        return registrar(usuarioId, codigoQr, descripcion, null);
    }

    @Transactional
    public IncidenciaRegistradaDto registrar(long usuarioId, String codigoQr, String descripcion, Integer tipoFallaId) {
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
        Instant fechaRegistro = Instant.now(reloj);
        IncidenciaEntidad guardada = incidencias.saveAndFlush(new IncidenciaEntidad(
                computadora, usuario, descripcion.trim(), fechaRegistro, EstadoIncidencia.REGISTRADA));
        if (tipoFallaId != null) {
            TipoFallaEntidad tipoFalla = tiposFalla.findById(tipoFallaId)
                    .orElseThrow(() -> new IllegalArgumentException("El tipo de falla seleccionado no existe."));
            fallas.save(new FallaEntidad(
                    guardada,
                    tipoFalla,
                    descripcion.trim(),
                    java.time.LocalDateTime.ofInstant(fechaRegistro, reloj.getZone())));
        }
        return convertirIncidencia(guardada);
    }

    @Transactional(readOnly = true)
    public List<com.utp.trazalab.dto.TipoFallaResumenDto> consultarTiposFalla() {
        return tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc().stream()
                .map(tipo -> new com.utp.trazalab.dto.TipoFallaResumenDto(
                        tipo.getId(), tipo.getCategoria().getNombre(), tipo.getNombre()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<IncidenciaRegistradaDto> consultarIncidencias(Pageable pageable) {
        Pageable ordenado = PageRequest.of(
                numeroPagina(pageable),
                limitarTamanoPagina(pageable),
                ordenIncidenciasDescendente());

        return incidencias.findAllByOrderByFechaRegistroDescIdDesc(ordenado)
                .map(this::convertirIncidencia);
    }

    @Transactional(readOnly = true)
    public IncidenciasExportacionDto prepararExportacionIncidencias() {
        Integer limiteId = incidencias.obtenerMaximoId();
        if (limiteId == null) {
            return new IncidenciasExportacionDto(0, List.of());
        }

        long total = incidencias.countByIdLessThanEqual(limiteId);
        return new IncidenciasExportacionDto(total, iterarIncidenciasHasta(limiteId));
    }

    private Iterable<IncidenciaRegistradaDto> iterarIncidenciasHasta(Integer limiteId) {
        return () -> new Iterator<>() {
            private int numeroPagina;
            private Iterator<IncidenciaRegistradaDto> incidenciasActuales = List.<IncidenciaRegistradaDto>of().iterator();
            private boolean ultimaPagina;

            @Override
            public boolean hasNext() {
                while (!incidenciasActuales.hasNext() && !ultimaPagina) {
                    Pageable pageable = PageRequest.of(
                            numeroPagina++,
                            TAMANO_LOTE_EXPORTACION,
                            ordenIncidenciasDescendente());
                    Page<IncidenciaRegistradaDto> pagina = incidencias
                            .findAllByIdLessThanEqualOrderByFechaRegistroDescIdDesc(limiteId, pageable)
                            .map(RegistroIncidenciaPersistenteServicio.this::convertirIncidencia);
                    incidenciasActuales = pagina.iterator();
                    ultimaPagina = pagina.isLast();
                }

                return incidenciasActuales.hasNext();
            }

            @Override
            public IncidenciaRegistradaDto next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No hay más incidencias para exportar.");
                }

                return incidenciasActuales.next();
            }
        };
    }

    @Transactional(readOnly = true)
    public List<UsuarioResumenDto> consultarUsuarios() {
        return usuarios.findAll().stream()
                .map(this::convertirUsuario)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResumenDto consultarUsuarioDemostracion(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalStateException("Configura un correo para el usuario de demostración.");
        }

        UsuarioEntidad usuario = usuarios.findByCorreoIgnoreCase(correo.trim())
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el usuario de demostración configurado en la base de datos."));
        if (!"activo".equalsIgnoreCase(usuario.getEstado())
                || !rolPuedeReportar(usuario.getRol().getNombre())) {
            throw new IllegalStateException(
                    "El usuario de demostración debe estar activo y tener un rol que pueda reportar incidencias.");
        }

        return convertirUsuario(usuario);
    }

    @Transactional(readOnly = true)
    public HistorialComputadoraDto consultarHistorialComputadora(String codigoQr, Pageable pageable) {
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new IllegalArgumentException("Ingresa el código QR de la computadora.");
        }

        ComputadoraEntidad computadora = computadoras.findByCodigoQrIgnoreCase(codigoQr.trim())
                .orElseThrow(() -> new IllegalArgumentException("No existe una computadora con ese código QR."));
        Pageable ordenado = PageRequest.of(
                numeroPagina(pageable),
                limitarTamanoPagina(pageable),
                ordenIncidenciasAscendente());
        Page<IncidenciaRegistradaDto> historial = incidencias
                .findAllByComputadora_IdOrderByFechaRegistroAscIdAsc(computadora.getId(), ordenado)
                .map(this::convertirIncidencia);
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

    private UsuarioResumenDto convertirUsuario(UsuarioEntidad usuario) {
        return new UsuarioResumenDto(
                usuario.getId(),
                usuario.getNombres(),
                usuario.getApellidos(),
                usuario.getRol().getNombre(),
                usuario.getEstado());
    }

    private boolean rolPuedeReportar(String nombreRol) {
        String normalizado = nombreRol.toLowerCase(Locale.ROOT);
        return normalizado.equals("docente") || normalizado.equals("responsable")
                || normalizado.equals("técnico de soporte") || normalizado.equals("técnico de laboratorio");
    }

    private Sort ordenIncidenciasDescendente() {
        Sort.TypedSort<IncidenciaEntidad> orden = Sort.sort(IncidenciaEntidad.class);
        return orden.by(IncidenciaEntidad::getFechaRegistro)
                .descending()
                .and(orden.by(IncidenciaEntidad::getId).descending());
    }

    private Sort ordenIncidenciasAscendente() {
        Sort.TypedSort<IncidenciaEntidad> orden = Sort.sort(IncidenciaEntidad.class);
        return orden.by(IncidenciaEntidad::getFechaRegistro)
                .ascending()
                .and(orden.by(IncidenciaEntidad::getId).ascending());
    }

    private int limitarTamanoPagina(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return TAMANO_PAGINA_MAXIMO;
        }

        return Math.max(1, Math.min(pageable.getPageSize(), TAMANO_PAGINA_MAXIMO));
    }

    private int numeroPagina(Pageable pageable) {
        return pageable.isPaged() ? pageable.getPageNumber() : 0;
    }
}
