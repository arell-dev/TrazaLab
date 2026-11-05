package com.utp.trazalab.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SeguridadWebConfiguracion {
    @Bean
    public SecurityFilterChain seguridadWeb(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(autorizacion -> autorizacion.anyRequest().permitAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public UserDetailsService usuariosNoConfigurados() {
        return usuario -> {
            throw new UsernameNotFoundException("La autenticación no está habilitada en esta etapa de demostración.");
        };
    }
}
