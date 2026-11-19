package com.utp.trazalab.app;

import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.model.Sede;
import com.utp.trazalab.trazabilidad.model.Ubicacion;
import com.utp.trazalab.trazabilidad.repository.ComputadoraRepository;
import com.utp.trazalab.trazabilidad.repository.SedeRepository;
import com.utp.trazalab.trazabilidad.repository.UbicacionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@ConditionalOnProperty(name = "trazalab.seed.enabled", havingValue = "true")
public class DatosDemoRunner implements CommandLineRunner {
    private final SedeRepository sedes;
    private final UbicacionRepository ubicaciones;
    private final ComputadoraRepository computadoras;
    public DatosDemoRunner(SedeRepository sedes, UbicacionRepository ubicaciones, ComputadoraRepository computadoras) {
        this.sedes = sedes;
        this.ubicaciones = ubicaciones;
        this.computadoras = computadoras;
    }

    @Override
    public void run(String... args) {
        Sede sedePiura = sedes.findByNombreIgnoreCase("UTP Sede Piura")
                .orElseThrow(() -> new IllegalStateException("Falta la sede UTP Sede Piura."));
        Ubicacion ubicacion = ubicaciones.findByNombreIgnoreCaseAndSede_NombreIgnoreCase("A0301", sedePiura.getNombre())
                .orElseThrow(() -> new IllegalStateException("Falta el aula A0301 en la sede UTP Sede Piura."));
        computadoras.findByCodigoQrIgnoreCase("PC-001")
                .orElseGet(() -> computadoras.save(new Computadora("PC-001", "TRAZALAB-DEMO-001", "HP", "EliteDesk", ubicacion, EstadoComputadora.OPERATIVA)));
    }
}
