package com.utp.trazalab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ubicacion")
public class UbicacionEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ubicacion_id")
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 200)
    private String detalle;

    protected UbicacionEntidad() {
    }

    public UbicacionEntidad(String nombre, String detalle) {
        this.nombre = nombre;
        this.detalle = detalle;
    }

    public Integer getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDetalle() { return detalle; }
}
