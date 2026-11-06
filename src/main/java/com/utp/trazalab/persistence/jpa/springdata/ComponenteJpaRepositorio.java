package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.ComponenteEntidad;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponenteJpaRepositorio extends JpaRepository<ComponenteEntidad, Integer> {
    Optional<ComponenteEntidad> findByNumeroSerieIgnoreCase(String numeroSerie);
}
