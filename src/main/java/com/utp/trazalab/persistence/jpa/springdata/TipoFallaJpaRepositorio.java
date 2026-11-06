package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.TipoFallaEntidad;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipoFallaJpaRepositorio extends JpaRepository<TipoFallaEntidad, Integer> {
    @EntityGraph(attributePaths = "categoria")
    List<TipoFallaEntidad> findAllByOrderByCategoria_NombreAscNombreAsc();
}
