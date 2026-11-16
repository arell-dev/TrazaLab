package com.utp.trazalab.config;

import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioAutenticacionServicio implements UserDetailsService {
    private final UsuarioJpaRepositorio usuarios;

    public UsuarioAutenticacionServicio(UsuarioJpaRepositorio usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) {
        UsuarioEntidad usuario = usuarios.findByCorreoIgnoreCase(correo)
                .filter(cuenta -> "activo".equalsIgnoreCase(cuenta.getEstado()))
                .filter(cuenta -> cuenta.getPasswordHash() != null && !cuenta.getPasswordHash().isBlank())
                .orElseThrow(() -> new UsernameNotFoundException("Correo o contraseña incorrectos."));

        return User.withUsername(usuario.getCorreo())
                .password(usuario.getPasswordHash())
                .authorities("ROLE_" + usuario.getRol().getNombre())
                .build();
    }
}
