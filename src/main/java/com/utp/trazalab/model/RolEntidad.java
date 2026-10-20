package com.utp.trazalab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rol")
public class RolEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rol_id")
    private Integer id;

    @Column(nullable = false, length = 60, unique = true)
    private String nombre;

    @Column(length = 200)
    private String descripcion;

    protected RolEntidad() {
    }

    public RolEntidad(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
}
