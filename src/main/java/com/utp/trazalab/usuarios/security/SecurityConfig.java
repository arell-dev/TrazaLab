package com.utp.trazalab.usuarios.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain seguridadWeb(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(autorizacion -> autorizacion
                        .requestMatchers("/usuarios/login", "/login", "/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/health", "/db-health", "/api/health").permitAll()
                        .requestMatchers("/usuarios/**").hasAuthority("ROLE_Administrador")
                        .requestMatchers("/mantenimientos/**").hasAnyAuthority("ROLE_Administrador", "ROLE_Técnico de Soporte", "ROLE_Técnico de Laboratorio")
                        .requestMatchers("/reportes/**").hasAnyAuthority("ROLE_Administrador", "ROLE_Técnico de Soporte", "ROLE_Técnico de Laboratorio", "ROLE_Responsable")
                        .requestMatchers("/api/computadoras/historial").hasAnyAuthority("ROLE_Administrador", "ROLE_Técnico de Soporte", "ROLE_Técnico de Laboratorio", "ROLE_Responsable")
                        .requestMatchers("/", "/disponibilidad", "/disponibilidad/resultados").hasAnyAuthority("ROLE_Administrador", "ROLE_Técnico de Soporte", "ROLE_Técnico de Laboratorio", "ROLE_Docente", "ROLE_Responsable")
                        .requestMatchers("/equipos/**").hasAnyAuthority("ROLE_Administrador", "ROLE_Técnico de Soporte", "ROLE_Técnico de Laboratorio", "ROLE_Responsable")
                        .requestMatchers("/incidencias/**").hasAnyAuthority("ROLE_Administrador", "ROLE_Técnico de Soporte", "ROLE_Técnico de Laboratorio", "ROLE_Docente", "ROLE_Responsable")
                        .anyRequest().denyAll())
                .formLogin(formulario -> formulario
                        .loginPage("/usuarios/login")
                        .loginProcessingUrl("/usuarios/login")
                        .defaultSuccessUrl("/disponibilidad", true)
                        .permitAll())
                .logout(cierre -> cierre.logoutSuccessUrl("/usuarios/login?salio"));
        return http.build();
    }

    @Bean
    public PasswordEncoder codificadorContrasenas() {
        return new BCryptPasswordEncoder();
    }
}
