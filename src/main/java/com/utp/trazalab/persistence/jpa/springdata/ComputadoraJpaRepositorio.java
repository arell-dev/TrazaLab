package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComputadoraJpaRepositorio extends JpaRepository<ComputadoraEntidad, Integer> {
    @EntityGraph(attributePaths = "ubicacion")
    Optional<ComputadoraEntidad> findByCodigoQrIgnoreCase(String codigoQr);
}
