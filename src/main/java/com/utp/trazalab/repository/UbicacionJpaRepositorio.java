package com.utp.trazalab.repository;

import com.utp.trazalab.model.UbicacionEntidad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UbicacionJpaRepositorio extends JpaRepository<UbicacionEntidad, Integer> {
    Optional<UbicacionEntidad> findByNombreIgnoreCase(String nombre);
}
