package com.utp.trazalab.controller;

import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.dto.RegistroIncidenciaDto;
import com.utp.trazalab.dto.RegistroIncidenciaFormulario;
import com.utp.trazalab.dto.UsuarioResumenDto;
import com.utp.trazalab.service.IncidenciaAplicacionServicio;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/incidencias")
public class IncidenciaWebController {
    private static final int TAMANO_PAGINA = 25;

    private final IncidenciaAplicacionServicio servicio;
    private final String correoUsuarioDemostracion;

    public IncidenciaWebController(
            IncidenciaAplicacionServicio servicio,
            @Value("${trazalab.demo.usuario-correo}") String correoUsuarioDemostracion) {
        this.servicio = servicio;
        this.correoUsuarioDemostracion = correoUsuarioDemostracion;
    }

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int pagina, Model model) {
        int paginaActual = Math.max(pagina, 0);
        Page<IncidenciaRegistradaDto> incidencias = servicio.consultarIncidencias(
                PageRequest.of(paginaActual, TAMANO_PAGINA));
        if (paginaActual > 0 && paginaActual >= incidencias.getTotalPages()) {
            return "redirect:/incidencias";
        }

        model.addAttribute("incidencias", incidencias);
        return "incidencias/listado";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        UsuarioResumenDto usuarioDemostracion = consultarUsuarioDemostracion();
        model.addAttribute("registroIncidencia", new RegistroIncidenciaFormulario());
        model.addAttribute("usuarioDemostracion", usuarioDemostracion);
        return "incidencias/nueva";
    }

    @PostMapping
    public String registrar(
            @Valid @ModelAttribute("registroIncidencia") RegistroIncidenciaFormulario formulario,
            BindingResult errores,
            Model model,
            RedirectAttributes redireccion) {
        UsuarioResumenDto usuarioDemostracion = consultarUsuarioDemostracion();
        if (errores.hasErrors()) {
            model.addAttribute("usuarioDemostracion", usuarioDemostracion);
            return "incidencias/nueva";
        }

        try {
            RegistroIncidenciaDto solicitud = new RegistroIncidenciaDto();
            solicitud.setUsuarioId(usuarioDemostracion.id());
            solicitud.setCodigoQr(formulario.getCodigoQr());
            solicitud.setDescripcion(formulario.getDescripcion());
            servicio.registrar(solicitud);
            redireccion.addFlashAttribute("mensajeExito", "La incidencia se registró correctamente.");
            return "redirect:/incidencias";
        } catch (IllegalArgumentException error) {
            errores.reject("incidencia.invalida", error.getMessage());
            model.addAttribute("usuarioDemostracion", usuarioDemostracion);
            return "incidencias/nueva";
        }
    }

    private UsuarioResumenDto consultarUsuarioDemostracion() {
        return servicio.consultarUsuarioDemostracion(correoUsuarioDemostracion);
    }
}
