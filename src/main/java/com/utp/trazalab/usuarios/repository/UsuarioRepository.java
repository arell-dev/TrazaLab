package com.utp.trazalab.usuarios.repository;

import com.utp.trazalab.usuarios.model.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {
    @Override
    @EntityGraph(attributePaths = "rol")
    List<Usuario> findAll();

    @Override
    @EntityGraph(attributePaths = "rol")
    Page<Usuario> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "rol")
    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    long countByEstadoIgnoreCaseAndRol_NombreIgnoreCase(String estado, String nombreRol);
}
