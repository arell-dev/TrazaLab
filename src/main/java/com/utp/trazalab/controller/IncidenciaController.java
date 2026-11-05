package com.utp.trazalab.controller;

import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.dto.HistorialComputadoraDto;
import com.utp.trazalab.dto.RegistroIncidenciaDto;
import com.utp.trazalab.dto.UsuarioResumenDto;
import com.utp.trazalab.logging.EventoAplicacion;
import com.utp.trazalab.logging.LoggingAplicacion;
import com.utp.trazalab.logging.ResultadoEvento;
import com.utp.trazalab.service.RegistroIncidenciaPersistenteServicio;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.StringJoiner;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class IncidenciaController {
    private final RegistroIncidenciaPersistenteServicio servicio;
    private final Validator validador;
    private final LoggingAplicacion logging;

    public IncidenciaController(RegistroIncidenciaPersistenteServicio servicio, Validator validador, LoggingAplicacion logging) {
        this.servicio = servicio;
        this.validador = validador;
        this.logging = logging;
    }

    public IncidenciaRegistradaDto registrar(RegistroIncidenciaDto solicitud) {
        return ejecutar("incidencia.registrar", "incidencia", () -> {
            Set<ConstraintViolation<RegistroIncidenciaDto>> errores = validador.validate(solicitud);
            if (!errores.isEmpty()) {
                StringJoiner mensajes = new StringJoiner(" ");
                errores.forEach(error -> mensajes.add(error.getMessage()));
                throw new IllegalArgumentException(mensajes.toString());
            }
            return servicio.registrar(solicitud.getUsuarioId(), solicitud.getCodigoQr(), solicitud.getDescripcion());
        }, incidencia -> "id=" + incidencia.id(), incidencia -> null);
    }

    public Page<IncidenciaRegistradaDto> consultarIncidencias(Pageable pageable) {
        return ejecutar("incidencia.listar", "incidencia", () -> servicio.consultarIncidencias(pageable),
                incidencias -> null, Page::getNumberOfElements);
    }

    public List<UsuarioResumenDto> consultarUsuarios() {
        return ejecutar("usuario.listar", "usuario", servicio::consultarUsuarios,
                usuarios -> null, List::size);
    }

    public HistorialComputadoraDto consultarHistorialComputadora(String codigoQr, Pageable pageable) {
        return ejecutar("computadora.historial", "computadora", () -> servicio.consultarHistorialComputadora(codigoQr, pageable),
                historial -> "id=" + historial.computadoraId(), historial -> historial.incidencias().getNumberOfElements());
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
