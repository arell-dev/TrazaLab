package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.MantenimientoEntidad;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MantenimientoJpaRepositorio extends JpaRepository<MantenimientoEntidad, Integer> {
    @EntityGraph(attributePaths = {"computadora", "usuario"})
    List<MantenimientoEntidad> findTop50ByOrderByFechaDescIdDesc();
}
