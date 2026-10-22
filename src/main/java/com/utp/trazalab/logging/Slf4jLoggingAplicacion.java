package com.utp.trazalab.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Slf4jLoggingAplicacion implements LoggingAplicacion {
    private static final Logger log = LoggerFactory.getLogger("trazalab.app");

    @Override
    public void registrar(EventoAplicacion evento) {
        String mensaje = "APP_EVENT accion={} recurso={} resultado={} referencia={} cantidad={} detalle={}";
        Object[] datos = {
                evento.accion(),
                evento.recurso(),
                evento.resultado(),
                evento.referencia(),
                evento.cantidad(),
                evento.detalle()
        };

        switch (evento.resultado()) {
            case EXITOSO -> log.info(mensaje, datos);
            case RECHAZADO -> log.warn(mensaje, datos);
            case ERROR -> log.error(mensaje, datos);
        }
    }
}
