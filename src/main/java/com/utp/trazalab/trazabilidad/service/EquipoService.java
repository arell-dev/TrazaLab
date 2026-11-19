package com.utp.trazalab.trazabilidad.service;

import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.repository.ComputadoraRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EquipoService {
    private final ComputadoraRepository computadoras;
    private final String sedeActiva;

    public EquipoService(ComputadoraRepository computadoras, @Value("${trazalab.sede-activa}") String sedeActiva) {
        this.computadoras = computadoras;
        this.sedeActiva = sedeActiva;
    }

    @Transactional(readOnly = true)
    public Computadora buscarPorQr(String codigoQr) {
        return computadoras.findByCodigoQrIgnoreCase(codigoQr)
                .filter(equipo -> equipo.getUbicacion().getSede().getNombre().equalsIgnoreCase(sedeActiva))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontr? el equipo en la sede activa."));
    }
}
