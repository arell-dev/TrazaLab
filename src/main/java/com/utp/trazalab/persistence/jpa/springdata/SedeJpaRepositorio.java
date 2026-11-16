package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.SedeEntidad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SedeJpaRepositorio extends JpaRepository<SedeEntidad, Integer> {
    Optional<SedeEntidad> findByNombreIgnoreCase(String nombre);
}
