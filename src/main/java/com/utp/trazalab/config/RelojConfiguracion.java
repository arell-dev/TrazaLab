package com.utp.trazalab.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelojConfiguracion {
    @Bean
    public Clock reloj() {
        return Clock.systemUTC();
    }
}
