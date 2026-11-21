package com.utp.trazalab.operacion;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.utp.trazalab.usuarios.security.SecurityConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(SeguridadActividadIntegracionTest.ConfiguracionPrueba.class)
@TestPropertySource(properties = "trazalab.actividad.habilitada=true")
class SeguridadActividadIntegracionTest {
    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ControlActividad actividad;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        actividad.cancelarDrenaje();
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    void cadenaRealAdmiteSaludYRechazaLoginYUsuarioDuranteDrenaje() throws Exception {
        MvcResult inicio = mvc.perform(post("/usuarios/login").servletPath("/usuarios/login").with(csrf()).param("username", "docente").param("password", "prueba"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        var sesion = (org.springframework.mock.web.MockHttpSession) inicio.getRequest().getSession(false);
        mvc.perform(get("/disponibilidad").session(sesion)).andExpect(status().isOk());
        assertTrue(actividad.iniciarDrenaje(Duration.ofNanos(1)));
        mvc.perform(get("/health")).andExpect(status().isOk());
        mvc.perform(post("/usuarios/login").servletPath("/usuarios/login").with(csrf()).param("username", "docente").param("password", "prueba"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/disponibilidad").session(sesion)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/disponibilidad").session(sesion)).andExpect(status().isServiceUnavailable());
        actividad.cancelarDrenaje();
        mvc.perform(get("/disponibilidad").session(sesion)).andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ConfiguracionActividad.class, RutasPrueba.class})
    static class ConfiguracionPrueba {
        @Bean
        UserDetailsService usuarios(PasswordEncoder codificador) {
            return new InMemoryUserDetailsManager(User.withUsername("docente").password(codificador.encode("prueba")).authorities("ROLE_Docente").build());
        }
    }

    @RestController
    static class RutasPrueba {
        @GetMapping({"/health", "/disponibilidad"})
        String responder() {
            return "ok";
        }
    }
}
