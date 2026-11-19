package com.utp.trazalab.trazabilidad.controller.api;

import com.utp.trazalab.trazabilidad.dto.FichaEquipoDto;
import com.utp.trazalab.trazabilidad.service.IncidenciaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/computadoras")
public class ComputadoraApiController {
    private final IncidenciaService servicio;

    public ComputadoraApiController(IncidenciaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/historial")
    public FichaEquipoDto consultarHistorialReciente(@RequestParam String codigoQr) {
        return servicio.consultarHistorialRecienteComputadora(codigoQr);
    }
}
