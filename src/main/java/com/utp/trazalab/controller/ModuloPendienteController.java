package com.utp.trazalab.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ModuloPendienteController {
    @GetMapping("/mantenimientos")
    public String mantenimientos(Model model) {
        model.addAttribute("seccion", "mantenimientos");
        model.addAttribute("titulo", "Mantenimientos");
        model.addAttribute("descripcion", "El registro de intervenciones y componentes se incorporará en una siguiente PR.");
        return "modulos/pendiente";
    }

    @GetMapping("/reportes")
    public String reportes(Model model) {
        model.addAttribute("seccion", "reportes");
        model.addAttribute("titulo", "Reportes");
        model.addAttribute("descripcion", "Los indicadores, filtros y exportaciones del módulo de reportes se incorporarán en una siguiente PR.");
        return "modulos/pendiente";
    }
}
