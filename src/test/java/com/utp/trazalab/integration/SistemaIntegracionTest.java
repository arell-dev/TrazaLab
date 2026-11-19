package com.utp.trazalab.integration;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SistemaIntegracionTest extends BaseIntegracion {
    @Test
    void renderizaLosPanelesWebActivos() throws Exception {
        servicio.registrar(reportante.getId(), computadora.getCodigoQr(), "La pantalla no enciende.");

        mockMvc.perform(get("/incidencias"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Cerrar sesión")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Historial de reportes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("La pantalla no enciende.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/colores.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/css/componentes.css")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("/incidencias/exportar/"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("CSV"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("PDF"))));

        mockMvc.perform(get("/mantenimientos")
                        .with(user(tecnico.getCorreo()).authorities(new SimpleGrantedAuthority("ROLE_Técnico de Soporte"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registrar intervención")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Intervenciones recientes")));

        mockMvc.perform(get("/reportes"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Fallas recurrentes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Descargar CSV")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Descargar Excel")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Descargar PDF")));

        mockMvc.perform(get("/incidencias/exportar/excel"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    void permiteConsultarLaAplicacionYLaBaseDeDatosSinSesion() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));

        mockMvc.perform(get("/db-health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"));
    }

    @Test
    void informaElEstadoDeLaApiConConexionPostgresql() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"estado\":\"disponible\"}"));
    }

}
