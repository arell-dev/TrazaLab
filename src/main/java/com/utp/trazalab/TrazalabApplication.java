package com.utp.trazalab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringBootApplication
public class TrazalabApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger("trazalab.arranque");

    public static void main(String[] args) {
        SpringApplication aplicacion = new SpringApplication(TrazalabApplication.class);
        aplicacion.addListeners(evento -> {
            if (evento instanceof ApplicationEnvironmentPreparedEvent) {
                LOGGER.info("Iniciando TrazaLab.");
            } else if (evento instanceof ApplicationReadyEvent) {
                LOGGER.info("TrazaLab está listo para recibir solicitudes.");
            } else if (evento instanceof ApplicationFailedEvent fallido) {
                LOGGER.error("No se pudo iniciar TrazaLab.", fallido.getException());
            }
        });
        aplicacion.run(args);
    }
}
