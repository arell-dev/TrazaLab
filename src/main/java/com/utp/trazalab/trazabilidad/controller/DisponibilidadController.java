package com.utp.trazalab.trazabilidad.controller;

import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import com.utp.trazalab.trazabilidad.service.DisponibilidadService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DisponibilidadController {
    private final DisponibilidadService disponibilidad;

    public DisponibilidadController(DisponibilidadService disponibilidad) {
        this.disponibilidad = disponibilidad;
    }

    @GetMapping("/disponibilidad")
    public String disponibilidad(@RequestParam(required = false) Integer ubicacionId, @RequestParam(required = false) String edificio, @RequestParam(required = false) Integer piso, @RequestParam(required = false) String buscar, @RequestParam(required = false) List<EstadoComputadora> estado, @RequestParam(defaultValue = "0") int pagina, @RequestParam(defaultValue = "false") boolean completo, Model model) {
        model.addAllAttributes(disponibilidad.consultar(ubicacionId, edificio, piso, buscar, estado, pagina, completo));
        model.addAttribute("cargaInicial", !completo);
        return "equipos/disponibilidad";
    }

    @GetMapping("/disponibilidad/resultados")
    public String resultadosDisponibilidad(@RequestParam(required = false) Integer ubicacionId, @RequestParam(required = false) String edificio, @RequestParam(required = false) Integer piso, @RequestParam(required = false) String buscar, @RequestParam(required = false) List<EstadoComputadora> estado, @RequestParam(defaultValue = "0") int pagina, Model model) {
        model.addAllAttributes(disponibilidad.consultarFragmento(ubicacionId, edificio, piso, buscar, estado, pagina));
        return "equipos/disponibilidad :: resultados";
    }
}
