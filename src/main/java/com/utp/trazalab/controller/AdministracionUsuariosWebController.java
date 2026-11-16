package com.utp.trazalab.controller;

import com.utp.trazalab.dto.UsuarioAltaFormulario;
import com.utp.trazalab.service.AdministracionUsuariosServicio;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class AdministracionUsuariosWebController {
    private final AdministracionUsuariosServicio servicio;

    public AdministracionUsuariosWebController(AdministracionUsuariosServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(Model model) {
        prepararModelo(model);
        model.addAttribute("usuarioAlta", new UsuarioAltaFormulario());
        return "usuarios/listado";
    }

    @PostMapping
    public String crear(@Valid @ModelAttribute("usuarioAlta") UsuarioAltaFormulario formulario, BindingResult errores, Model model, RedirectAttributes redireccion) {
        if (errores.hasErrors()) {
            prepararModelo(model);
            return "usuarios/listado";
        }
        try {
            servicio.crear(formulario);
            redireccion.addFlashAttribute("mensajeExito", "La cuenta se creó correctamente.");
            return "redirect:/usuarios";
        } catch (IllegalArgumentException error) {
            errores.reject("usuario.invalido", error.getMessage());
            prepararModelo(model);
            return "usuarios/listado";
        }
    }

    @PostMapping("/{usuarioId}/estado")
    public String cambiarEstado(@PathVariable Integer usuarioId, Authentication autenticacion, RedirectAttributes redireccion) {
        try {
            servicio.cambiarEstado(usuarioId, autenticacion.getName());
            redireccion.addFlashAttribute("mensajeExito", "El estado de la cuenta se actualizó.");
        } catch (IllegalArgumentException error) {
            redireccion.addFlashAttribute("mensajeError", error.getMessage());
        }
        return "redirect:/usuarios";
    }

    private void prepararModelo(Model model) {
        model.addAttribute("usuarios", servicio.listarUsuarios());
        model.addAttribute("roles", servicio.listarRoles());
    }
}
