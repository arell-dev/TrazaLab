package com.utp.trazalab.trazabilidad.controller;

import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.service.EquipoService;
import com.utp.trazalab.trazabilidad.service.HistorialService;
import com.utp.trazalab.trazabilidad.service.DisponibilidadService;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

@Controller
public class EquipoController {
    private final EquipoService equipos;
    private final HistorialService historialTecnico;

    public EquipoController(EquipoService equipos, HistorialService historialTecnico) {
        this.equipos = equipos;
        this.historialTecnico = historialTecnico;
    }

    @GetMapping("/equipos/escaneo")
    public String escaneo() {
        return "equipos/escaneo";
    }

    @GetMapping("/equipos/buscar")
    public String buscarEquipo(@RequestParam String codigoQr) {
        if (codigoQr.isBlank()) {
            return "redirect:/equipos/escaneo";
        }

        return "redirect:/equipos/" + UriUtils.encodePathSegment(codigoQr.strip(), StandardCharsets.UTF_8);
    }

    @GetMapping("/equipos/{codigoQr}")
    public String fichaEquipo(@PathVariable String codigoQr, @RequestParam(defaultValue = "0") int paginaHistorial, Model model) {
        Computadora computadora = equipos.buscarPorQr(codigoQr);

        model.addAttribute("computadora", computadora);
        model.addAttribute("etiquetaUbicacion", DisponibilidadService.etiquetaUbicacion(computadora.getUbicacion()));
        model.addAttribute("historialTecnico", historialTecnico.consultar(computadora, paginaHistorial, 10));

        return "equipos/ficha";
    }

}
