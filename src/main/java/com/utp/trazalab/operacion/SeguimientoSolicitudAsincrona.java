package com.utp.trazalab.operacion;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

final class SeguimientoSolicitudAsincrona implements AsyncListener {
    private final Runnable finalizar;
    private final AtomicBoolean finalizada = new AtomicBoolean();

    SeguimientoSolicitudAsincrona(Runnable finalizar) {
        this.finalizar = finalizar;
    }

    void adjuntar(AsyncContext contexto) {
        contexto.addListener(this);
    }

    @Override
    public void onComplete(AsyncEvent evento) {
        if (finalizada.compareAndSet(false, true)) {
            finalizar.run();
        }
    }

    @Override
    public void onTimeout(AsyncEvent evento) {
    }

    @Override
    public void onError(AsyncEvent evento) {
    }

    @Override
    public void onStartAsync(AsyncEvent evento) throws IOException {
        adjuntar(evento.getAsyncContext());
    }
}
