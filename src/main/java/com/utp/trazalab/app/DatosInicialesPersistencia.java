package com.utp.trazalab.app;

import com.utp.trazalab.domain.enums.EstadoComputadora;
import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import com.utp.trazalab.persistence.jpa.entity.RolEntidad;
import com.utp.trazalab.persistence.jpa.entity.UbicacionEntidad;
import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.ComputadoraJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.RolJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UbicacionJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@ConditionalOnProperty(name = "trazalab.seed.enabled", havingValue = "true")
public class DatosInicialesPersistencia implements CommandLineRunner {
    private final RolJpaRepositorio roles;
    private final UsuarioJpaRepositorio usuarios;
    private final UbicacionJpaRepositorio ubicaciones;
    private final ComputadoraJpaRepositorio computadoras;
    private final String correoUsuarioDemostracion;
    private final String correoTecnicoDemostracion;

    public DatosInicialesPersistencia(
            RolJpaRepositorio roles,
            UsuarioJpaRepositorio usuarios,
            UbicacionJpaRepositorio ubicaciones,
            ComputadoraJpaRepositorio computadoras,
            @Value("${trazalab.demo.usuario-correo}") String correoUsuarioDemostracion,
            @Value("${trazalab.demo.mantenimiento-correo}") String correoTecnicoDemostracion) {
        this.roles = roles;
        this.usuarios = usuarios;
        this.ubicaciones = ubicaciones;
        this.computadoras = computadoras;
        this.correoUsuarioDemostracion = correoUsuarioDemostracion;
        this.correoTecnicoDemostracion = correoTecnicoDemostracion;
    }

    @Override
    public void run(String... args) {
        RolEntidad rolDocente = roles.findByNombreIgnoreCase("Docente")
                .orElseThrow(() -> new IllegalStateException("Falta el rol Docente. Inicializa primero el esquema PostgreSQL."));
        usuarios.findByCorreoIgnoreCase(correoUsuarioDemostracion)
                .orElseGet(() -> usuarios.save(new UsuarioEntidad(
                        rolDocente,
                        "Ana",
                        "Torres",
                        correoUsuarioDemostracion,
                        "activo")));
        RolEntidad rolTecnico = roles.findByNombreIgnoreCase("Técnico de Soporte")
                .orElseThrow(() -> new IllegalStateException("Falta el rol Técnico de Soporte."));
        usuarios.findByCorreoIgnoreCase(correoTecnicoDemostracion)
                .orElseGet(() -> usuarios.save(new UsuarioEntidad(
                        rolTecnico,
                        "Técnico",
                        "Demostración",
                        correoTecnicoDemostracion,
                        "activo")));

        UbicacionEntidad ubicacion = ubicaciones.findByNombreIgnoreCase("LAB-01")
                .orElseGet(() -> ubicaciones.save(new UbicacionEntidad("LAB-01", "Laboratorio de demostración")));
        computadoras.findByCodigoQrIgnoreCase("PC-001")
                .orElseGet(() -> computadoras.save(new ComputadoraEntidad("PC-001", "TRAZALAB-DEMO-001", "HP", "EliteDesk", ubicacion, EstadoComputadora.OPERATIVA)));
    }
}
