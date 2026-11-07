package com.utp.trazalab.controller.api;

import com.utp.trazalab.dto.EquipoConHistorialRecienteDto;
import com.utp.trazalab.service.IncidenciaAplicacionServicio;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/computadoras")
public class ComputadoraApiController {
    private final IncidenciaAplicacionServicio servicio;

    public ComputadoraApiController(IncidenciaAplicacionServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/historial")
    public EquipoConHistorialRecienteDto consultarHistorialReciente(@RequestParam String codigoQr) {
        return servicio.consultarHistorialRecienteComputadora(codigoQr);
    }
}
