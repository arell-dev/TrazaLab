package com.utp.trazalab.integration;

import com.utp.trazalab.usuarios.controller.form.UsuarioForm;
import com.utp.trazalab.usuarios.model.Rol;
import com.utp.trazalab.usuarios.model.Usuario;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UsuarioIntegracionTest extends BaseIntegracion {
    @Test
    @WithMockUser(username = "admin-test@trazalab.local", authorities = "ROLE_Administrador")
    void paginaUsuariosConOrdenEstableYDatosDeRol() throws Exception {
        guardarUsuariosDePrueba(25);

        var primeraRespuesta = mockMvc.perform(get("/usuarios"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Paginación de usuarios")))
                .andReturn();
        var segundaRespuesta = mockMvc.perform(get("/usuarios").param("pagina", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Página 2 de 2")))
                .andReturn();

        @SuppressWarnings("unchecked")
        Page<Usuario> primera = (Page<Usuario>) primeraRespuesta.getModelAndView().getModel().get("paginaUsuarios");
        @SuppressWarnings("unchecked")
        Page<Usuario> segunda = (Page<Usuario>) segundaRespuesta.getModelAndView().getModel().get("paginaUsuarios");
        assertEquals(20, primera.getNumberOfElements());
        assertEquals(8, segunda.getNumberOfElements());
        assertEquals(28, primera.getTotalElements());
        assertTrue(primera.getContent().get(19).getId() < segunda.getContent().get(0).getId());
        assertTrue(segunda.getContent().stream().allMatch(usuario -> usuario.getRol().getNombre() != null));
    }

    @Test
    @WithMockUser(username = "admin-test@trazalab.local", authorities = "ROLE_Administrador")
    void paginasInvalidasMuestranLaPrimeraPagina() throws Exception {
        for (String pagina : List.of("-1", "texto", "2147483647", "9999999999999999999", "5")) {
            var respuesta = mockMvc.perform(get("/usuarios").param("pagina", pagina))
                    .andExpect(status().isOk())
                    .andReturn();
            Page<?> resultado = (Page<?>) respuesta.getModelAndView().getModel().get("paginaUsuarios");
            assertEquals(0, resultado.getNumber());
        }
    }

    @Test
    @WithMockUser(username = "admin-test@trazalab.local", authorities = "ROLE_Administrador")
    void conservaPaginaYRolesCuandoFallaElFormulario() throws Exception {
        guardarUsuariosDePrueba(25);

        var respuesta = mockMvc.perform(post("/usuarios").with(csrf()).param("pagina", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Página 2 de 2")))
                .andReturn();

        Page<?> resultado = (Page<?>) respuesta.getModelAndView().getModel().get("paginaUsuarios");
        assertEquals(1, resultado.getNumber());
        assertFalse(((List<?>) respuesta.getModelAndView().getModel().get("roles")).isEmpty());
    }

    @Test
    @WithMockUser(username = "admin-test@trazalab.local", authorities = "ROLE_Administrador")
    void conservaPaginaAlCambiarElEstadoDeUnaCuenta() throws Exception {
        guardarUsuariosDePrueba(25);

        mockMvc.perform(post("/usuarios/{usuarioId}/estado", tecnico.getId()).with(csrf()).param("pagina", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios?pagina=1"));

        assertEquals("inactivo", usuarios.findById(tecnico.getId()).orElseThrow().getEstado());
    }

    private void guardarUsuariosDePrueba(int cantidad) {
        Rol rol = roles.findByNombreIgnoreCase("Docente").orElseThrow();
        List<Usuario> cuentas = new ArrayList<>();
        for (int numero = 0; numero < cantidad; numero++) {
            cuentas.add(new Usuario(rol, "Cuenta", "Prueba", "paginacion" + numero + "@prueba.local", "activo"));
        }
        usuarios.saveAllAndFlush(cuentas);
    }

    @Test
    @WithMockUser(username = "docente@trazalab.local", authorities = "ROLE_Docente")
    void docenteNoPuedeConsultarReportesNiHistorialTecnico() throws Exception {
        mockMvc.perform(get("/reportes"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/computadoras/historial")
                        .param("codigoQr", "QR-TEST-001"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void usuarioAnonimoDebeIniciarSesionParaConsultarIncidencias() throws Exception {
        mockMvc.perform(get("/incidencias"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/login"));
    }

    @Test
    @WithAnonymousUser
    void loginDeUsuariosMuestraLaMarcaInstitucional() throws Exception {
        mockMvc.perform(get("/usuarios/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/images/utp-labtrace.svg")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"/usuarios/login\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("rel=\"icon\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Trazabilidad técnica para los laboratorios de la UTP.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Iniciar sesión</button>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("autofocus"))));
    }

    @Test
    @WithAnonymousUser
    void muestraElErrorDeAutenticacionEnElFormularioDeAcceso() throws Exception {
        mockMvc.perform(get("/usuarios/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Correo o contraseña incorrectos, o cuenta inactiva.")));
    }

    @Test
    @WithAnonymousUser
    void loginCorrectoRedirigeAlPanelDeDisponibilidad() throws Exception {
        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .param("username", "admin-test@trazalab.local")
                        .param("password", "clave-segura-de-pruebas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/disponibilidad"));
    }

    @Test
    @WithAnonymousUser
    void rechazaContrasenaIncorrectaYCuentaInactiva() throws Exception {
        Rol rolDocente = roles.findByNombreIgnoreCase("Docente").orElseThrow();
        usuarios.saveAndFlush(new Usuario(
                rolDocente,
                "Cuenta",
                "Inactiva",
                "cuenta.inactiva@trazalab.local",
                "inactivo",
                codificadorContrasenas.encode("contrasena-valida-123")));

        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .param("username", "admin-test@trazalab.local")
                        .param("password", "incorrecta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/login?error"));

        mockMvc.perform(post("/usuarios/login")
                        .with(csrf())
                        .param("username", "cuenta.inactiva@trazalab.local")
                        .param("password", "contrasena-valida-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/login?error"));
    }

    @Test
    void limitaLaAdministracionDeUsuariosAlRolAdministradorYExigeCsrf() throws Exception {
        mockMvc.perform(get("/usuarios")
                        .with(user("tecnico@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Técnico de Soporte"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/usuarios")
                        .with(user("admin-test@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Administrador"))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Administración de usuarios")));

        mockMvc.perform(post("/usuarios")
                        .with(user("admin-test@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Administrador")))
                        .param("nombres", "Nueva")
                        .param("apellidos", "Cuenta")
                        .param("correo", "nueva.cuenta@trazalab.local")
                        .param("contrasena", "clave-de-prueba-123")
                        .param("rolId", roles.findByNombreIgnoreCase("Docente").orElseThrow().getId().toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/usuarios")
                        .with(user("tecnico@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Técnico de Soporte")))
                        .with(csrf())
                        .param("nombres", "Nueva")
                        .param("apellidos", "Cuenta")
                        .param("correo", "nueva.cuenta@trazalab.local")
                        .param("contrasena", "clave-de-prueba-123")
                        .param("rolId", roles.findByNombreIgnoreCase("Docente").orElseThrow().getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void formularioAdministradorCreaUnaCuentaConCredencialCifrada() throws Exception {
        Integer rolDocenteId = roles.findByNombreIgnoreCase("Docente").orElseThrow().getId();

        mockMvc.perform(post("/usuarios")
                        .with(user("admin-test@trazalab.local")
                                .authorities(new SimpleGrantedAuthority("ROLE_Administrador")))
                        .with(csrf())
                        .param("nombres", "Elena")
                        .param("apellidos", "Paredes")
                        .param("correo", "elena.paredes@trazalab.local")
                        .param("contrasena", "clave-de-prueba-123")
                        .param("rolId", rolDocenteId.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios"));

        Usuario creada = usuarios.findByCorreoIgnoreCase("elena.paredes@trazalab.local").orElseThrow();
        assertEquals("activo", creada.getEstado());
        assertTrue(codificadorContrasenas.matches("clave-de-prueba-123", creada.getPasswordHash()));
    }

    @Test
    void creaCuentasConContrasenaBcryptYRechazaCorreoDuplicado() {
        Rol rolDocente = roles.findByNombreIgnoreCase("Docente").orElseThrow();
        UsuarioForm formulario = new UsuarioForm();
        formulario.setNombres("  Carla ");
        formulario.setApellidos(" Ríos ");
        formulario.setCorreo(" Carla.Rios@trazalab.local ");
        formulario.setContrasena("clave-de-prueba-123");
        formulario.setRolId(rolDocente.getId());

        servicioUsuarios.crear(formulario);

        Usuario creada = usuarios.findByCorreoIgnoreCase("carla.rios@trazalab.local").orElseThrow();
        assertEquals("Carla", creada.getNombres());
        assertEquals("Ríos", creada.getApellidos());
        assertEquals("activo", creada.getEstado());
        assertNotEquals("clave-de-prueba-123", creada.getPasswordHash());
        assertTrue(codificadorContrasenas.matches("clave-de-prueba-123", creada.getPasswordHash()));

        formulario.setCorreo("CARLA.RIOS@TRAZALAB.LOCAL");
        assertThrows(IllegalArgumentException.class, () -> servicioUsuarios.crear(formulario));
    }

    @Test
    void evitaContrasenasQueSuperanElLimiteUtf8YDesactivarAlUltimoAdministrador() {
        Rol rolDocente = roles.findByNombreIgnoreCase("Docente").orElseThrow();
        UsuarioForm formulario = new UsuarioForm();
        formulario.setNombres("Cuenta");
        formulario.setApellidos("Con límite");
        formulario.setCorreo("limite.contrasena@trazalab.local");
        formulario.setContrasena("é".repeat(37));
        formulario.setRolId(rolDocente.getId());
        assertThrows(IllegalArgumentException.class, () -> servicioUsuarios.crear(formulario));

        Usuario administrador = usuarios.findByCorreoIgnoreCase("admin-test@trazalab.local").orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> servicioUsuarios.cambiarEstado(
                administrador.getId(), "otro-administrador@trazalab.local"));
        assertThrows(IllegalArgumentException.class, () -> servicioUsuarios.cambiarEstado(
                administrador.getId(), administrador.getCorreo()));
        assertFalse("inactivo".equalsIgnoreCase(administrador.getEstado()));
    }

    @Test
    void cerrarSesionRegresaAlFormularioDeAcceso() throws Exception {
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios/login?salio"));
    }

}
