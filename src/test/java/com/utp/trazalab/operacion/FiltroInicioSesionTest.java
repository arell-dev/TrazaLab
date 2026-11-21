package com.utp.trazalab.operacion;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltroInicioSesionTest {
    @Test
    void loginEnCursoBloqueaDrenajeYFalloNoReiniciaPlazo() throws Exception {
        AtomicLong reloj = new AtomicLong();
        ControlActividad control = new ControlActividad(reloj::get);
        FiltroInicioSesion filtro = new FiltroInicioSesion(control);
        MockHttpServletRequest solicitud = new MockHttpServletRequest("POST", "/usuarios/login");
        solicitud.setServletPath("/usuarios/login");
        reloj.set(Duration.ofMinutes(30).toNanos());
        filtro.doFilter(solicitud, new MockHttpServletResponse(), (entrada, salida) -> assertFalse(control.iniciarDrenaje(Duration.ofMinutes(30))));
        assertTrue(control.iniciarDrenaje(Duration.ofMinutes(30)));
    }

    @Test
    void loginDuranteDrenajeSeRechazaSinAdmitirse() throws Exception {
        AtomicLong reloj = new AtomicLong();
        ControlActividad control = new ControlActividad(reloj::get);
        reloj.set(Duration.ofMinutes(30).toNanos());
        assertTrue(control.iniciarDrenaje(Duration.ofMinutes(30)));
        MockHttpServletRequest solicitud = new MockHttpServletRequest("POST", "/usuarios/login");
        solicitud.setServletPath("/usuarios/login");
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        new FiltroInicioSesion(control).doFilter(solicitud, respuesta, (entrada, salida) -> { throw new AssertionError("El login no debe admitirse."); });
        assertEquals(503, respuesta.getStatus());
        assertTrue(control.drenajeVigente());
    }
}
