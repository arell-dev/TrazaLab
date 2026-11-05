package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.UbicacionEntidad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UbicacionJpaRepositorio extends JpaRepository<UbicacionEntidad, Integer> {
    Optional<UbicacionEntidad> findByNombreIgnoreCase(String nombre);
}
