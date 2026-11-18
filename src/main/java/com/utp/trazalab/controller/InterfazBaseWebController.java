package com.utp.trazalab.controller;

import com.utp.trazalab.domain.enums.EstadoComputadora;
import com.utp.trazalab.persistence.jpa.entity.ComputadoraEntidad;
import com.utp.trazalab.persistence.jpa.entity.UbicacionEntidad;
import com.utp.trazalab.persistence.jpa.springdata.ComputadoraJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UbicacionJpaRepositorio;
import com.utp.trazalab.service.HistorialTecnicoConsultaServicio;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    public String disponibilidad(@RequestParam(required = false) Integer ubicacionId, @RequestParam(required = false) String edificio, @RequestParam(required = false) Integer piso, @RequestParam(required = false) String buscar, @RequestParam(defaultValue = "0") int pagina, Model model) {
        List<UbicacionEntidad> todasLasUbicaciones = ubicaciones.findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc(sedeActiva);
        Set<Integer> idsUbicacionesConEquipos = todasLasUbicaciones.isEmpty()
                ? Set.of()
                : new HashSet<>(computadoras.findIdsDeUbicacionConEquipos(todasLasUbicaciones.getFirst().getSede().getId()));
        List<UbicacionEntidad> aulasDisponibles = todasLasUbicaciones.stream()
                .filter(ubicacion -> "AULA".equals(ubicacion.getTipo()))
                .filter(aula -> aula.getEdificio() != null && aula.getPiso() != null)
                .toList();
        UbicacionEntidad ubicacionSeleccionada = seleccionarUbicacion(ubicacionId, aulasDisponibles);
        Map<String, List<Integer>> pisosPorTorre = obtenerPisosPorTorre(aulasDisponibles);
        String edificioSeleccionado = seleccionarEdificio(edificio, pisosPorTorre);
        Integer pisoSeleccionado = seleccionarPiso(piso, edificioSeleccionado, pisosPorTorre);
        boolean ubicacionNoDisponible = (edificio != null && edificioSeleccionado == null)
                || (piso != null && pisoSeleccionado == null)
                || (ubicacionId != null && ubicacionSeleccionada == null);

        if (ubicacionSeleccionada == null && ubicacionId == null && !ubicacionNoDisponible && edificioSeleccionado != null) {
            List<Integer> pisosDisponibles = pisosPorTorre.getOrDefault(edificioSeleccionado, List.of());
            if (pisoSeleccionado == null && piso == null && !pisosDisponibles.isEmpty()) {
                pisoSeleccionado = pisosDisponibles.getFirst();
            }

            if (pisoSeleccionado != null) {
                String edificioFinal = edificioSeleccionado;
                Integer pisoFinal = pisoSeleccionado;
                ubicacionSeleccionada = aulasDisponibles.stream()
                        .filter(aula -> aula.getEdificio() != null && aula.getEdificio().equalsIgnoreCase(edificioFinal))
                        .filter(aula -> pisoFinal.equals(aula.getPiso()))
                        .findFirst()
                        .orElse(null);
            }
        }

        if (ubicacionSeleccionada != null) {
            if (ubicacionNoDisponible
                    || (edificioSeleccionado != null && !edificioSeleccionado.equalsIgnoreCase(ubicacionSeleccionada.getEdificio()))
                    || (pisoSeleccionado != null && !pisoSeleccionado.equals(ubicacionSeleccionada.getPiso()))) {
                ubicacionNoDisponible = true;
                ubicacionSeleccionada = null;
            } else {
                edificioSeleccionado = ubicacionSeleccionada.getEdificio();
                pisoSeleccionado = ubicacionSeleccionada.getPiso();
            }
        }
        List<Integer> idsUbicacionesPorVerificar = todasLasUbicaciones.stream()
                .filter(ubicacion -> !"AULA".equals(ubicacion.getTipo())
                        || ubicacion.getEdificio() == null
                        || ubicacion.getPiso() == null)
                .map(UbicacionEntidad::getId)
                .filter(idsUbicacionesConEquipos::contains)
                .toList();
        long equiposConUbicacionPorVerificar = idsUbicacionesPorVerificar.isEmpty()
                ? 0
                : computadoras.countByUbicacion_IdIn(idsUbicacionesPorVerificar);
        Map<Integer, String> etiquetasEquipos = new HashMap<>();
        List<Integer> idsUbicacion = ubicacionSeleccionada == null ? List.of() : List.of(ubicacionSeleccionada.getId());
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

        model.addAttribute("aulasDisponibles", aulasDisponibles);
        model.addAttribute("pisosPorTorre", pisosPorTorre);
        model.addAttribute("edificioSeleccionado", edificioSeleccionado);
        model.addAttribute("pisoSeleccionado", pisoSeleccionado);
        model.addAttribute("ubicacionSeleccionada", ubicacionSeleccionada);
        model.addAttribute("etiquetaUbicacionSeleccionada", etiquetaUbicacion(ubicacionSeleccionada));
        model.addAttribute("ubicacionNoDisponible", ubicacionNoDisponible);
        model.addAttribute("equiposConUbicacionPorVerificar", equiposConUbicacionPorVerificar);
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

    private UbicacionEntidad seleccionarUbicacion(Integer ubicacionId, List<UbicacionEntidad> aulasDisponibles) {
        if (ubicacionId == null) {
            return null;
        }

        return aulasDisponibles.stream()
                .filter(aula -> aula.getId().equals(ubicacionId))
                .findFirst()
                .orElse(null);
    }

    private Map<String, List<Integer>> obtenerPisosPorTorre(List<UbicacionEntidad> aulasDisponibles) {
        Map<String, Set<Integer>> pisosPorTorre = new LinkedHashMap<>();

        for (UbicacionEntidad aula : aulasDisponibles) {
            pisosPorTorre.computeIfAbsent(aula.getEdificio(), clave -> new TreeSet<>()).add(aula.getPiso());
        }

        Map<String, List<Integer>> pisosOrdenadosPorTorre = new LinkedHashMap<>();
        pisosPorTorre.forEach((torre, pisos) -> pisosOrdenadosPorTorre.put(torre, new ArrayList<>(pisos)));

        return pisosOrdenadosPorTorre;
    }

    private String seleccionarEdificio(String edificio, Map<String, List<Integer>> pisosPorTorre) {
        if (edificio == null || edificio.isBlank()) {
            return null;
        }

        return pisosPorTorre.keySet().stream()
                .filter(torre -> torre.equalsIgnoreCase(edificio.strip()))
                .findFirst()
                .orElse(null);
    }

    private Integer seleccionarPiso(Integer piso, String edificio, Map<String, List<Integer>> pisosPorTorre) {
        if (piso == null || edificio == null) {
            return null;
        }

        return pisosPorTorre.get(edificio).stream()
                .filter(pisoDisponible -> pisoDisponible.equals(piso))
                .findFirst()
                .orElse(null);
    }

    private Page<ComputadoraEntidad> consultarEquipos(Collection<Integer> ubicacionIds, String busqueda, Pageable pageable) {
        if (busqueda == null) {
            return computadoras.listarPorUbicaciones(ubicacionIds, pageable);
        }

        return computadoras.buscarPorUbicaciones(ubicacionIds, busqueda, pageable);
    }

    private String etiquetaUbicacion(UbicacionEntidad ubicacion) {
        if (ubicacion == null) {
            return "Selecciona un aula";
        }

        String etiqueta = ubicacion.getNombre();
        if (ubicacion.getEdificio() != null) {
            etiqueta += " · " + ubicacion.getEdificio();
        }
        if (ubicacion.getPiso() != null) {
            etiqueta += ", piso " + ubicacion.getPiso();
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
