package com.utp.trazalab.usuarios.repository;

import com.utp.trazalab.usuarios.model.Rol;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolRepository extends JpaRepository<Rol, Integer> {
    Optional<Rol> findByNombreIgnoreCase(String nombre);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rol FROM Rol rol WHERE LOWER(rol.nombre) = LOWER(:nombre)")
    Optional<Rol> bloquearPorNombre(@Param("nombre") String nombre);
}
