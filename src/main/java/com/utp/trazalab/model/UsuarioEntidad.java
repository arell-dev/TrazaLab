package com.utp.trazalab.model;

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
public class UsuarioEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rol_id", nullable = false)
    private RolEntidad rol;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 120)
    private String apellidos;

    @Column(nullable = false, length = 150, unique = true)
    private String correo;

    @Column(nullable = false, length = 15)
    private String estado;

    protected UsuarioEntidad() {
    }

    public UsuarioEntidad(RolEntidad rol, String nombres, String apellidos, String correo, String estado) {
        this.rol = rol;
        this.nombres = nombres;
        this.apellidos = apellidos;
        this.correo = correo;
        this.estado = estado;
    }

    public Integer getId() { return id; }
    public RolEntidad getRol() { return rol; }
    public String getNombres() { return nombres; }
    public String getApellidos() { return apellidos; }
    public String getCorreo() { return correo; }
    public String getEstado() { return estado; }
}
