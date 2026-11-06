package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.IncidenciaEntidad;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidenciaJpaRepositorio extends JpaRepository<IncidenciaEntidad, Integer> {
    @EntityGraph(attributePaths = {"usuario", "computadora"})
    Page<IncidenciaEntidad> findAllByOrderByFechaRegistroDescIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"usuario", "computadora"})
    Page<IncidenciaEntidad> findAllByComputadora_IdOrderByFechaRegistroAscIdAsc(Integer computadoraId, Pageable pageable);

}
