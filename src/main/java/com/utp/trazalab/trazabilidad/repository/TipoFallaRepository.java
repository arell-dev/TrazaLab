package com.utp.trazalab.trazabilidad.repository;

import com.utp.trazalab.trazabilidad.model.TipoFalla;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoFallaRepository extends JpaRepository<TipoFalla, Integer> {
    @EntityGraph(attributePaths = "categoria")
    List<TipoFalla> findAllByOrderByCategoria_NombreAscNombreAsc();
}
