package com.utp.trazalab.usuarios.controller.form;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UsuarioForm {
    @NotBlank
    @Size(max = 100)
    private String nombres;

    @NotBlank
    @Size(max = 120)
    private String apellidos;

    @NotBlank
    @Email
    @Size(max = 150)
    private String correo;

    @NotBlank
    @Size(min = 12, max = 72)
    private String contrasena;

    @NotNull
    private Integer rolId;

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public Integer getRolId() {
        return rolId;
    }

    public void setRolId(Integer rolId) {
        this.rolId = rolId;
    }
}
