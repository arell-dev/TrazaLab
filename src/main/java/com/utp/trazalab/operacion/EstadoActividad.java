package com.utp.trazalab.operacion;

public record EstadoActividad(boolean relojValido, long inactividadNanos, int solicitudesActivas, boolean drenando) {
}
