package com.utp.trazalab.usuarios.controller;

import java.util.Collection;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SesionAdvice {
    @ModelAttribute
    public void agregarDatosDeSesion(Authentication autenticacion, Model model) {
        Collection<? extends GrantedAuthority> autoridades = autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken
                ? java.util.List.of()
                : autenticacion.getAuthorities();
        model.addAttribute("correoSesion", autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken ? "" : autenticacion.getName());
        model.addAttribute("puedeGestionarUsuarios", tieneAutoridad(autoridades, "ROLE_Administrador"));
        model.addAttribute("puedeRegistrarMantenimiento", tieneAlgunaAutoridad(autoridades, "ROLE_Administrador", "ROLE_Técnico de Soporte", "ROLE_Técnico de Laboratorio"));
        model.addAttribute("puedeConsultarReportes", tieneAlgunaAutoridad(autoridades, "ROLE_Administrador", "ROLE_Técnico de Soporte", "ROLE_Técnico de Laboratorio", "ROLE_Responsable"));
        model.addAttribute("puedeGestionarEquipos", tieneAlgunaAutoridad(autoridades, "ROLE_Administrador", "ROLE_Técnico de Soporte", "ROLE_Técnico de Laboratorio", "ROLE_Responsable"));
    }

    private boolean tieneAlgunaAutoridad(Collection<? extends GrantedAuthority> autoridades, String... nombres) {
        for (String nombre : nombres) {
            if (tieneAutoridad(autoridades, nombre)) {
                return true;
            }
        }

        return false;
    }

    private boolean tieneAutoridad(Collection<? extends GrantedAuthority> autoridades, String nombre) {
        return autoridades.stream().anyMatch(autoridad -> autoridad.getAuthority().equals(nombre));
    }
}
