package com.utp.trazalab.trazabilidad.repository;

import com.utp.trazalab.trazabilidad.model.Componente;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponenteRepository extends JpaRepository<Componente, Integer> {
    Optional<Componente> findByNumeroSerieIgnoreCase(String numeroSerie);
}
