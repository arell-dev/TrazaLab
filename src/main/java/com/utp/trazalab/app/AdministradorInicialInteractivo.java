package com.utp.trazalab.app;

import com.utp.trazalab.dto.UsuarioAltaFormulario;
import com.utp.trazalab.persistence.jpa.entity.RolEntidad;
import com.utp.trazalab.persistence.jpa.entity.UsuarioEntidad;
import com.utp.trazalab.persistence.jpa.springdata.RolJpaRepositorio;
import com.utp.trazalab.persistence.jpa.springdata.UsuarioJpaRepositorio;
import jakarta.validation.Validator;
import java.io.Console;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@Profile("crear-administrador")
public class AdministradorInicialInteractivo implements CommandLineRunner {
    private final UsuarioJpaRepositorio usuarios;
    private final RolJpaRepositorio roles;
    private final PasswordEncoder codificador;
    private final Validator validador;

    public AdministradorInicialInteractivo(UsuarioJpaRepositorio usuarios, RolJpaRepositorio roles, PasswordEncoder codificador, Validator validador) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.codificador = codificador;
        this.validador = validador;
    }

    @Override
    public void run(String... args) {
        Console consola = System.console();
        if (consola == null) {
            throw new IllegalStateException("Ejecuta el alta inicial desde una consola interactiva.");
        }

        RolEntidad rolAdministrador = roles.findByNombreIgnoreCase("Administrador")
                .orElseThrow(() -> new IllegalStateException("No existe el rol Administrador en la base de datos."));
        List<UsuarioEntidad> administradores = usuarios.findAllByRol_NombreIgnoreCase(rolAdministrador.getNombre());
        if (administradores.size() > 1) {
            throw new IllegalStateException("Hay varias cuentas administradoras; revisa la base de datos antes del alta inicial.");
        }
        UsuarioEntidad administrador = administradores.stream().findFirst().orElse(null);
        if (administrador != null && administrador.getPasswordHash() != null
                && !administrador.getPasswordHash().isBlank()) {
            throw new IllegalStateException("Ya existe una cuenta administradora con contraseña en la base de datos.");
        }

        String nombres;
        String apellidos;
        String correo;
        if (administrador == null) {
            nombres = leerCampo(consola, "Nombres del administrador: ");
            apellidos = leerCampo(consola, "Apellidos del administrador: ");
            correo = leerCampo(consola, "Correo del administrador: ");
        } else {
            nombres = administrador.getNombres();
            apellidos = administrador.getApellidos();
            correo = administrador.getCorreo();
        }
        char[] contrasena = consola.readPassword("Contraseña (mínimo 12 caracteres): ");
        char[] confirmacion = null;

        try {
            if (contrasena == null) {
                throw new IllegalStateException("No se recibió la contraseña.");
            }
            confirmacion = consola.readPassword("Confirma la contrasena: ");
            if (confirmacion == null) {
                throw new IllegalStateException("No se recibió la confirmación de la contraseña.");
            }
            validarDatos(nombres, apellidos, correo, contrasena, confirmacion);
            if (administrador == null) {
                administrador = new UsuarioEntidad(rolAdministrador, nombres, apellidos, correo, "activo");
            }
            administrador.setPasswordHash(codificador.encode(CharBuffer.wrap(contrasena)));
            administrador.setEstado("activo");
            usuarios.saveAndFlush(administrador);
            consola.printf("Cuenta administradora disponible en PostgreSQL para %s.%n", correo);
        } finally {
            if (contrasena != null) {
                Arrays.fill(contrasena, '\0');
            }
            if (confirmacion != null) {
                Arrays.fill(confirmacion, '\0');
            }
        }
    }

    private String leerCampo(Console consola, String indicacion) {
        String valor = consola.readLine(indicacion);
        if (valor == null) {
            throw new IllegalStateException("No se recibieron los datos de la cuenta.");
        }
        return valor.strip();
    }

    private void validarDatos(String nombres, String apellidos, String correo, char[] contrasena, char[] confirmacion) {
        if (nombres.isBlank() || nombres.length() > 100 || apellidos.isBlank() || apellidos.length() > 120) {
            throw new IllegalArgumentException("Ingresa nombres y apellidos dentro de los límites permitidos.");
        }
        if (!validador.validateValue(UsuarioAltaFormulario.class, "correo", correo).isEmpty()) {
            throw new IllegalArgumentException("Ingresa un correo válido de hasta 150 caracteres.");
        }
        if (!Arrays.equals(contrasena, confirmacion)) {
            throw new IllegalArgumentException("Las contraseñas no coinciden.");
        }

        ByteBuffer bytesContrasena = StandardCharsets.UTF_8.encode(CharBuffer.wrap(contrasena));
        int longitudUtf8 = bytesContrasena.remaining();
        if (bytesContrasena.hasArray()) {
            Arrays.fill(bytesContrasena.array(), (byte) 0);
        }
        if (contrasena.length < 12 || longitudUtf8 > 72) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 12 caracteres y no superar 72 bytes UTF-8.");
        }
    }
}
