package com.utp.trazalab.trazabilidad.repository;

import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import com.utp.trazalab.trazabilidad.model.Computadora;
import java.util.Collection;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ComputadoraRepository extends JpaRepository<Computadora, Integer> {
    @EntityGraph(attributePaths = {"ubicacion", "ubicacion.sede"})
    Optional<Computadora> findByCodigoQrIgnoreCase(String codigoQr);

    @EntityGraph(attributePaths = {"ubicacion", "ubicacion.sede"})
    @Query("""
            SELECT computadora
            FROM Computadora computadora
            WHERE computadora.ubicacion.id IN :ubicacionIds
                AND (
                    LOWER(computadora.codigoQr) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                    OR LOWER(computadora.numeroSerie) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                    OR LOWER(computadora.marca) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                    OR LOWER(computadora.modelo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                )
            """)
    Page<Computadora> buscarPorUbicaciones(@Param("ubicacionIds") Collection<Integer> ubicacionIds, @Param("busqueda") String busqueda, Pageable pageable);

    @EntityGraph(attributePaths = {"ubicacion", "ubicacion.sede"})
    @Query("""
            SELECT computadora
            FROM Computadora computadora
            WHERE computadora.ubicacion.id IN :ubicacionIds
            """)
    Page<Computadora> listarPorUbicaciones(@Param("ubicacionIds") Collection<Integer> ubicacionIds, Pageable pageable);

    @EntityGraph(attributePaths = {"ubicacion", "ubicacion.sede"})
    @Query("""
            SELECT computadora
            FROM Computadora computadora
            WHERE computadora.ubicacion.id IN :ubicacionIds
                AND computadora.estado IN :estados
                AND (
                    LOWER(computadora.codigoQr) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                    OR LOWER(computadora.numeroSerie) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                    OR LOWER(computadora.marca) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                    OR LOWER(computadora.modelo) LIKE LOWER(CONCAT('%', :busqueda, '%'))
                )
            """)
    Page<Computadora> filtrarPorUbicaciones(@Param("ubicacionIds") Collection<Integer> ubicacionIds, @Param("busqueda") String busqueda, @Param("estados") Collection<EstadoComputadora> estados, Pageable pageable);

    @Query("""
            SELECT DISTINCT computadora.ubicacion.id
            FROM Computadora computadora
            WHERE computadora.ubicacion.sede.id = :sedeId
            """)
    Collection<Integer> findIdsDeUbicacionConEquipos(@Param("sedeId") Integer sedeId);

    long countByUbicacion_IdIn(Collection<Integer> ubicacionIds);

    @Query("""
            SELECT computadora.estado, COUNT(computadora)
            FROM Computadora computadora
            WHERE computadora.ubicacion.id IN :ubicacionIds
            GROUP BY computadora.estado
            """)
    List<Object[]> contarPorEstado(@Param("ubicacionIds") Collection<Integer> ubicacionIds);

    long countByUbicacion_IdInAndEstado(Collection<Integer> ubicacionIds, EstadoComputadora estado);
}
