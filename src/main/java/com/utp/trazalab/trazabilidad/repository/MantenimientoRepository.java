package com.utp.trazalab.trazabilidad.repository;

import com.utp.trazalab.trazabilidad.model.Mantenimiento;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MantenimientoRepository extends JpaRepository<Mantenimiento, Integer> {
    @EntityGraph(attributePaths = {"computadora", "usuario"})
    List<Mantenimiento> findTop50ByOrderByFechaDescIdDesc();
}
