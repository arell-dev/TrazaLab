package com.utp.trazalab.operacion;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltroActividadTest {
    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void saludTemporizadorYAnonimoNoExtiendenElPlazo() throws Exception {
        AtomicLong reloj = new AtomicLong();
        ControlActividad control = new ControlActividad(reloj::get);
        FiltroActividad filtro = new FiltroActividad(control);
        reloj.set(Duration.ofMinutes(29).toNanos());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("usuario", "", List.of()));
        ejecutar(filtro, "/health");
        ejecutar(filtro, "/api/temporizador");
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("clave", "anonimo", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        ejecutar(filtro, "/disponibilidad");
        reloj.set(Duration.ofMinutes(30).toNanos());
        assertTrue(control.iniciarDrenaje(Duration.ofMinutes(30)));
    }

    @Test
    void solicitudAutenticadaExtiendeElPlazo() throws Exception {
        AtomicLong reloj = new AtomicLong();
        ControlActividad control = new ControlActividad(reloj::get);
        FiltroActividad filtro = new FiltroActividad(control);
        reloj.set(Duration.ofMinutes(29).toNanos());
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("usuario", "", List.of()));
        ejecutar(filtro, "/disponibilidad");
        reloj.set(Duration.ofMinutes(30).toNanos());
        assertFalse(control.iniciarDrenaje(Duration.ofMinutes(30)));
    }

    private void ejecutar(FiltroActividad filtro, String ruta) throws Exception {
        MockHttpServletRequest solicitud = new MockHttpServletRequest("GET", ruta);
        solicitud.setServletPath(ruta);
        AtomicBoolean atendida = new AtomicBoolean();
        filtro.doFilter(solicitud, new MockHttpServletResponse(), (entrada, salida) -> atendida.set(true));
        assertTrue(atendida.get());
    }
}
