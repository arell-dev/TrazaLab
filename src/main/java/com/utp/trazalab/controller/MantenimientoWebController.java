package com.utp.trazalab.controller;

import com.utp.trazalab.dto.RegistroMantenimientoFormulario;
import com.utp.trazalab.service.IncidenciaAplicacionServicio;
import com.utp.trazalab.service.RegistroMantenimientoServicio;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/mantenimientos")
public class MantenimientoWebController {
    private final RegistroMantenimientoServicio mantenimientos;
    private final IncidenciaAplicacionServicio incidencias;
    private final String correoTecnicoDemostracion;

    public MantenimientoWebController(RegistroMantenimientoServicio mantenimientos, IncidenciaAplicacionServicio incidencias, @Value("${trazalab.demo.mantenimiento-correo}") String correoTecnicoDemostracion) {
        this.mantenimientos = mantenimientos;
        this.incidencias = incidencias;
        this.correoTecnicoDemostracion = correoTecnicoDemostracion;
    }

    @GetMapping
    public String listar(Model model) {
        prepararModelo(model);
        model.addAttribute("registroMantenimiento", new RegistroMantenimientoFormulario());
        return "mantenimientos/listado";
    }

    @PostMapping
    public String registrar(@Valid @ModelAttribute("registroMantenimiento") RegistroMantenimientoFormulario formulario, BindingResult errores, Model model, RedirectAttributes redireccion) {
        if (errores.hasErrors()) {
            prepararModelo(model);
            return "mantenimientos/listado";
        }

        try {
            int usuarioId = incidencias.consultarUsuarioDemostracion(correoTecnicoDemostracion).id();
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
