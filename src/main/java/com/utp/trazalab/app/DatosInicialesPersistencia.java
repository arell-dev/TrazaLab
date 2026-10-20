package com.utp.trazalab.app;

import com.utp.trazalab.model.EstadoComputadora;
import com.utp.trazalab.model.ComputadoraEntidad;
import com.utp.trazalab.model.RolEntidad;
import com.utp.trazalab.model.UbicacionEntidad;
import com.utp.trazalab.model.UsuarioEntidad;
import com.utp.trazalab.repository.ComputadoraJpaRepositorio;
import com.utp.trazalab.repository.RolJpaRepositorio;
import com.utp.trazalab.repository.UbicacionJpaRepositorio;
import com.utp.trazalab.repository.UsuarioJpaRepositorio;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("persistencia")
@Order(1)
@ConditionalOnProperty(name = "trazalab.persistencia.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DatosInicialesPersistencia implements CommandLineRunner {
    private final RolJpaRepositorio roles;
    private final UsuarioJpaRepositorio usuarios;
    private final UbicacionJpaRepositorio ubicaciones;
    private final ComputadoraJpaRepositorio computadoras;

    public DatosInicialesPersistencia(RolJpaRepositorio roles, UsuarioJpaRepositorio usuarios, UbicacionJpaRepositorio ubicaciones, ComputadoraJpaRepositorio computadoras) {
        this.roles = roles;
        this.usuarios = usuarios;
        this.ubicaciones = ubicaciones;
        this.computadoras = computadoras;
    }

    @Override
    public void run(String... args) {
        RolEntidad rolDocente = roles.findByNombreIgnoreCase("Docente")
                .orElseThrow(() -> new IllegalStateException("Falta el rol Docente. Inicializa primero el esquema PostgreSQL."));
        usuarios.findByCorreoIgnoreCase("ana.torres.demo@trazalab.local")
                .orElseGet(() -> usuarios.save(new UsuarioEntidad(rolDocente, "Ana", "Torres", "ana.torres.demo@trazalab.local", "activo")));

        UbicacionEntidad ubicacion = ubicaciones.findByNombreIgnoreCase("LAB-01")
                .orElseGet(() -> ubicaciones.save(new UbicacionEntidad("LAB-01", "Laboratorio de demostración")));
        computadoras.findByCodigoQrIgnoreCase("PC-001")
                .orElseGet(() -> computadoras.save(new ComputadoraEntidad("PC-001", "TRAZALAB-DEMO-001", "HP", "EliteDesk", ubicacion, EstadoComputadora.OPERATIVA)));
    }
}
