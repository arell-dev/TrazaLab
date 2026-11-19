package com.utp.trazalab.trazabilidad.dto;

import java.util.List;

public record FichaEquipoDto(Integer computadoraId, String codigoQr, String numeroSerie, String marca, String modelo, String ubicacion, String estado, List<IncidenciaRecienteDto> incidencias) {
}
