package com.utp.trazalab.operacion;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SeguimientoSolicitudAsincronaTest {
    @Test
    void timeoutAndErrorDoNotFinalizeButCompleteDoes() throws Exception {
        AtomicLong reloj = new AtomicLong();
        ControlActividad control = new ControlActividad(reloj::get);
        FiltroActividad filtro = new FiltroActividad(control);
        MockHttpServletRequest base = new MockHttpServletRequest("GET", "/disponibilidad");
        base.setServletPath("/disponibilidad");
        AsyncContext contexto = mock(AsyncContext.class);
        var solicitud = new HttpServletRequestWrapper(base) {
            @Override
            public boolean isAsyncStarted() {
                return true;
            }

            @Override
            public AsyncContext getAsyncContext() {
                return contexto;
            }
        };
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("usuario", "", List.of()));

        try {
            filtro.doFilter(solicitud, new MockHttpServletResponse(), (entrada, salida) -> { });
        } finally {
            SecurityContextHolder.clearContext();
        }

        reloj.set(Duration.ofMinutes(31).toNanos());
        assertFalse(control.iniciarDrenaje(Duration.ofMinutes(30)));

        ArgumentCaptor<AsyncListener> captor = ArgumentCaptor.forClass(AsyncListener.class);
        verify(contexto).addListener(captor.capture());
        AsyncListener listener = captor.getValue();
        AsyncEvent evento = new AsyncEvent(contexto);
        listener.onTimeout(evento);
        listener.onError(evento);
        assertFalse(control.iniciarDrenaje(Duration.ofMinutes(30)));

        listener.onComplete(evento);
        assertFalse(control.iniciarDrenaje(Duration.ofMinutes(30)));
        reloj.addAndGet(Duration.ofMinutes(30).toNanos());
        assertTrue(control.iniciarDrenaje(Duration.ofMinutes(30)));
    }
}
