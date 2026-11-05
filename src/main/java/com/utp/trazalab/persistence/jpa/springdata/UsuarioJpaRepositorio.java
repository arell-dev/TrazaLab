package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioJpaRepositorio extends JpaRepository<UsuarioEntidad, Integer> {
    @Override
    @EntityGraph(attributePaths = "rol")
    List<UsuarioEntidad> findAll();

    @EntityGraph(attributePaths = "rol")
    Optional<UsuarioEntidad> findByCorreoIgnoreCase(String correo);
}
