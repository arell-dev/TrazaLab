package com.utp.trazalab.usuarios.controller;

import com.utp.trazalab.usuarios.controller.form.UsuarioForm;
import com.utp.trazalab.usuarios.model.Usuario;
import com.utp.trazalab.usuarios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {
    private static final int TAMANO_PAGINA = 20;
    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") String pagina, Model model) {
        prepararModelo(model, numeroPagina(pagina));
        model.addAttribute("usuarioAlta", new UsuarioForm());
        return "usuarios/listado";
    }

    @PostMapping
    public String crear(@Valid @ModelAttribute("usuarioAlta") UsuarioForm formulario, BindingResult errores, @RequestParam(defaultValue = "0") String pagina, Model model, RedirectAttributes redireccion) {
        if (errores.hasErrors()) {
            prepararModelo(model, numeroPagina(pagina));
            return "usuarios/listado";
        }
        try {
            servicio.crear(formulario);
            redireccion.addFlashAttribute("mensajeExito", "La cuenta se creó correctamente.");
            conservarPagina(redireccion, numeroPagina(pagina));
            return "redirect:/usuarios";
        } catch (IllegalArgumentException error) {
            errores.reject("usuario.invalido", error.getMessage());
            prepararModelo(model, numeroPagina(pagina));
            return "usuarios/listado";
        }
    }

    @PostMapping("/{usuarioId}/estado")
    public String cambiarEstado(@PathVariable Integer usuarioId, @RequestParam(defaultValue = "0") String pagina, Authentication autenticacion, RedirectAttributes redireccion) {
        try {
            servicio.cambiarEstado(usuarioId, autenticacion.getName());
            redireccion.addFlashAttribute("mensajeExito", "El estado de la cuenta se actualizó.");
        } catch (IllegalArgumentException error) {
            redireccion.addFlashAttribute("mensajeError", error.getMessage());
        }
        conservarPagina(redireccion, numeroPagina(pagina));
        return "redirect:/usuarios";
    }

    private void prepararModelo(Model model, int pagina) {
        Page<Usuario> usuarios = servicio.listarUsuarios(PageRequest.of(pagina, TAMANO_PAGINA, Sort.by("id")));
        if (pagina > 0 && pagina >= usuarios.getTotalPages()) {
            usuarios = servicio.listarUsuarios(PageRequest.of(0, TAMANO_PAGINA, Sort.by("id")));
        }
        model.addAttribute("usuarios", usuarios.getContent());
        model.addAttribute("paginaUsuarios", usuarios);
        model.addAttribute("roles", servicio.listarRoles());
    }

    private int numeroPagina(String pagina) {
        try {
            int numero = Integer.parseInt(pagina);
            return numero < 0 || numero > Integer.MAX_VALUE / TAMANO_PAGINA ? 0 : numero;
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    private void conservarPagina(RedirectAttributes redireccion, int pagina) {
        if (pagina > 0) {
            redireccion.addAttribute("pagina", pagina);
        }
    }
}
