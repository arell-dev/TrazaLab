package com.utp.trazalab.trazabilidad.controller;

import com.utp.trazalab.trazabilidad.controller.form.MantenimientoForm;
import com.utp.trazalab.trazabilidad.service.IncidenciaService;
import com.utp.trazalab.trazabilidad.service.MantenimientoService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/mantenimientos")
public class MantenimientoController {
    private final MantenimientoService mantenimientos;
    private final IncidenciaService incidencias;

    public MantenimientoController(MantenimientoService mantenimientos, IncidenciaService incidencias) {
        this.mantenimientos = mantenimientos;
        this.incidencias = incidencias;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String codigoQr, Model model) {
        prepararModelo(model);
        MantenimientoForm formulario = new MantenimientoForm();
        formulario.setCodigoQr(codigoQr);
        model.addAttribute("registroMantenimiento", formulario);
        return "mantenimientos/listado";
    }

    @PostMapping
    public String registrar(@Valid @ModelAttribute("registroMantenimiento") MantenimientoForm formulario, BindingResult errores, Model model, RedirectAttributes redireccion) {
        if (errores.hasErrors()) {
            prepararModelo(model);
            return "mantenimientos/listado";
        }

        try {
            String correo = SecurityContextHolder.getContext().getAuthentication().getName();
            int usuarioId = incidencias.consultarUsuarioAutenticado(correo).id();
            mantenimientos.registrar(
                    usuarioId,
                    formulario.getCodigoQr(),
                    formulario.getTipo(),
                    formulario.getFallaId(),
                    formulario.getDescripcion(),
                    formulario.getAccionComponente(),
                    formulario.getSerieComponenteActual(),
                    formulario.getNombreComponenteNuevo(),
                    formulario.getTipoComponenteNuevo(),
                    formulario.getSerieComponenteNuevo());
            redireccion.addFlashAttribute("mensajeExito", "El mantenimiento se registró correctamente.");
            return "redirect:/mantenimientos";
        } catch (IllegalArgumentException error) {
            errores.reject("mantenimiento.invalido", error.getMessage());
            prepararModelo(model);
            return "mantenimientos/listado";
        }
    }

    private void prepararModelo(Model model) {
        model.addAttribute("seccion", "mantenimientos");
        model.addAttribute("titulo", "Mantenimientos");
        model.addAttribute("mantenimientos", mantenimientos.consultarRecientes());
        model.addAttribute("fallasRecientes", mantenimientos.consultarFallasRecientes());
    }
}
