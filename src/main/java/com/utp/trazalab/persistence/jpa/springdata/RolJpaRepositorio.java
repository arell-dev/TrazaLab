package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.RolEntidad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolJpaRepositorio extends JpaRepository<RolEntidad, Integer> {
    Optional<RolEntidad> findByNombreIgnoreCase(String nombre);
}
