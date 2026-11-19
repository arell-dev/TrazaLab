package com.utp.trazalab.usuarios.security;

import com.utp.trazalab.usuarios.model.Usuario;
import com.utp.trazalab.usuarios.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsuarioDetailsService implements UserDetailsService {
    private final UsuarioRepository usuarios;

    public UsuarioDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) {
        Usuario usuario = usuarios.findByCorreoIgnoreCase(correo)
                .filter(cuenta -> "activo".equalsIgnoreCase(cuenta.getEstado()))
                .filter(cuenta -> cuenta.getPasswordHash() != null && !cuenta.getPasswordHash().isBlank())
                .orElseThrow(() -> new UsernameNotFoundException("Correo o contraseña incorrectos."));

        return User.withUsername(usuario.getCorreo())
                .password(usuario.getPasswordHash())
                .authorities("ROLE_" + usuario.getRol().getNombre())
                .build();
    }
}
