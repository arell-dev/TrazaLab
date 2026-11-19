package com.utp.trazalab.trazabilidad.repository;

import com.utp.trazalab.trazabilidad.model.Ubicacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UbicacionRepository extends JpaRepository<Ubicacion, Integer> {
    @EntityGraph(attributePaths = "sede")
    List<Ubicacion> findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc(String sede);

    @EntityGraph(attributePaths = "sede")
    Optional<Ubicacion> findByIdAndSede_NombreIgnoreCase(Integer id, String sede);

    Optional<Ubicacion> findByNombreIgnoreCaseAndSede_NombreIgnoreCase(String nombre, String sede);

    List<Ubicacion> findAllBySede_IdAndEdificioIgnoreCase(Integer sedeId, String edificio);

    List<Ubicacion> findAllBySede_IdAndEdificioIgnoreCaseAndPiso(Integer sedeId, String edificio, Integer piso);
}
