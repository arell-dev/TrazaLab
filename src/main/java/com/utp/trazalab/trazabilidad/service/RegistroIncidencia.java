package com.utp.trazalab.trazabilidad.service;

import com.utp.trazalab.trazabilidad.model.EstadoIncidencia;
import com.utp.trazalab.trazabilidad.dto.FichaEquipoDto;
import com.utp.trazalab.trazabilidad.dto.HistorialComputadoraDto;
import com.utp.trazalab.trazabilidad.dto.IncidenciaRecienteDto;
import com.utp.trazalab.trazabilidad.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.usuarios.dto.UsuarioResumenDto;
import com.utp.trazalab.exception.RecursoNoEncontradoException;
import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.model.Falla;
import com.utp.trazalab.trazabilidad.model.Incidencia;
import com.utp.trazalab.trazabilidad.model.TipoFalla;
import com.utp.trazalab.usuarios.model.Usuario;
import com.utp.trazalab.trazabilidad.repository.ComputadoraRepository;
import com.utp.trazalab.trazabilidad.repository.FallaRepository;
import com.utp.trazalab.trazabilidad.repository.IncidenciaRepository;
import com.utp.trazalab.trazabilidad.repository.TipoFallaRepository;
import com.utp.trazalab.usuarios.service.UsuarioService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroIncidencia {
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    private final UsuarioService usuarios;
    private final ComputadoraRepository computadoras;
    private final IncidenciaRepository incidencias;
    private final FallaRepository fallas;
    private final TipoFallaRepository tiposFalla;
    private final Clock reloj;

    public RegistroIncidencia(UsuarioService usuarios, ComputadoraRepository computadoras, IncidenciaRepository incidencias, FallaRepository fallas, TipoFallaRepository tiposFalla, Clock reloj) {
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
        Usuario usuario = usuarios.buscarPorId(Math.toIntExact(usuarioId))
                .orElseThrow(() -> new RecursoNoEncontradoException("El usuario indicado no existe."));
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

        Computadora computadora = computadoras.findByCodigoQrIgnoreCase(codigoQr.trim())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una computadora con ese código QR."));
        Instant fechaRegistro = Instant.now(reloj);
        Incidencia guardada = incidencias.saveAndFlush(new Incidencia(
                computadora, usuario, descripcion.trim(), fechaRegistro, EstadoIncidencia.REGISTRADA));
        if (tipoFallaId != null) {
            TipoFalla tipoFalla = tiposFalla.findById(tipoFallaId)
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "El tipo de falla seleccionado no existe."));
            fallas.save(new Falla(
                    guardada,
                    tipoFalla,
                    descripcion.trim(),
                    java.time.LocalDateTime.ofInstant(fechaRegistro, reloj.getZone())));
        }
        return convertirIncidencia(guardada);
    }

    @Transactional(readOnly = true)
    public List<com.utp.trazalab.trazabilidad.dto.TipoFallaResumenDto> consultarTiposFalla() {
        return tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc().stream()
                .map(tipo -> new com.utp.trazalab.trazabilidad.dto.TipoFallaResumenDto(
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
    public List<UsuarioResumenDto> consultarUsuarios() {
        return usuarios.listarUsuarios().stream()
                .map(this::convertirUsuario)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResumenDto consultarUsuarioAutenticado(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalStateException("No se encontró el correo del usuario autenticado.");
        }

        Usuario usuario = usuarios.buscarPorCorreo(correo.trim())
                .orElseThrow(() -> new IllegalStateException(
                        "La cuenta autenticada no existe en la base de datos."));
        if (!"activo".equalsIgnoreCase(usuario.getEstado())
                || !rolPuedeReportar(usuario.getRol().getNombre())) {
            throw new IllegalStateException(
                    "La cuenta autenticada debe estar activa y tener un rol autorizado para registrar incidencias.");
        }

        return convertirUsuario(usuario);
    }

    @Transactional(readOnly = true)
    public HistorialComputadoraDto consultarHistorialComputadora(String codigoQr, Pageable pageable) {
        Computadora computadora = buscarComputadora(codigoQr);
        Pageable ordenado = PageRequest.of(
                numeroPagina(pageable),
                limitarTamanoPagina(pageable),
                ordenIncidenciasAscendente());
        Page<IncidenciaRegistradaDto> historial = incidencias
                .findAllByComputadora_IdOrderByFechaRegistroAscIdAsc(computadora.getId(), ordenado)
                .map(this::convertirIncidencia);
        return convertirHistorialComputadora(computadora, historial);
    }

    @Transactional(readOnly = true)
    public FichaEquipoDto consultarHistorialRecienteComputadora(String codigoQr) {
        Computadora computadora = buscarComputadora(codigoQr);
        List<IncidenciaRecienteDto> historial = incidencias
                .findTop5ByComputadora_IdOrderByFechaRegistroDescIdDesc(computadora.getId())
                .stream()
                .map(this::convertirIncidenciaHistorialReciente)
                .toList();

        return new FichaEquipoDto(
                computadora.getId(),
                computadora.getCodigoQr(),
                computadora.getNumeroSerie(),
                computadora.getMarca(),
                computadora.getModelo(),
                convertirUbicacion(computadora),
                computadora.getEstado().etiqueta(),
                historial);
    }

    private Computadora buscarComputadora(String codigoQr) {
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new IllegalArgumentException("Ingresa el código QR de la computadora.");
        }

        return computadoras.findByCodigoQrIgnoreCase(codigoQr.trim())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe una computadora con ese código QR."));
    }

    private HistorialComputadoraDto convertirHistorialComputadora(Computadora computadora, Page<IncidenciaRegistradaDto> historial) {
        return new HistorialComputadoraDto(
                computadora.getId(),
                computadora.getCodigoQr(),
                computadora.getNumeroSerie(),
                computadora.getMarca(),
                computadora.getModelo(),
                convertirUbicacion(computadora),
                computadora.getEstado().etiqueta(),
                historial);
    }

    private String convertirUbicacion(Computadora computadora) {
        String ubicacion = computadora.getUbicacion().getNombre();
        String edificio = computadora.getUbicacion().getEdificio();
        Integer piso = computadora.getUbicacion().getPiso();
        String detalle = computadora.getUbicacion().getDetalle();
        if (edificio != null && !edificio.isBlank()) {
            ubicacion += " · " + edificio;
        }
        if (piso != null) {
            ubicacion += ", piso " + piso;
        }
        if (detalle != null && !detalle.isBlank()) {
            ubicacion += " · " + detalle;
        }

        return ubicacion;
    }

    private IncidenciaRegistradaDto convertirIncidencia(Incidencia incidencia) {
        return new IncidenciaRegistradaDto(
                incidencia.getId(),
                incidencia.getUsuario().getId(),
                incidencia.getUsuario().getNombres() + " " + incidencia.getUsuario().getApellidos(),
                incidencia.getComputadora().getCodigoQr(),
                incidencia.getDescripcion(),
                incidencia.getFechaRegistro(),
                incidencia.getEstado().name().toLowerCase(Locale.ROOT));
    }

    private IncidenciaRecienteDto convertirIncidenciaHistorialReciente(Incidencia incidencia) {
        return new IncidenciaRecienteDto(
                incidencia.getId(),
                incidencia.getDescripcion(),
                incidencia.getFechaRegistro(),
                incidencia.getEstado().name().toLowerCase(Locale.ROOT));
    }

    private UsuarioResumenDto convertirUsuario(Usuario usuario) {
        return new UsuarioResumenDto(
                usuario.getId(),
                usuario.getNombres(),
                usuario.getApellidos(),
                usuario.getRol().getNombre(),
                usuario.getEstado());
    }

    private boolean rolPuedeReportar(String nombreRol) {
        String normalizado = nombreRol.toLowerCase(Locale.ROOT);
        return normalizado.equals("administrador") || normalizado.equals("docente") || normalizado.equals("responsable")
                || normalizado.equals("técnico de soporte") || normalizado.equals("técnico de laboratorio");
    }

    private Sort ordenIncidenciasDescendente() {
        return Sort.by(Incidencia::getFechaRegistro, Incidencia::getId)
                .descending();
    }

    private Sort ordenIncidenciasAscendente() {
        return Sort.by(Incidencia::getFechaRegistro, Incidencia::getId)
                .ascending();
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
