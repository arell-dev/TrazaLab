package com.utp.trazalab.operacion;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "trazalab.actividad", name = "canal-habilitado", havingValue = "true")
public class ConfiguracionCanalDrenaje {
    @Bean(destroyMethod = "close")
    public CanalDrenaje canalDrenaje(ControlActividad actividad, @Value("${trazalab.actividad.socket:/run/trazalab-control/control.sock}") String ruta) throws IOException {
        return new CanalDrenaje(actividad, Path.of(ruta));
    }
}
