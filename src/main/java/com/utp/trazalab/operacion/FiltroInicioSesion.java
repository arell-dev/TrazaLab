package com.utp.trazalab.operacion;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public final class FiltroInicioSesion extends OncePerRequestFilter {
    private final ControlActividad actividad;

    public FiltroInicioSesion(ControlActividad actividad) {
        this.actividad = actividad;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena) throws ServletException, IOException {

        if (!"POST".equals(solicitud.getMethod()) || !"/usuarios/login".equals(solicitud.getServletPath())) {
            cadena.doFilter(solicitud, respuesta);
            return;
        }

        if (!actividad.admitirInicioSesion()) {
            respuesta.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "El sistema se está preparando para apagarse. Inténtalo de nuevo más tarde.");
            return;
        }

        try {
            cadena.doFilter(solicitud, respuesta);
        } finally {
            actividad.finalizarInicioSesion(solicitud.isAsyncStarted());
        }
    }
}
