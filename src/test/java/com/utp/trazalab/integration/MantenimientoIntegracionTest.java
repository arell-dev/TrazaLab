package com.utp.trazalab.integration;

import com.utp.trazalab.trazabilidad.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.trazabilidad.model.Componente;
import com.utp.trazalab.trazabilidad.model.Computadora;
import com.utp.trazalab.trazabilidad.model.EstadoComputadora;
import com.utp.trazalab.trazabilidad.model.Ubicacion;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MantenimientoIntegracionTest extends BaseIntegracion {
    @Test
    void registraMantenimientoSinCambiarEstadoNiCerrarIncidencia() throws Exception {
        IncidenciaRegistradaDto incidencia = servicio.registrar(
                reportante.getId(), computadora.getCodigoQr(), "Pantalla intermitente.");

        servicioMantenimiento.registrar(
                tecnico.getId(),
                computadora.getCodigoQr(),
                "preventivo",
                null,
                "Se revisó el cable de video.",
                null,
                null,
                null,
                null,
                null);

        assertEquals("operativa", jdbcTemplate.queryForObject(
                "SELECT estado FROM computadora WHERE computadora_id = ?", String.class, computadora.getId()));
        assertEquals("registrada", jdbcTemplate.queryForObject(
                "SELECT estado FROM incidencia WHERE incidencia_id = ?", String.class, incidencia.id()));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mantenimiento", Integer.class));
        mockMvc.perform(get("/mantenimientos")
                        .with(user(tecnico.getCorreo()).authorities(new SimpleGrantedAuthority("ROLE_Técnico de Soporte"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Se revisó el cable de video.")));
    }

    @Test
    void instalarYReemplazarComponenteMantieneHistorialDeAsignaciones() {
        servicioMantenimiento.registrar(
                tecnico.getId(), computadora.getCodigoQr(), "preventivo", null, "Instalación inicial.",
                "instalado", null, "Memoria 8 GB", "RAM", "RAM-OLD");
        Componente anterior = componentes.findByNumeroSerieIgnoreCase("RAM-OLD").orElseThrow();

        servicioMantenimiento.registrar(
                tecnico.getId(), computadora.getCodigoQr(), "preventivo", null, "Se reemplazó la memoria.",
                "reemplazado", "RAM-OLD", "Memoria 16 GB", "RAM", "RAM-NEW");

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM computadora_componente "
                        + "WHERE computadora_id = ? AND componente_id = ? AND fecha_retiro IS NOT NULL",
                Integer.class,
                computadora.getId(),
                anterior.getId()));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM computadora_componente cc "
                        + "JOIN componente c ON c.componente_id = cc.componente_id "
                        + "WHERE cc.computadora_id = ? AND c.numero_serie = 'RAM-NEW' AND cc.fecha_retiro IS NULL",
                Integer.class,
                computadora.getId()));
        assertEquals("operativa", jdbcTemplate.queryForObject(
                "SELECT estado FROM computadora WHERE computadora_id = ?", String.class, computadora.getId()));
        assertEquals(3, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mantenimiento_componente", Integer.class));
    }

    @Test
    void mantenimientoCorrectivoExigeFallaDelMismoEquipo() {
        Integer tipoFallaId = tiposFalla.findAllByOrderByCategoria_NombreAscNombreAsc().getFirst().getId();
        IncidenciaRegistradaDto incidencia = servicio.registrar(
                reportante.getId(), computadora.getCodigoQr(), "Falla para correctivo.", tipoFallaId);
        Integer fallaId = jdbcTemplate.queryForObject(
                "SELECT falla_id FROM falla WHERE incidencia_id = ?", Integer.class, incidencia.id());

        Ubicacion otraUbicacion = ubicaciones.saveAndFlush(new Ubicacion("LAB-OTRO", "Equipo separado", sedePiura, "Torre A", 4, "LABORATORIO"));
        Computadora otroEquipo = computadoras.saveAndFlush(new Computadora(
                "QR-TEST-002", "SERIE-TEST-002", "Dell", "OptiPlex", otraUbicacion, EstadoComputadora.OPERATIVA));

        assertThrows(IllegalArgumentException.class, () -> servicioMantenimiento.registrar(
                tecnico.getId(), otroEquipo.getCodigoQr(), "correctivo", fallaId, "Correctivo cruzado.",
                null, null, null, null, null));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mantenimiento", Integer.class));

        servicioMantenimiento.registrar(
                tecnico.getId(), computadora.getCodigoQr(), "correctivo", fallaId, "Correctivo asociado.",
                null, null, null, null, null);
        assertEquals(fallaId, jdbcTemplate.queryForObject(
                "SELECT falla_id FROM mantenimiento", Integer.class));
    }

}
