package com.utp.trazalab.repository;

import com.utp.trazalab.model.RolEntidad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolJpaRepositorio extends JpaRepository<RolEntidad, Integer> {
    Optional<RolEntidad> findByNombreIgnoreCase(String nombre);
}
