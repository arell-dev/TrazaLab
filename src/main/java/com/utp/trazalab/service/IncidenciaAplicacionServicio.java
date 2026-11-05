package com.utp.trazalab.service;

import com.utp.trazalab.dto.HistorialComputadoraDto;
import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.dto.RegistroIncidenciaDto;
import com.utp.trazalab.dto.UsuarioResumenDto;
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
public class IncidenciaAplicacionServicio {
    private final RegistroIncidenciaPersistenteServicio servicio;
    private final LoggingAplicacion logging;

    public IncidenciaAplicacionServicio(
            RegistroIncidenciaPersistenteServicio servicio,
            LoggingAplicacion logging) {
        this.servicio = servicio;
        this.logging = logging;
    }

    public IncidenciaRegistradaDto registrar(RegistroIncidenciaDto solicitud) {
        return ejecutar(
                "incidencia.registrar",
                "incidencia",
                () -> servicio.registrar(
                        solicitud.getUsuarioId(),
                        solicitud.getCodigoQr(),
                        solicitud.getDescripcion()),
                incidencia -> "id=" + incidencia.id(),
                incidencia -> null);
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

    public UsuarioResumenDto consultarUsuarioDemostracion(String correo) {
        return ejecutar(
                "usuario.demostracion",
                "usuario",
                () -> servicio.consultarUsuarioDemostracion(correo),
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

    private <T> T ejecutar(
            String accion,
            String recurso,
            Supplier<T> operacion,
            Function<T, String> referencia,
            Function<T, Integer> cantidad) {
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
