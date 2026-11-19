package com.utp.trazalab.integration;

import com.utp.trazalab.app.DatosDemoRunner;
import com.utp.trazalab.trazabilidad.dto.EventoHistorialDto;
import com.utp.trazalab.trazabilidad.dto.HistorialComputadoraDto;
import com.utp.trazalab.trazabilidad.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import com.utp.trazalab.trazabilidad.model.Incidencia;
import com.utp.trazalab.trazabilidad.model.Sede;
import com.utp.trazalab.trazabilidad.model.Ubicacion;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EquipoIntegracionTest extends BaseIntegracion {
    @Test
    void cargaDeEquipoDeEjemploNoCreaUsuarios() {
        long usuariosAntes = usuarios.count();

        new DatosDemoRunner(sedes, ubicaciones, computadoras).run();

        assertEquals(usuariosAntes, usuarios.count());
        assertTrue(computadoras.findByCodigoQrIgnoreCase("PC-001").isPresent());
    }

    @Test
    void consultaEquipoYUltimasCincoIncidenciasPorCodigoQr() throws Exception {
        for (int numero = 1; numero <= 6; numero++) {
            servicio.registrar(
                    reportante.getId(),
                    computadora.getCodigoQr(),
                    "Incidencia de prueba " + numero + ".");
        }

        mockMvc.perform(get("/api/computadoras/historial")
                        .param("codigoQr", computadora.getCodigoQr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoQr").value("QR-TEST-001"))
                .andExpect(jsonPath("$.numeroSerie").value("SERIE-TEST-001"))
                .andExpect(jsonPath("$.marca").value("HP"))
                .andExpect(jsonPath("$.ubicacion").value("A0301 · Torre A, piso 3"))
                .andExpect(jsonPath("$.incidencias.length()").value(5))
                .andExpect(jsonPath("$.incidencias[0].descripcion")
                        .value("Incidencia de prueba 6."))
                .andExpect(jsonPath("$.incidencias[4].descripcion")
                        .value("Incidencia de prueba 2."))
                .andExpect(jsonPath("$.incidencias[0].reportante").doesNotExist());
    }

    @Test
    void responde404CuandoElQrDelEquipoNoExiste() throws Exception {
        mockMvc.perform(get("/api/computadoras/historial").param("codigoQr", "QR-INEXISTENTE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.detail").value("No existe una computadora con ese código QR."));
    }

    @Test
    void buscaElEquipoPorQrYConservaLaEntradaManualDelEscaner() throws Exception {
        mockMvc.perform(get("/equipos/escaneo"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-lector-iniciar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-lector-entrada")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/equipos/buscar")));

        mockMvc.perform(get("/equipos/buscar").param("codigoQr", " "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipos/escaneo"));

        mockMvc.perform(get("/equipos/buscar").param("codigoQr", computadora.getCodigoQr()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/equipos/QR-TEST-001"));
    }

    @Test
    void fichaDeEquipoMuestraElHistorialTecnicoYLaUbicacion() throws Exception {
        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "La fuente de poder no responde.");

        mockMvc.perform(get("/equipos/{codigoQr}", computadora.getCodigoQr()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Historial técnico")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("La fuente de poder no responde.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("UTP Sede Piura")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar mantenimiento")));
    }

    @Test
    void panelDeDisponibilidadMuestraElEquipoDeLaUbicacionSeleccionada() throws Exception {
        mockMvc.perform(get("/disponibilidad").param("completo", "true")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Disponibilidad de equipos")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(computadora.getCodigoQr())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A0301 · Torre A, piso 3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 equipo conserva una ubicación anterior")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("QR-LEGACY-001"))));
    }

    @Test
    void panelDeDisponibilidadOmiteAulasSinTorreOPisoYAdvierteQueRequierenValidacion() throws Exception {
        Ubicacion aulaIncompleta = ubicaciones.saveAndFlush(new Ubicacion(
                "A0901", null, sedePiura, null, null, "AULA"));
        computadoras.saveAndFlush(new Computadora(
                "QR-AULA-INCOMPLETA", "SERIE-AULA-INCOMPLETA", "Dell", "OptiPlex", aulaIncompleta, EstadoComputadora.OPERATIVA));

        mockMvc.perform(get("/disponibilidad").param("completo", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "2 equipos conservan ubicaciones anteriores y requieren validar su aula.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Aula 0901"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("QR-AULA-INCOMPLETA"))));
    }

    @Test
    void panelDeDisponibilidadSeleccionaLaPrimeraUbicacionDelCatalogo() throws Exception {
        assertEquals(List.of(
                        "A0301", "A0302", "A0303", "A0305", "A0307", "A0308", "A0310", "A0311", "A0312",
                        "B0605", "B0606", "B0607", "B0608"),
                ubicaciones.findAllBySede_NombreIgnoreCaseOrderByEdificioAscPisoAscNombreAsc(sedePiura.getNombre()).stream()
                        .filter(ubicacion -> "AULA".equals(ubicacion.getTipo()))
                        .map(Ubicacion::getNombre)
                        .toList());

        mockMvc.perform(get("/disponibilidad").param("completo", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"edificio\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"piso\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"ubicacionId\"")))
                .andExpect(model().attribute("edificioSeleccionado", "Torre A"))
                .andExpect(model().attribute("pisoSeleccionado", 3))
                .andExpect(model().attribute("ubicacionSeleccionada", org.hamcrest.Matchers.hasProperty("nombre", org.hamcrest.Matchers.is("A0301"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A0301")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("B0608")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("LAB-TEST</option>"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(computadora.getCodigoQr())));
    }

    @Test
    void resultadosDeDisponibilidadPaginanDiezEquipos() throws Exception {
        for (int numero = 0; numero < 10; numero++) {
            String codigo = "QR-PAGINA-" + String.format("%02d", numero);
            computadoras.saveAndFlush(new Computadora(codigo, "SERIE-" + codigo, "HP", "Mini", computadora.getUbicacion(), EstadoComputadora.OPERATIVA));
        }

        var primera = mockMvc.perform(get("/disponibilidad/resultados").param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk()).andReturn();
        var segunda = mockMvc.perform(get("/disponibilidad/resultados").param("ubicacionId", computadora.getUbicacion().getId().toString()).param("pagina", "1"))
                .andExpect(status().isOk()).andReturn();
        var diez = mockMvc.perform(get("/disponibilidad/resultados").param("ubicacionId", computadora.getUbicacion().getId().toString()).param("buscar", "QR-PAGINA"))
                .andExpect(status().isOk()).andReturn();
        Page<?> primeraPagina = (Page<?>) primera.getModelAndView().getModel().get("paginaEquipos");
        Page<?> segundaPagina = (Page<?>) segunda.getModelAndView().getModel().get("paginaEquipos");
        assertEquals(10, primeraPagina.getNumberOfElements());
        assertEquals(1, segundaPagina.getNumberOfElements());
        assertEquals(11, segundaPagina.getTotalElements());
        Page<?> paginaFiltrada = (Page<?>) diez.getModelAndView().getModel().get("paginaEquipos");
        assertEquals(10, paginaFiltrada.getNumberOfElements());
        assertEquals(1, paginaFiltrada.getTotalPages());
    }

    @Test
    void disponibilidadConservaPaginadorEnCadaLimite() throws Exception {
        int cantidadRegistrada = 0;
        for (int cantidad : new int[] {0, 1, 10, 11, 20, 21, 31}) {
            while (cantidadRegistrada < cantidad) {
                String codigo = "QR-LIMITE-" + String.format("%02d", cantidadRegistrada);
                computadoras.saveAndFlush(new Computadora(codigo, "SERIE-" + codigo, "HP", "Mini", computadora.getUbicacion(), EstadoComputadora.OPERATIVA));
                cantidadRegistrada++;
            }

            int paginas = (cantidad + 9) / 10;
            int ultimaPagina = Math.max(0, paginas - 1);
            var resultado = mockMvc.perform(get("/disponibilidad/resultados")
                            .param("ubicacionId", computadora.getUbicacion().getId().toString())
                            .param("buscar", "QR-LIMITE")
                            .param("pagina", Integer.toString(ultimaPagina)))
                    .andExpect(status().isOk())
                    .andReturn();
            Page<?> pagina = (Page<?>) resultado.getModelAndView().getModel().get("paginaEquipos");
            assertEquals(cantidad, pagina.getTotalElements());
            assertEquals(paginas, pagina.getTotalPages());
            assertEquals(cantidad == 0 ? 0 : cantidad - ultimaPagina * 10, pagina.getNumberOfElements());

            String contenido = resultado.getResponse().getContentAsString();
            org.junit.jupiter.api.Assertions.assertTrue(contenido.contains("aria-label=\"Paginación de equipos\""));
            if (cantidad == 0) {
                org.junit.jupiter.api.Assertions.assertTrue(contenido.contains("aria-disabled=\"true\">1</span>"));
            } else {
                org.junit.jupiter.api.Assertions.assertTrue(contenido.contains("aria-current=\"page\""));
            }
            if (paginas < 3) {
                org.junit.jupiter.api.Assertions.assertTrue(contenido.contains("aria-disabled=\"true\">3</span>"));
            }
        }
    }

    @Test
    void disponibilidadCombinaEstadosBusquedaYUbicacion() throws Exception {
        computadoras.saveAndFlush(new Computadora("QR-FILTRO-01", "SERIE-FILTRO-01", "HP", "Mini", computadora.getUbicacion(), EstadoComputadora.EN_MANTENIMIENTO));
        computadoras.saveAndFlush(new Computadora("QR-FILTRO-02", "SERIE-FILTRO-02", "HP", "Mini", computadora.getUbicacion(), EstadoComputadora.FUERA_DE_SERVICIO));
        computadoras.saveAndFlush(new Computadora("QR-FILTRO-03", "SERIE-FILTRO-03", "HP", "Mini", computadora.getUbicacion(), EstadoComputadora.OPERATIVA));

        var resultado = mockMvc.perform(get("/disponibilidad/resultados")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString())
                        .param("buscar", "QR-FILTRO")
                        .param("estado", "EN_MANTENIMIENTO", "FUERA_DE_SERVICIO"))
                .andExpect(status().isOk())
                .andReturn();
        Page<?> pagina = (Page<?>) resultado.getModelAndView().getModel().get("paginaEquipos");
        assertEquals(2, pagina.getTotalElements());
        mockMvc.perform(get("/disponibilidad/resultados")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString())
                        .param("estado", "ESTADO_INVALIDO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void resultadosDeDisponibilidadExigenRolAutorizado() throws Exception {
        mockMvc.perform(get("/disponibilidad/resultados").param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/login"));
        mockMvc.perform(get("/disponibilidad/resultados").with(user("docente").authorities(new SimpleGrantedAuthority("ROLE_Docente"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/disponibilidad/resultados").with(user("externo").authorities(new SimpleGrantedAuthority("ROLE_Externo"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aceptaElAulaCuandoCoincideConLaTorreYElPisoSeleccionados() throws Exception {
        mockMvc.perform(get("/disponibilidad").param("completo", "true")
                        .param("edificio", "Torre A")
                        .param("piso", "3")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A0301 · Torre A, piso 3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(computadora.getCodigoQr())));
    }

    @Test
    void rechazaElAulaCuandoNoCoincideConLaTorreSeleccionada() throws Exception {
        mockMvc.perform(get("/disponibilidad").param("completo", "true")
                        .param("edificio", "Torre B")
                        .param("piso", "6")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "La combinación de torre, piso y aula no está disponible.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(computadora.getCodigoQr()))));

        mockMvc.perform(get("/disponibilidad").param("completo", "true")
                        .param("edificio", "Torre A")
                        .param("piso", "6")
                        .param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "La combinación de torre, piso y aula no está disponible.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(computadora.getCodigoQr()))));
    }

    @Test
    void impideRegistrarDosVecesElMismoCodigoDeAulaEnUnaSede() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO ubicacion (nombre, detalle, sede_id, edificio, piso, tipo) VALUES (?, NULL, ?, ?, ?, 'AULA')",
                "A0301",
                sedePiura.getId(),
                "Torre A",
                3));
    }

    @Test
    void migraDesdeVersionCincoYReasignaSoloElEquipoDemoA0301() throws Exception {
        String esquema = "prueba_rf04_" + UUID.randomUUID().toString().replace("-", "");
        String prefijoEsquema = "\"" + esquema + "\".";

        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(esquema)
                    .defaultSchema(esquema)
                    .target(MigrationVersion.fromVersion("5"))
                    .load()
                    .migrate();

            try (Connection conexion = dataSource.getConnection()) {
                JdbcTemplate jdbcEsquema = new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
                jdbcEsquema.update(
                        "INSERT INTO " + prefijoEsquema + "computadora (codigo_qr, numero_serie, marca, modelo, ubicacion_id) "
                                + "SELECT ?, ?, ?, ?, ubicacion_id FROM " + prefijoEsquema + "ubicacion WHERE nombre = ?",
                        "PC-001",
                        "TRAZALAB-DEMO-001",
                        "HP",
                        "EliteDesk",
                        "LAB-01");
                jdbcEsquema.update(
                        "INSERT INTO " + prefijoEsquema + "computadora (codigo_qr, numero_serie, marca, modelo, ubicacion_id) "
                                + "SELECT ?, ?, ?, ?, ubicacion_id FROM " + prefijoEsquema + "ubicacion WHERE nombre = ?",
                        "PC-LEGACY-001",
                        "SERIE-LEGACY-001",
                        "Dell",
                        "OptiPlex",
                        "LAB-01");
            }

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(esquema)
                    .defaultSchema(esquema)
                    .load()
                    .migrate();

            try (Connection conexion = dataSource.getConnection()) {
                JdbcTemplate jdbcEsquema = new JdbcTemplate(new SingleConnectionDataSource(conexion, true));
                assertEquals("A0301", jdbcEsquema.queryForObject(
                        "SELECT ubicacion.nombre FROM " + prefijoEsquema + "computadora "
                                + "JOIN " + prefijoEsquema + "ubicacion USING (ubicacion_id) WHERE codigo_qr = ?",
                        String.class,
                        "PC-001"));
                assertEquals("LAB-01", jdbcEsquema.queryForObject(
                        "SELECT ubicacion.nombre FROM " + prefijoEsquema + "computadora "
                                + "JOIN " + prefijoEsquema + "ubicacion USING (ubicacion_id) WHERE codigo_qr = ?",
                        String.class,
                        "PC-LEGACY-001"));
            }
        } finally {
            try (Connection conexion = dataSource.getConnection(); Statement sentencia = conexion.createStatement()) {
                sentencia.execute("DROP SCHEMA IF EXISTS \"" + esquema + "\" CASCADE");
            }
        }
    }

    @Test
    void rechazaComoFiltroLasUbicacionesAnterioresAlCatalogoDeAulas() throws Exception {
        Integer ubicacionAnteriorId = ubicaciones.findByNombreIgnoreCaseAndSede_NombreIgnoreCase("LAB-TEST", sedePiura.getNombre())
                .orElseThrow()
                .getId();

        mockMvc.perform(get("/disponibilidad").param("completo", "true").param("ubicacionId", ubicacionAnteriorId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "La combinación de torre, piso y aula no está disponible.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("QR-LEGACY-001"))));
    }

    @Test
    void panelDeDisponibilidadSeparaLasAulasPorTorre() throws Exception {
        Ubicacion aulaTorreB = ubicaciones.findByNombreIgnoreCaseAndSede_NombreIgnoreCase("B0605", sedePiura.getNombre()).orElseThrow();
        Computadora equipoTorreB = computadoras.saveAndFlush(new Computadora(
                "QR-TORRE-B-001", "SERIE-TORRE-B-001", "Lenovo", "ThinkCentre", aulaTorreB, EstadoComputadora.OPERATIVA));

        mockMvc.perform(get("/disponibilidad").param("completo", "true").param("ubicacionId", computadora.getUbicacion().getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(computadora.getCodigoQr())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(equipoTorreB.getCodigoQr()))));

        mockMvc.perform(get("/disponibilidad").param("completo", "true").param("ubicacionId", aulaTorreB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(equipoTorreB.getCodigoQr())))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(computadora.getCodigoQr()))));
    }

    @Test
    void limitaElTamanoDePaginaDelHistorialTecnico() {
        Page<EventoHistorialDto> paginaNegativa = servicioHistorialTecnico.consultar(
                computadora.getCodigoQr(), -2, 500);
        Page<EventoHistorialDto> paginaMinima = servicioHistorialTecnico.consultar(
                computadora.getCodigoQr(), 0, 0);

        assertEquals(0, paginaNegativa.getNumber());
        assertEquals(50, paginaNegativa.getSize());
        assertEquals(1, paginaMinima.getSize());
    }

    @Test
    void paginaElHistorialDeLaComputadoraEnOrdenAscendente() {
        guardarIncidencias(11);

        HistorialComputadoraDto primera = servicio.consultarHistorialComputadora(
                computadora.getCodigoQr(), PageRequest.of(0, 10));
        HistorialComputadoraDto ultima = servicio.consultarHistorialComputadora(
                computadora.getCodigoQr(), PageRequest.of(1, 10));

        assertEquals(11, primera.incidencias().getTotalElements());
        assertEquals(10, primera.incidencias().getNumberOfElements());
        assertEquals(1, ultima.incidencias().getNumberOfElements());
        assertTrue(primera.incidencias().getContent().getFirst().id()
                < primera.incidencias().getContent().getLast().id());
        assertTrue(primera.incidencias().getContent().getLast().id()
                < ultima.incidencias().getContent().getFirst().id());
    }

    @Test
    void recorrePorPaginasUnHistorialDeCincoMilIncidencias() {
        int cantidad = 5_000;
        guardarIncidenciasJdbc(cantidad);
        int numeroPagina = 0;
        long registrosLeidos = 0;
        Integer idAnterior = null;
        Page<IncidenciaRegistradaDto> pagina;

        do {
            pagina = servicio.consultarIncidencias(PageRequest.of(numeroPagina, 100));
            assertEquals(cantidad, pagina.getTotalElements());
            for (IncidenciaRegistradaDto incidencia : pagina.getContent()) {
                if (idAnterior != null) {
                    assertTrue(idAnterior > incidencia.id());
                }
                idAnterior = incidencia.id();
                registrosLeidos++;
            }
            numeroPagina++;
        } while (pagina.hasNext());

        assertEquals(cantidad, registrosLeidos);
        assertEquals(50, numeroPagina);
    }

    @Test
    void consolidaIncidenciaFallaMantenimientoYComponenteConPaginacion() {
        Integer tipoFallaId = tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc().getFirst().getId();
        IncidenciaRegistradaDto incidencia = servicio.registrar(
                reportante.getId(), computadora.getCodigoQr(), "Equipo reinicia sin aviso.", tipoFallaId);
        Integer fallaId = jdbcTemplate.queryForObject(
                "SELECT falla_id FROM falla WHERE incidencia_id = ?", Integer.class, incidencia.id());

        servicioMantenimiento.registrar(
                tecnico.getId(),
                computadora.getCodigoQr(),
                "correctivo",
                fallaId,
                "Se reemplazó el módulo de memoria.",
                "instalado",
                null,
                "Memoria 16 GB",
                "RAM",
                "RAM-HIST-001");

        Page<EventoHistorialDto> primeraPagina = servicioHistorialTecnico.consultar(
                computadora.getCodigoQr(), 0, 2);
        Page<EventoHistorialDto> segundaPagina = servicioHistorialTecnico.consultar(
                computadora.getCodigoQr(), 1, 2);
        List<EventoHistorialDto> eventos = Stream.concat(
                        primeraPagina.getContent().stream(), segundaPagina.getContent().stream())
                .toList();
        HashSet<String> clavesEvento = new HashSet<>();
        HashSet<String> tiposEvento = new HashSet<>();
        for (EventoHistorialDto evento : eventos) {
            clavesEvento.add(evento.tipo() + ":" + evento.id());
            tiposEvento.add(evento.tipo());
        }

        assertEquals(4, primeraPagina.getTotalElements());
        assertEquals(4, segundaPagina.getTotalElements());
        assertEquals(2, primeraPagina.getContent().size());
        assertEquals(2, segundaPagina.getContent().size());
        assertEquals(4, clavesEvento.size());
        assertTrue(tiposEvento.containsAll(List.of("INCIDENCIA", "FALLA", "MANTENIMIENTO", "COMPONENTE")));
        assertTrue(eventos.stream().anyMatch(evento -> evento.descripcion().contains("RAM-HIST-001")));
    }

}
