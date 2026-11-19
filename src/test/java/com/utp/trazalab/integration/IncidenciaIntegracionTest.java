package com.utp.trazalab.integration;

import com.utp.trazalab.trazabilidad.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.trazabilidad.model.Incidencia;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IncidenciaIntegracionTest extends BaseIntegracion {
    @Test
    void registraYRecuperaLaIncidenciaEnPostgresql() {
        IncidenciaRegistradaDto creada = servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "No enciende.");

        List<Incidencia> guardadas = incidencias
                .findAllByComputadora_IdOrderByFechaRegistroAscIdAsc(computadora.getId(), PageRequest.of(0, 10))
                .getContent();
        assertEquals(1, guardadas.size());
        assertEquals(creada.id(), guardadas.getFirst().getId());
        assertEquals("No enciende.", guardadas.getFirst().getDescripcion());
        assertEquals("activo", guardadas.getFirst().getUsuario().getEstado());
        assertEquals("QR-TEST-001", guardadas.getFirst().getComputadora().getCodigoQr());
    }

    @Test
    void rechazaDescripcionVaciaSinGuardar() {
        assertThrows(IllegalArgumentException.class, () -> servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "  "));
        assertEquals(0, incidencias.count());
    }

    @Test
    void renderizaElFormularioWebSinTextoDeIdentidadDemostracion() throws Exception {
        mockMvc.perform(get("/incidencias/nueva"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar incidencia")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Revisa antes de guardar")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Incidencias recientes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verificar equipo")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Sin autenticación"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("identidad de demostración"))));
    }

    @Test
    void registraLaIncidenciaConUsuarioFijadoPorElServidor() throws Exception {
        mockMvc.perform(post("/incidencias")
                        .with(csrf())
                        .param("codigoQr", computadora.getCodigoQr())
                        .param("descripcion", "La pantalla no enciende.")
                        .param("usuarioId", "9999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/incidencias"));

        List<Incidencia> guardadas = incidencias.findAll();
        assertEquals(1, guardadas.size());
        assertEquals(reportante.getId(), guardadas.getFirst().getUsuario().getId());
    }

    @Test
    void mantieneElFormularioYValidaLosCamposObligatorios() throws Exception {
        mockMvc.perform(post("/incidencias")
                        .with(csrf())
                        .param("codigoQr", " ")
                        .param("descripcion", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ingresa el código QR del equipo.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("La descripción de la falla es obligatoria.")));
    }

    @Test
    void paginaIncidenciasSinDuplicadosConDesempatePorId() {
        guardarIncidencias(11);

        Page<IncidenciaRegistradaDto> primera = servicio.consultarIncidencias(PageRequest.of(0, 10));
        Page<IncidenciaRegistradaDto> ultima = servicio.consultarIncidencias(PageRequest.of(1, 10));
        Page<IncidenciaRegistradaDto> vacia = servicio.consultarIncidencias(PageRequest.of(2, 10));

        assertEquals(11, primera.getTotalElements());
        assertEquals(10, primera.getNumberOfElements());
        assertEquals(1, ultima.getNumberOfElements());
        assertTrue(ultima.isLast());
        assertTrue(vacia.isEmpty());
        List<Integer> idsPagina = Stream.concat(
                primera.getContent().stream(),
                ultima.getContent().stream())
                .map(IncidenciaRegistradaDto::id)
                .toList();

        assertEquals(11, new HashSet<>(idsPagina).size());
        assertTrue(primera.getContent().getFirst().id() > primera.getContent().getLast().id());
        assertTrue(primera.getContent().getLast().id() > ultima.getContent().getFirst().id());
    }

    @Test
    void limitaElTamanoSolicitadoParaMantenerConsultasAcotadas() {
        guardarIncidencias(11);

        Page<IncidenciaRegistradaDto> pagina = servicio.consultarIncidencias(PageRequest.of(0, 1_000));
        Page<IncidenciaRegistradaDto> paginaSinLimite = servicio.consultarIncidencias(Pageable.unpaged());

        assertEquals(100, pagina.getSize());
        assertEquals(11, pagina.getNumberOfElements());
        assertEquals(11, pagina.getTotalElements());
        assertEquals(100, paginaSinLimite.getSize());
        assertEquals(11, paginaSinLimite.getNumberOfElements());
    }

    @Test
    void mantieneElFormularioCuandoElCodigoQrNoExiste() throws Exception {
        mockMvc.perform(post("/incidencias")
                        .with(csrf())
                        .param("codigoQr", "QR-INEXISTENTE")
                        .param("descripcion", "La computadora no inicia."))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "No existe una computadora con ese código QR.")));

        assertEquals(0, incidencias.count());
    }

}
