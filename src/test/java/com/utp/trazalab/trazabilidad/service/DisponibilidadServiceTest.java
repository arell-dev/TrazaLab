package com.utp.trazalab.trazabilidad.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import com.utp.trazalab.trazabilidad.model.Ubicacion;
import com.utp.trazalab.trazabilidad.repository.ComputadoraRepository;
import com.utp.trazalab.trazabilidad.repository.UbicacionRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class DisponibilidadServiceTest {
    private final ComputadoraRepository computadoras = mock(ComputadoraRepository.class);
    private final UbicacionRepository ubicaciones = mock(UbicacionRepository.class);
    private final DisponibilidadService disponibilidad = new DisponibilidadService(computadoras, ubicaciones, "Piura");

    @Test
    void fragmentoConAulaValidaConsultaSoloSuUbicacion() {
        Ubicacion aula = mock(Ubicacion.class);
        when(aula.getId()).thenReturn(42);
        when(aula.getTipo()).thenReturn("AULA");
        when(aula.getEdificio()).thenReturn("Torre A");
        when(aula.getPiso()).thenReturn(3);
        when(ubicaciones.findByIdAndSede_NombreIgnoreCase(42, "Piura")).thenReturn(Optional.of(aula));
        when(computadoras.contarPorEstado(List.of(42))).thenReturn(Collections.singletonList(new Object[] {EstadoComputadora.OPERATIVA, 4L}));
        when(computadoras.filtrarPorUbicaciones(eq(List.of(42)), eq(""), any(), any())).thenReturn(Page.empty());

        Map<String, Object> resultado = disponibilidad.consultarFragmento(42, "Torre A", 3, null, null, 0);

        assertSame(aula, resultado.get("ubicacionSeleccionada"));
        assertEquals(4L, resultado.get("totalEquipos"));
        assertEquals(false, resultado.get("ubicacionNoDisponible"));
        verify(ubicaciones, never()).findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc(any());
        verify(computadoras, never()).findIdsDeUbicacionConEquipos(any());
    }

    @Test
    void fragmentoConAulaInvalidaUsaSeleccionCompleta() {
        when(ubicaciones.findByIdAndSede_NombreIgnoreCase(99, "Piura")).thenReturn(Optional.empty());
        when(ubicaciones.findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc("Piura")).thenReturn(List.of());

        Map<String, Object> resultado = disponibilidad.consultarFragmento(99, null, null, null, null, 0);

        assertNull(resultado.get("ubicacionSeleccionada"));
        assertEquals(true, resultado.get("ubicacionNoDisponible"));
        verify(ubicaciones).findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc("Piura");
        verify(computadoras, never()).contarPorEstado(any());
    }
}
