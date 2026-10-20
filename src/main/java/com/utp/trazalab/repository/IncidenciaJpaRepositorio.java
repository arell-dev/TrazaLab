package com.utp.trazalab.repository;

import com.utp.trazalab.model.IncidenciaEntidad;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidenciaJpaRepositorio extends JpaRepository<IncidenciaEntidad, Integer> {
    @EntityGraph(attributePaths = {"usuario", "computadora"})
    List<IncidenciaEntidad> findAllByOrderByFechaRegistroDesc();

    @EntityGraph(attributePaths = {"usuario", "computadora"})
    List<IncidenciaEntidad> findAllByComputadora_IdOrderByFechaRegistroAsc(Integer computadoraId);
}
