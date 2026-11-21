package com.utp.trazalab.operacion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;

@Configuration
@ConditionalOnProperty(prefix = "trazalab.actividad", name = "habilitada", havingValue = "true")
public class ConfiguracionActividad {
    private final ControlActividad actividad = new ControlActividad();

    @Bean
    public ControlActividad controlActividad() {
        return actividad;
    }

    @EventListener
    public void inicioSesionCorrecto(AuthenticationSuccessEvent evento) {
        actividad.registrarInicioSesion();
    }
}
