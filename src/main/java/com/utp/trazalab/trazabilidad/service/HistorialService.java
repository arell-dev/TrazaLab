package com.utp.trazalab.trazabilidad.service;

import com.utp.trazalab.trazabilidad.dto.EventoHistorialDto;
import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.repository.ComputadoraRepository;
import com.utp.trazalab.trazabilidad.repository.HistorialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HistorialService {
    private static final int TAMANO_PAGINA_MAXIMO = 50;

    private final ComputadoraRepository computadoras;
    private final HistorialRepository historial;
    private final String sedeActiva;

    public HistorialService(ComputadoraRepository computadoras, HistorialRepository historial, @Value("${trazalab.sede-activa}") String sedeActiva) {
        this.computadoras = computadoras;
        this.historial = historial;
        this.sedeActiva = sedeActiva;
    }

    @Transactional(readOnly = true)
    public Page<EventoHistorialDto> consultar(String codigoQr, int pagina, int tamano) {
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new IllegalArgumentException("Ingresa el código QR de la computadora.");
        }

        Computadora computadora = computadoras.findByCodigoQrIgnoreCase(codigoQr.strip())
                .filter(equipo -> equipo.getUbicacion().getSede().getNombre().equalsIgnoreCase(sedeActiva))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el equipo en la sede activa."));
        return consultar(computadora, pagina, tamano);
    }

    @Transactional(readOnly = true)
    public Page<EventoHistorialDto> consultar(Computadora computadora, int pagina, int tamano) {
        int numeroPagina = Math.max(0, pagina);
        int limite = Math.max(1, Math.min(tamano, TAMANO_PAGINA_MAXIMO));
        PageRequest pageable = PageRequest.of(numeroPagina, limite);
        return historial.consultar(computadora.getId(), pageable);
    }
}
