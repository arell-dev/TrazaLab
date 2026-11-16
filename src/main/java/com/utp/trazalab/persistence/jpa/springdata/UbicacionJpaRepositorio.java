package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.UbicacionEntidad;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UbicacionJpaRepositorio extends JpaRepository<UbicacionEntidad, Integer> {
    @EntityGraph(attributePaths = "sede")
    List<UbicacionEntidad> findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc(String sede);

    Optional<UbicacionEntidad> findByNombreIgnoreCaseAndSede_NombreIgnoreCase(String nombre, String sede);

    List<UbicacionEntidad> findAllBySede_IdAndEdificioIgnoreCase(Integer sedeId, String edificio);

    List<UbicacionEntidad> findAllBySede_IdAndEdificioIgnoreCaseAndPiso(Integer sedeId, String edificio, Integer piso);
}
