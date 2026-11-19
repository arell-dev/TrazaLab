package com.utp.trazalab.usuarios.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 120)
    private String apellidos;

    @Column(nullable = false, length = 150, unique = true)
    private String correo;

    @Column(nullable = false, length = 15)
    private String estado;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    protected Usuario() {
    }

    public Usuario(Rol rol, String nombres, String apellidos, String correo, String estado) {
        this.rol = rol;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.correo = correo;
        this.estado = estado;
    }

    public Usuario(Rol rol, String nombres, String apellidos, String correo, String estado, String passwordHash) {
        this(rol, nombres, apellidos, correo, estado);
        this.passwordHash = passwordHash;
    }

    public Integer getId() {
        return id;
    }

    public Rol getRol() {
        return rol;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public String getCorreo() {
        return correo;
    }

    public String getEstado() {
        return estado;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
