package com.utp.trazalab.app;

import com.utp.trazalab.persistence.jpa.entity.RolEntidad;
import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.RolJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class AdministradorInicial implements CommandLineRunner {
    private final UsuarioJpaRepositorio usuarios;
    private final RolJpaRepositorio roles;
    private final PasswordEncoder codificador;
    private final String correo;
    private final String contrasena;

    public AdministradorInicial(UsuarioJpaRepositorio usuarios, RolJpaRepositorio roles, PasswordEncoder codificador, @Value("${trazalab.admin.email:}") String correo, @Value("${trazalab.admin.password:}") String contrasena) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.codificador = codificador;
        this.correo = correo.strip();
        this.contrasena = contrasena;
    }

    @Override
    public void run(String... args) {
        if (correo.isBlank() && contrasena.isBlank()) {
            if (!usuarios.existsByEstadoIgnoreCaseAndPasswordHashIsNotNull("activo")) {
                throw new IllegalStateException("Configura TRAZALAB_ADMIN_EMAIL y TRAZALAB_ADMIN_PASSWORD para crear la primera cuenta administradora.");
            }
            return;
        }
        if (correo.isBlank() || contrasena.length() < 12 || contrasena.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Configura TRAZALAB_ADMIN_EMAIL y una contraseña inicial de al menos 12 caracteres.");
        }

        RolEntidad rolAdministrador = roles.findByNombreIgnoreCase("Administrador")
                .orElseThrow(() -> new IllegalStateException("No existe el rol Administrador en la base de datos."));
        UsuarioEntidad administrador = usuarios.findByCorreoIgnoreCase(correo)
                .orElseGet(() -> usuarios.save(new UsuarioEntidad(rolAdministrador, "Administrador", "TrazaLab", correo, "activo")));

        if (!"Administrador".equalsIgnoreCase(administrador.getRol().getNombre())) {
            throw new IllegalStateException("El correo configurado para el administrador ya pertenece a otro rol.");
        }

        if (administrador.getPasswordHash() == null || administrador.getPasswordHash().isBlank()) {
            administrador.setPasswordHash(codificador.encode(contrasena));
            administrador.setEstado("activo");
            usuarios.save(administrador);
        }
    }
}
