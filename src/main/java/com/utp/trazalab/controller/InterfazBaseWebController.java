package com.utp.trazalab.controller;

import com.utp.trazalab.domain.enums.EstadoComputadora;
import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import com.utp.trazalab.persistence.jpa.entity.UbicacionEntidad;
import com.utp.trazalab.persistence.jpa.springdata.ComputadoraJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UbicacionJpaRepositorio;
import com.utp.trazalab.service.HistorialTecnicoConsultaServicio;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

@Controller
public class InterfazBaseWebController {
    private static final int TAMANO_PAGINA = 5;

    private final ComputadoraJpaRepositorio computadoras;
    private final UbicacionJpaRepositorio ubicaciones;
    private final HistorialTecnicoConsultaServicio historialTecnico;
    private final String sedeActiva;

    public InterfazBaseWebController(ComputadoraJpaRepositorio computadoras, UbicacionJpaRepositorio ubicaciones, HistorialTecnicoConsultaServicio historialTecnico, @Value("${trazalab.sede-activa}") String sedeActiva) {
        this.computadoras = computadoras;
        this.ubicaciones = ubicaciones;
        this.historialTecnico = historialTecnico;
        this.sedeActiva = sedeActiva;
    }

    @GetMapping("/disponibilidad")
    public String disponibilidad(@RequestParam(required = false) Integer ubicacionId, @RequestParam(required = false) String buscar, @RequestParam(defaultValue = "0") int pagina, Model model) {
        List<UbicacionEntidad> todasLasUbicaciones = ubicaciones.findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc(sedeActiva);
        Set<Integer> idsUbicacionesConEquipos = todasLasUbicaciones.isEmpty()
                ? Set.of()
                : new HashSet<>(computadoras.findIdsDeUbicacionConEquipos(todasLasUbicaciones.getFirst().getSede().getId()));
        List<UbicacionEntidad> ubicacionesDisponibles = filtrarUbicacionesDelMenu(todasLasUbicaciones, idsUbicacionesConEquipos);
        UbicacionEntidad ubicacionSeleccionada = seleccionarUbicacion(ubicacionId, ubicacionesDisponibles);
        Map<String, List<UbicacionEntidad>> ubicacionesAgrupadas = agruparUbicaciones(ubicacionesDisponibles);
        Map<Integer, String> etiquetasEquipos = new HashMap<>();
        List<Integer> idsUbicacion = ubicacionSeleccionada == null ? List.of() : buscarIdsUbicacion(ubicacionSeleccionada);
        String busqueda = buscar == null || buscar.isBlank() ? null : buscar.strip();
        Page<ComputadoraEntidad> paginaEquipos = Page.empty();
        long totalEquipos = 0;
        long equiposOperativos = 0;
        long equiposEnMantenimiento = 0;
        long equiposFueraDeServicio = 0;

        if (!idsUbicacion.isEmpty()) {
            totalEquipos = computadoras.countByUbicacion_IdIn(idsUbicacion);
            equiposOperativos = computadoras.countByUbicacion_IdInAndEstado(idsUbicacion, EstadoComputadora.OPERATIVA);
            equiposEnMantenimiento = computadoras.countByUbicacion_IdInAndEstado(idsUbicacion, EstadoComputadora.EN_MANTENIMIENTO);
            equiposFueraDeServicio = computadoras.countByUbicacion_IdInAndEstado(idsUbicacion, EstadoComputadora.FUERA_DE_SERVICIO);

            Pageable pageable = PageRequest.of(Math.max(0, pagina), TAMANO_PAGINA, ordenPorCodigoQr());
            paginaEquipos = consultarEquipos(idsUbicacion, busqueda, pageable);

            if (!paginaEquipos.hasContent() && paginaEquipos.getTotalPages() > 0) {
                pageable = PageRequest.of(paginaEquipos.getTotalPages() - 1, TAMANO_PAGINA, ordenPorCodigoQr());
                paginaEquipos = consultarEquipos(idsUbicacion, busqueda, pageable);
            }

            for (ComputadoraEntidad computadora : paginaEquipos.getContent()) {
                etiquetasEquipos.put(computadora.getId(), etiquetaUbicacion(computadora.getUbicacion()));
            }
        }

        long inicioResultados = paginaEquipos.hasContent() ? (long) paginaEquipos.getNumber() * TAMANO_PAGINA + 1 : 0;
        long finResultados = paginaEquipos.hasContent() ? inicioResultados + paginaEquipos.getNumberOfElements() - 1 : 0;

        model.addAttribute("ubicacionesAgrupadas", ubicacionesAgrupadas);
        model.addAttribute("ubicacionSeleccionada", ubicacionSeleccionada);
        model.addAttribute("etiquetaUbicacionSeleccionada", etiquetaUbicacion(ubicacionSeleccionada));
        model.addAttribute("etiquetasEquipos", etiquetasEquipos);
        model.addAttribute("paginaEquipos", paginaEquipos);
        model.addAttribute("busqueda", busqueda == null ? "" : busqueda);
        model.addAttribute("totalEquipos", totalEquipos);
        model.addAttribute("equiposOperativos", equiposOperativos);
        model.addAttribute("equiposEnMantenimiento", equiposEnMantenimiento);
        model.addAttribute("equiposFueraDeServicio", equiposFueraDeServicio);
        model.addAttribute("porcentajeOperativos", porcentaje(equiposOperativos, totalEquipos));
        model.addAttribute("porcentajeEnMantenimiento", porcentaje(equiposEnMantenimiento, totalEquipos));
        model.addAttribute("porcentajeFueraDeServicio", porcentaje(equiposFueraDeServicio, totalEquipos));
        model.addAttribute("inicioResultados", inicioResultados);
        model.addAttribute("finResultados", finResultados);
        model.addAttribute("paginasVisibles", paginasVisibles(paginaEquipos.getNumber(), paginaEquipos.getTotalPages()));

        return "equipos/disponibilidad";
    }

    @GetMapping("/equipos/escaneo")
    public String escaneo() {
        return "equipos/escaneo";
    }

    @GetMapping("/equipos/buscar")
    public String buscarEquipo(@RequestParam String codigoQr) {
        if (codigoQr.isBlank()) {
            return "redirect:/equipos/escaneo";
        }

        return "redirect:/equipos/" + UriUtils.encodePathSegment(codigoQr.strip(), StandardCharsets.UTF_8);
    }

    @GetMapping("/equipos/{codigoQr}")
    public String fichaEquipo(@PathVariable String codigoQr, @RequestParam(defaultValue = "0") int paginaHistorial, Model model) {
        ComputadoraEntidad computadora = computadoras.findByCodigoQrIgnoreCase(codigoQr)
                .filter(equipo -> equipo.getUbicacion().getSede().getNombre().equalsIgnoreCase(sedeActiva))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el equipo en la sede activa."));

        model.addAttribute("computadora", computadora);
        model.addAttribute("etiquetaUbicacion", etiquetaUbicacion(computadora.getUbicacion()));
        model.addAttribute("historialTecnico", historialTecnico.consultar(codigoQr, paginaHistorial, 10));

        return "equipos/ficha";
    }

    private UbicacionEntidad seleccionarUbicacion(Integer ubicacionId, List<UbicacionEntidad> ubicacionesDisponibles) {
        if (ubicacionId != null) {
            UbicacionEntidad solicitada = ubicacionesDisponibles.stream()
                    .filter(ubicacion -> ubicacion.getId().equals(ubicacionId))
                    .findFirst()
                    .orElse(null);

            if (solicitada != null) {
                return solicitada;
            }
        }

        return ubicacionesDisponibles.stream()
                .filter(ubicacion -> "LAB-01".equalsIgnoreCase(ubicacion.getNombre()))
                .findFirst()
                .orElse(ubicacionesDisponibles.isEmpty() ? null : ubicacionesDisponibles.getFirst());
    }

    private Map<String, List<UbicacionEntidad>> agruparUbicaciones(List<UbicacionEntidad> ubicacionesDisponibles) {
        Map<String, List<UbicacionEntidad>> ubicacionesAgrupadas = new LinkedHashMap<>();

        for (UbicacionEntidad ubicacion : ubicacionesDisponibles) {
            String grupo = grupoUbicacion(ubicacion);
            ubicacionesAgrupadas.computeIfAbsent(grupo, clave -> new ArrayList<>()).add(ubicacion);
        }

        return ubicacionesAgrupadas;
    }

    private List<UbicacionEntidad> filtrarUbicacionesDelMenu(List<UbicacionEntidad> todasLasUbicaciones, Set<Integer> idsUbicacionesConEquipos) {
        return todasLasUbicaciones.stream()
                .filter(ubicacion -> debeMostrarUbicacion(ubicacion, idsUbicacionesConEquipos))
                .toList();
    }

    private boolean debeMostrarUbicacion(UbicacionEntidad ubicacion, Set<Integer> idsUbicacionesConEquipos) {
        if ("TORRE".equals(ubicacion.getTipo()) || "PISO".equals(ubicacion.getTipo())) {
            return idsUbicacionesConEquipos.contains(ubicacion.getId());
        }

        return true;
    }

    private List<Integer> buscarIdsUbicacion(UbicacionEntidad ubicacion) {
        Collection<UbicacionEntidad> ubicacionesDelNivel;
        if ("TORRE".equals(ubicacion.getTipo())) {
            String edificio = ubicacion.getEdificio() == null ? ubicacion.getNombre() : ubicacion.getEdificio();
            ubicacionesDelNivel = ubicaciones.findAllBySede_IdAndEdificioIgnoreCase(ubicacion.getSede().getId(), edificio);
        } else if ("PISO".equals(ubicacion.getTipo()) && ubicacion.getPiso() != null && ubicacion.getEdificio() != null) {
            ubicacionesDelNivel = ubicaciones.findAllBySede_IdAndEdificioIgnoreCaseAndPiso(ubicacion.getSede().getId(), ubicacion.getEdificio(), ubicacion.getPiso());
        } else {
            ubicacionesDelNivel = List.of(ubicacion);
        }

        return ubicacionesDelNivel.stream()
                .map(UbicacionEntidad::getId)
                .toList();
    }

    private Page<ComputadoraEntidad> consultarEquipos(Collection<Integer> ubicacionIds, String busqueda, Pageable pageable) {
        if (busqueda == null) {
            return computadoras.listarPorUbicaciones(ubicacionIds, pageable);
        }

        return computadoras.buscarPorUbicaciones(ubicacionIds, busqueda, pageable);
    }

    private String grupoUbicacion(UbicacionEntidad ubicacion) {
        if ("TORRE".equals(ubicacion.getTipo())) {
            return ubicacion.getSede().getNombre().toUpperCase(Locale.ROOT);
        }

        String torre = ubicacion.getEdificio() == null ? "Torre por asignar" : ubicacion.getEdificio();
        if (ubicacion.getPiso() == null) {
            return torre.toUpperCase(Locale.ROOT);
        }

        return "PISO " + ubicacion.getPiso() + " · " + torre.toUpperCase(Locale.ROOT);
    }

    private String etiquetaUbicacion(UbicacionEntidad ubicacion) {
        if (ubicacion == null) {
            return "Selecciona una ubicación";
        }

        if ("TORRE".equals(ubicacion.getTipo())) {
            return ubicacion.getNombre();
        }

        if ("PISO".equals(ubicacion.getTipo())) {
            return ubicacion.getNombre() + " · " + ubicacion.getEdificio();
        }

        String etiqueta = ubicacion.getNombre();
        if (ubicacion.getEdificio() != null) {
            etiqueta += " · " + ubicacion.getEdificio();
        }
        if (ubicacion.getPiso() != null) {
            etiqueta += ", Piso " + ubicacion.getPiso();
        }
        if (ubicacion.getEdificio() == null && ubicacion.getPiso() == null && ubicacion.getDetalle() != null) {
            etiqueta += " · " + ubicacion.getDetalle();
        }

        return etiqueta;
    }

    private long porcentaje(long cantidad, long total) {
        return total == 0 ? 0 : Math.round(cantidad * 100.0 / total);
    }

    private Sort ordenPorCodigoQr() {
        return Sort.by(ComputadoraEntidad::getCodigoQr).ascending();
    }

    private List<Integer> paginasVisibles(int paginaActual, int totalPaginas) {
        if (totalPaginas <= 5) {
            return java.util.stream.IntStream.range(0, totalPaginas).boxed().toList();
        }

        int primeraPagina = Math.max(0, paginaActual - 1);
        int ultimaPagina = Math.min(totalPaginas - 1, paginaActual + 1);
        if (paginaActual <= 1) {
            ultimaPagina = Math.min(totalPaginas - 1, 2);
        }
        if (paginaActual >= totalPaginas - 2) {
            primeraPagina = Math.max(0, totalPaginas - 3);
        }

        TreeSet<Integer> paginas = new TreeSet<>();
        paginas.add(0);
        paginas.add(totalPaginas - 1);
        for (int numero = primeraPagina; numero <= ultimaPagina; numero++) {
            paginas.add(numero);
        }

        return new ArrayList<>(paginas);
    }
}
