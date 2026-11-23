package com.utp.trazalab.operacion;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlActividadTest {
    @Test
    void esperaTreintaMinutosYReiniciaElPlazoAlIniciarSesion() {
        AtomicLong reloj = new AtomicLong();
        ControlActividad control = new ControlActividad(reloj::get);
        reloj.set(Duration.ofMinutes(30).minusNanos(1).toNanos());
        assertFalse(control.iniciarDrenaje(Duration.ofMinutes(30)));
        control.registrarInicioSesion();
        reloj.addAndGet(Duration.ofMinutes(30).toNanos());
        assertTrue(control.iniciarDrenaje(Duration.ofMinutes(30)));
        control.registrarInicioSesion();
        assertTrue(control.drenajeVigente());
        control.cancelarDrenaje();
        assertFalse(control.drenajeVigente());
    }

    @Test
    void solicitudEnCursoImpideDrenajeYUnaNuevaSolicitudSeRechaza() throws Exception {
        AtomicLong reloj = new AtomicLong();
        ControlActividad control = new ControlActividad(reloj::get);
        CountDownLatch iniciada = new CountDownLatch(1);
        CountDownLatch terminar = new CountDownLatch(1);
        try (ExecutorService ejecutor = Executors.newSingleThreadExecutor()) {
            Future<?> solicitud = ejecutor.submit(() -> {
                assertTrue(control.admitirSolicitud());
                iniciada.countDown();
                try {
                    if (!terminar.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("No se liberó la solicitud.");
                    }
                } catch (InterruptedException excepcion) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(excepcion);
                } finally {
                    control.finalizarSolicitud();
                }
            });
            assertTrue(iniciada.await(5, TimeUnit.SECONDS));
            reloj.set(Duration.ofMinutes(31).toNanos());
            assertFalse(control.iniciarDrenaje(Duration.ofMinutes(30)));
            terminar.countDown();
            solicitud.get(5, TimeUnit.SECONDS);
            reloj.addAndGet(Duration.ofMinutes(30).toNanos());
            assertTrue(control.iniciarDrenaje(Duration.ofMinutes(30)));
            assertFalse(control.admitirSolicitud());
            assertTrue(control.drenajeVigente());
            control.cancelarDrenaje();
            assertFalse(control.drenajeVigente());
        }
    }

    @Test
    void unaSolicitudSoloFinalizaCuandoSeNotificaSuCierre() {
        AtomicLong reloj = new AtomicLong();
        ControlActividad control = new ControlActividad(reloj::get);
        assertTrue(control.admitirSolicitud());
        reloj.set(Duration.ofHours(1).toNanos());
        assertFalse(control.iniciarDrenaje(Duration.ofMinutes(30)));
        control.finalizarSolicitud();
        assertFalse(control.iniciarDrenaje(Duration.ofMinutes(30)));
        reloj.addAndGet(Duration.ofMinutes(30).toNanos());
        assertTrue(control.iniciarDrenaje(Duration.ofMinutes(30)));
    }
}
