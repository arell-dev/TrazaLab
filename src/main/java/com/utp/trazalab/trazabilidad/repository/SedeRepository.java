package com.utp.trazalab.trazabilidad.repository;

import com.utp.trazalab.trazabilidad.model.Sede;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SedeRepository extends JpaRepository<Sede, Integer> {
    Optional<Sede> findByNombreIgnoreCase(String nombre);
}
