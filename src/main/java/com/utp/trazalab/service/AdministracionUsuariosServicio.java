package com.utp.trazalab.service;

import com.utp.trazalab.dto.UsuarioAltaFormulario;
import com.utp.trazalab.persistence.jpa.entity.RolEntidad;
import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.RolJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministracionUsuariosServicio {
    private final UsuarioJpaRepositorio usuarios;
    private final RolJpaRepositorio roles;
    private final PasswordEncoder codificador;

    public AdministracionUsuariosServicio(UsuarioJpaRepositorio usuarios, RolJpaRepositorio roles, PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.codificador = codificador;
    }

    @Transactional(readOnly = true)
    public List<UsuarioEntidad> listarUsuarios() {
        return usuarios.findAll();
    }

    @Transactional(readOnly = true)
    public List<RolEntidad> listarRoles() {
        return roles.findAll();
    }

    @Transactional
    public void crear(UsuarioAltaFormulario formulario) {
        String correo = formulario.getCorreo().strip();
        if (formulario.getContrasena().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contraseña no puede superar 72 bytes UTF-8.");
        }
        if (usuarios.findByCorreoIgnoreCase(correo).isPresent()) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo.");
        }
        RolEntidad rol = roles.findById(formulario.getRolId())
                .orElseThrow(() -> new IllegalArgumentException("Selecciona un rol válido."));
        usuarios.save(new UsuarioEntidad(rol, formulario.getNombres().strip(), formulario.getApellidos().strip(), correo, "activo", codificador.encode(formulario.getContrasena())));
    }

    @Transactional
    public void cambiarEstado(Integer usuarioId, String correoAdministrador) {
        UsuarioEntidad usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cuenta solicitada."));
        if (usuario.getCorreo().equalsIgnoreCase(correoAdministrador)) {
            throw new IllegalArgumentException("No puedes desactivar la cuenta con la que iniciaste sesión.");
        }
        if ("activo".equalsIgnoreCase(usuario.getEstado()) && "Administrador".equalsIgnoreCase(usuario.getRol().getNombre())
                && usuarios.findAll().stream().filter(cuenta -> "activo".equalsIgnoreCase(cuenta.getEstado()))
                .filter(cuenta -> "Administrador".equalsIgnoreCase(cuenta.getRol().getNombre())).count() <= 1) {
            throw new IllegalArgumentException("Debe permanecer al menos un administrador activo.");
        }
        usuario.setEstado("activo".equalsIgnoreCase(usuario.getEstado()) ? "inactivo" : "activo");
        usuarios.save(usuario);
    }
}
