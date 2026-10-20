package com.utp.trazalab.repository;

import com.utp.trazalab.model.UsuarioEntidad;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioJpaRepositorio extends JpaRepository<UsuarioEntidad, Integer> {
    @Override
    @EntityGraph(attributePaths = "rol")
    List<UsuarioEntidad> findAll();

    Optional<UsuarioEntidad> findByCorreoIgnoreCase(String correo);
}
