package com.utp.trazalab.trazabilidad.service;

import com.utp.trazalab.trazabilidad.dto.FichaEquipoDto;
import com.utp.trazalab.trazabilidad.dto.HistorialComputadoraDto;
import com.utp.trazalab.trazabilidad.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.trazabilidad.dto.IncidenciaSolicitudDto;
import com.utp.trazalab.usuarios.dto.UsuarioResumenDto;
import com.utp.trazalab.trazabilidad.dto.TipoFallaResumenDto;
import com.utp.trazalab.logging.EventoAplicacion;
import com.utp.trazalab.logging.LoggingAplicacion;
import com.utp.trazalab.logging.ResultadoEvento;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class IncidenciaService {
    private final RegistroIncidencia servicio;
    private final LoggingAplicacion logging;

    public IncidenciaService(RegistroIncidencia servicio, LoggingAplicacion logging) {
        this.servicio = servicio;
        this.logging = logging;
    }

    public IncidenciaRegistradaDto registrar(IncidenciaSolicitudDto solicitud) {
        return registrar(solicitud, null);
    }

    public IncidenciaRegistradaDto registrar(IncidenciaSolicitudDto solicitud, Integer tipoFallaId) {
        return ejecutar(
                "incidencia.registrar",
                "incidencia",
                () -> servicio.registrar(
                        solicitud.getUsuarioId(),
                        solicitud.getCodigoQr(),
                        solicitud.getDescripcion(),
                        tipoFallaId),
                incidencia -> "id=" + incidencia.id(),
                incidencia -> null);
    }

    public List<TipoFallaResumenDto> consultarTiposFalla() {
        return servicio.consultarTiposFalla();
    }

    public Page<IncidenciaRegistradaDto> consultarIncidencias(Pageable pageable) {
        return ejecutar(
                "incidencia.listar",
                "incidencia",
                () -> servicio.consultarIncidencias(pageable),
                incidencias -> null,
                Page::getNumberOfElements);
    }

    public List<UsuarioResumenDto> consultarUsuarios() {
        return ejecutar(
                "usuario.listar",
                "usuario",
                servicio::consultarUsuarios,
                usuarios -> null,
                List::size);
    }

    public UsuarioResumenDto consultarUsuarioAutenticado(String correo) {
        return ejecutar(
                "usuario.autenticado",
                "usuario",
                () -> servicio.consultarUsuarioAutenticado(correo),
                usuario -> "id=" + usuario.id(),
                usuario -> null);
    }

    public HistorialComputadoraDto consultarHistorialComputadora(String codigoQr, Pageable pageable) {
        return ejecutar(
                "computadora.historial",
                "computadora",
                () -> servicio.consultarHistorialComputadora(codigoQr, pageable),
                historial -> "id=" + historial.computadoraId(),
                historial -> historial.incidencias().getNumberOfElements());
    }

    public FichaEquipoDto consultarHistorialRecienteComputadora(String codigoQr) {
        return ejecutar(
                "computadora.historial.reciente",
                "computadora",
                () -> servicio.consultarHistorialRecienteComputadora(codigoQr),
                historial -> "id=" + historial.computadoraId(),
                historial -> historial.incidencias().size());
    }

    private <T> T ejecutar(String accion, String recurso, Supplier<T> operacion, Function<T, String> referencia, Function<T, Integer> cantidad) {
        try {
            T resultado = operacion.get();
            logging.registrar(new EventoAplicacion(
                    accion, recurso, ResultadoEvento.EXITOSO, referencia.apply(resultado), cantidad.apply(resultado), null));
            return resultado;
        } catch (IllegalArgumentException error) {
            logging.registrar(new EventoAplicacion(
                    accion, recurso, ResultadoEvento.RECHAZADO, null, null, "validacion"));
            throw error;
        } catch (RuntimeException error) {
            logging.registrar(new EventoAplicacion(
                    accion, recurso, ResultadoEvento.ERROR, null, null, error.getClass().getSimpleName()));
            throw error;
        }
    }
}
