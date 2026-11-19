package com.utp.trazalab.trazabilidad.repository;

import com.utp.trazalab.trazabilidad.model.Incidencia;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidenciaRepository extends JpaRepository<Incidencia, Integer> {
    @EntityGraph(attributePaths = {"usuario", "computadora"})
    Page<Incidencia> findAllByOrderByFechaRegistroDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "computadora"})
    Page<Incidencia> findAllByComputadora_IdOrderByFechaRegistroAscIdAsc(Integer computadoraId, Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "computadora"})
    Page<Incidencia> findAllByComputadora_IdOrderByFechaRegistroDescIdDesc(Integer computadoraId, Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "computadora"})
    List<Incidencia> findTop5ByComputadora_IdOrderByFechaRegistroDescIdDesc(Integer computadoraId);

}
