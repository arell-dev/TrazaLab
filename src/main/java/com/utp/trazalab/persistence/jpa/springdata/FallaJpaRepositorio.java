package com.utp.trazalab.persistence.jpa.springdata;

import com.utp.trazalab.persistence.jpa.entity.FallaEntidad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FallaJpaRepositorio extends JpaRepository<FallaEntidad, Integer> {
}
