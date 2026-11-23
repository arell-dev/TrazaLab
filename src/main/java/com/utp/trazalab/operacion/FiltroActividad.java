package com.utp.trazalab.operacion;

import java.io.IOException;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class FiltroActividad extends OncePerRequestFilter {
    private final ControlActividad actividad;
    private final AuthenticationTrustResolver confianza = new AuthenticationTrustResolverImpl();

    public FiltroActividad(ControlActividad actividad) {
        this.actividad = actividad;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena) throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        String ruta = solicitud.getServletPath();

        boolean publica = ruta.equals("/health") || ruta.equals("/db-health") || ruta.equals("/api/health") || ruta.equals("/favicon.ico") || ruta.startsWith("/css/") || ruta.startsWith("/js/") || ruta.startsWith("/images/") || ruta.startsWith("/webjars/");

        if (publica || autenticacion == null || !autenticacion.isAuthenticated() || confianza.isAnonymous(autenticacion)) {
            cadena.doFilter(solicitud, respuesta);
            return;
        }

        if (!actividad.admitirSolicitud()) {
            respuesta.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "El sistema se está preparando para apagarse. Inténtalo de nuevo más tarde.");
            return;
        }

        try {
            cadena.doFilter(solicitud, respuesta);
        } finally {
            finalizar(solicitud, actividad::finalizarSolicitud);
        }
    }

    private void finalizar(HttpServletRequest solicitud, Runnable finalizar) {
        if (!solicitud.isAsyncStarted()) {
            finalizar.run();
            return;
        }

        try {
            AsyncContext contexto = solicitud.getAsyncContext();
            new SeguimientoSolicitudAsincrona(finalizar).adjuntar(contexto);
        } catch (IllegalStateException excepcion) {
            System.err.println("La solicitud asíncrona no confirmó su contexto; se conserva como activa.");
        }
    }
}
