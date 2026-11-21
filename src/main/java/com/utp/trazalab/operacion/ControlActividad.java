package com.utp.trazalab.operacion;

import java.time.Duration;
import java.util.function.LongSupplier;

public final class ControlActividad {
    private final LongSupplier reloj;
    private long ultimaActividad;
    private int solicitudesActivas;
    private boolean drenando;
    private boolean actividadAsincronaNoVerificada;

    public ControlActividad() {
        this(System::nanoTime);
    }

    ControlActividad(LongSupplier reloj) {
        this.reloj = reloj;
        this.ultimaActividad = reloj.getAsLong();
    }

    public synchronized boolean admitirSolicitud() {
        if (drenando) {
            return false;
        }
        ultimaActividad = reloj.getAsLong();
        solicitudesActivas++;

        return true;
    }

    public synchronized void finalizarSolicitud(boolean asincrona) {
        finalizar(asincrona);
        ultimaActividad = reloj.getAsLong();
    }

    public synchronized boolean admitirInicioSesion() {
        if (drenando) {
            return false;
        }

        solicitudesActivas++;
        return true;
    }

    public synchronized void finalizarInicioSesion(boolean asincrona) {
        finalizar(asincrona);
    }

    private void finalizar(boolean asincrona) {
        if (solicitudesActivas <= 0) {
            throw new IllegalStateException("No hay una solicitud activa para finalizar.");
        }

        solicitudesActivas--;
        actividadAsincronaNoVerificada |= asincrona;
    }

    public synchronized void registrarInicioSesion() {
        ultimaActividad = reloj.getAsLong();
    }

    public synchronized boolean iniciarDrenaje(Duration inactividad) {
        if (inactividad.isNegative() || inactividad.isZero()) {
            throw new IllegalArgumentException("El plazo debe ser positivo.");
        }

        long transcurrido = reloj.getAsLong() - ultimaActividad;

        if (drenando || actividadAsincronaNoVerificada || solicitudesActivas != 0 || transcurrido < 0 || transcurrido < inactividad.toNanos()) {
            return false;
        }

        drenando = true;
        return true;
    }

    public synchronized boolean drenajeVigente() {
        return drenando && solicitudesActivas == 0 && !actividadAsincronaNoVerificada;
    }

    public synchronized void cancelarDrenaje() {
        drenando = false;
    }
}
