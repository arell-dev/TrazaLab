package com.utp.trazalab.trazabilidad.service;

import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.model.Ubicacion;
import com.utp.trazalab.trazabilidad.repository.ComputadoraRepository;
import com.utp.trazalab.trazabilidad.repository.UbicacionRepository;
import java.util.ArrayList;
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
import org.springframework.stereotype.Service;

@Service
public class DisponibilidadService {
    private static final int TAMANO_PAGINA = 10;

    private final ComputadoraRepository computadoras;
    private final UbicacionRepository ubicaciones;
    private final String sedeActiva;

    public DisponibilidadService(ComputadoraRepository computadoras, UbicacionRepository ubicaciones, @Value("${trazalab.sede-activa}") String sedeActiva) {
        this.computadoras = computadoras;
        this.ubicaciones = ubicaciones;
        this.sedeActiva = sedeActiva;
    }

    public Map<String, Object> consultar(Integer ubicacionId, String edificio, Integer piso, String buscar, List<EstadoComputadora> estado, int pagina, boolean consultarResultados) {
        return consultar(ubicacionId, edificio, piso, buscar, estado, pagina, consultarResultados, false);
    }

    public Map<String, Object> consultarFragmento(Integer ubicacionId, String edificio, Integer piso, String buscar, List<EstadoComputadora> estado, int pagina) {
        return consultar(ubicacionId, edificio, piso, buscar, estado, pagina, true, true);
    }

    private Map<String, Object> consultar(Integer ubicacionId, String edificio, Integer piso, String buscar, List<EstadoComputadora> estado, int pagina, boolean consultarResultados, boolean fragmento) {
        Map<String, Object> datos = new HashMap<>();
        Ubicacion aulaFragmento = fragmento ? buscarAulaFragmento(ubicacionId, edificio, piso) : null;
        List<Ubicacion> todasLasUbicaciones = aulaFragmento == null
                ? ubicaciones.findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc(sedeActiva)
                : List.of(aulaFragmento);
        Set<Integer> idsUbicacionesConEquipos = aulaFragmento != null || todasLasUbicaciones.isEmpty()
                ? Set.of()
                : new HashSet<>(computadoras.findIdsDeUbicacionConEquipos(todasLasUbicaciones.getFirst().getSede().getId()));
        List<Ubicacion> aulasDisponibles = todasLasUbicaciones.stream()
                .filter(ubicacion -> "AULA".equals(ubicacion.getTipo()))
                .filter(aula -> aula.getEdificio() != null && aula.getPiso() != null)
                .toList();
        Ubicacion ubicacionSeleccionada = seleccionarUbicacion(ubicacionId, aulasDisponibles);
        Map<String, List<Integer>> pisosPorTorre = obtenerPisosPorTorre(aulasDisponibles);
        String edificioSeleccionado = seleccionarEdificio(edificio, pisosPorTorre);
        if (edificio == null && ubicacionId == null && !pisosPorTorre.isEmpty()) {
            edificioSeleccionado = pisosPorTorre.keySet().iterator().next();
        }
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
                .map(Ubicacion::getId)
                .filter(idsUbicacionesConEquipos::contains)
                .toList();
        long equiposConUbicacionPorVerificar = idsUbicacionesPorVerificar.isEmpty()
                ? 0
                : computadoras.countByUbicacion_IdIn(idsUbicacionesPorVerificar);
        Map<Integer, String> etiquetasEquipos = new HashMap<>();
        List<Integer> idsUbicacion = ubicacionSeleccionada == null ? List.of() : List.of(ubicacionSeleccionada.getId());
        String busqueda = buscar == null || buscar.isBlank() ? null : buscar.strip();
        List<EstadoComputadora> estadosSeleccionados = estado == null ? List.of() : estado.stream().distinct().toList();
        List<EstadoComputadora> estadosConsulta = estadosSeleccionados.isEmpty() ? List.of(EstadoComputadora.values()) : estadosSeleccionados;
        Page<Computadora> paginaEquipos = Page.empty();
        long totalEquipos = 0;
        long equiposOperativos = 0;
        long equiposEnMantenimiento = 0;
        long equiposFueraDeServicio = 0;

        if (consultarResultados && !idsUbicacion.isEmpty()) {
            Map<EstadoComputadora, Long> cantidades = new java.util.EnumMap<>(EstadoComputadora.class);
            for (Object[] fila : computadoras.contarPorEstado(idsUbicacion)) {
                cantidades.put((EstadoComputadora) fila[0], (Long) fila[1]);
            }
            equiposOperativos = cantidades.getOrDefault(EstadoComputadora.OPERATIVA, 0L);
            equiposEnMantenimiento = cantidades.getOrDefault(EstadoComputadora.EN_MANTENIMIENTO, 0L);
            equiposFueraDeServicio = cantidades.getOrDefault(EstadoComputadora.FUERA_DE_SERVICIO, 0L);
            totalEquipos = cantidades.values().stream().mapToLong(Long::longValue).sum();

            Pageable pageable = PageRequest.of(Math.max(0, pagina), TAMANO_PAGINA, ordenPorCodigoQr());
            paginaEquipos = computadoras.filtrarPorUbicaciones(idsUbicacion, busqueda == null ? "" : busqueda, estadosConsulta, pageable);

            if (!paginaEquipos.hasContent() && paginaEquipos.getTotalPages() > 0) {
                pageable = PageRequest.of(paginaEquipos.getTotalPages() - 1, TAMANO_PAGINA, ordenPorCodigoQr());
                paginaEquipos = computadoras.filtrarPorUbicaciones(idsUbicacion, busqueda == null ? "" : busqueda, estadosConsulta, pageable);
            }

            for (Computadora computadora : paginaEquipos.getContent()) {
                etiquetasEquipos.put(computadora.getId(), etiquetaUbicacion(computadora.getUbicacion()));
            }
        }

        long inicioResultados = paginaEquipos.hasContent() ? (long) paginaEquipos.getNumber() * TAMANO_PAGINA + 1 : 0;
        long finResultados = paginaEquipos.hasContent() ? inicioResultados + paginaEquipos.getNumberOfElements() - 1 : 0;

        datos.put("aulasDisponibles", aulasDisponibles);
        datos.put("pisosPorTorre", pisosPorTorre);
        datos.put("edificioSeleccionado", edificioSeleccionado);
        datos.put("pisoSeleccionado", pisoSeleccionado);
        datos.put("ubicacionSeleccionada", ubicacionSeleccionada);
        datos.put("etiquetaUbicacionSeleccionada", etiquetaUbicacion(ubicacionSeleccionada));
        datos.put("ubicacionNoDisponible", ubicacionNoDisponible);
        datos.put("equiposConUbicacionPorVerificar", equiposConUbicacionPorVerificar);
        datos.put("etiquetasEquipos", etiquetasEquipos);
        datos.put("paginaEquipos", paginaEquipos);
        datos.put("busqueda", busqueda == null ? "" : busqueda);
        datos.put("estadosSeleccionados", estadosSeleccionados);
        datos.put("totalEquipos", totalEquipos);
        datos.put("equiposOperativos", equiposOperativos);
        datos.put("equiposEnMantenimiento", equiposEnMantenimiento);
        datos.put("equiposFueraDeServicio", equiposFueraDeServicio);
        datos.put("porcentajeOperativos", porcentaje(equiposOperativos, totalEquipos));
        datos.put("porcentajeEnMantenimiento", porcentaje(equiposEnMantenimiento, totalEquipos));
        datos.put("porcentajeFueraDeServicio", porcentaje(equiposFueraDeServicio, totalEquipos));
        datos.put("inicioResultados", inicioResultados);
        datos.put("finResultados", finResultados);
        datos.put("paginasVisibles", paginasVisibles(paginaEquipos.getNumber(), paginaEquipos.getTotalPages()));
        return datos;
    }

    private Ubicacion buscarAulaFragmento(Integer ubicacionId, String edificio, Integer piso) {
        if (ubicacionId == null || (piso != null && edificio == null)) {
            return null;
        }

        return ubicaciones.findByIdAndSede_NombreIgnoreCase(ubicacionId, sedeActiva)
                .filter(aula -> "AULA".equals(aula.getTipo()) && aula.getEdificio() != null && aula.getPiso() != null)
                .filter(aula -> edificio == null || (!edificio.isBlank() && aula.getEdificio().equalsIgnoreCase(edificio.strip())))
                .filter(aula -> piso == null || piso.equals(aula.getPiso()))
                .orElse(null);
    }

    private Ubicacion seleccionarUbicacion(Integer ubicacionId, List<Ubicacion> aulasDisponibles) {
        if (ubicacionId == null) {
            return null;
        }

        return aulasDisponibles.stream()
                .filter(aula -> aula.getId().equals(ubicacionId))
                .findFirst()
                .orElse(null);
    }

    private Map<String, List<Integer>> obtenerPisosPorTorre(List<Ubicacion> aulasDisponibles) {
        Map<String, Set<Integer>> pisosPorTorre = new LinkedHashMap<>();

        for (Ubicacion aula : aulasDisponibles) {
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

    public static String etiquetaUbicacion(Ubicacion ubicacion) {
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
        return Sort.by(Computadora::getCodigoQr).ascending();
    }

    private List<Integer> paginasVisibles(int paginaActual, int totalPaginas) {
        if (totalPaginas <= 5) {
            return java.util.stream.IntStream.range(0, Math.max(3, totalPaginas)).boxed().toList();
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
