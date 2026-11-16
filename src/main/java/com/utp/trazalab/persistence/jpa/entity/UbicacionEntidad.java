package com.utp.trazalab.persistence.jpa.entity;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sede_id", nullable = false)
    private SedeEntidad sede;

    @Column(length = 80)
    private String edificio;

    @Column
    private Integer piso;

    @Column(nullable = false, length = 20)
    private String tipo;

    protected UbicacionEntidad() {
    }

    public UbicacionEntidad(String nombre, String detalle, SedeEntidad sede, String edificio, Integer piso, String tipo) {
        this.nombre = nombre;
        this.detalle = detalle;
        this.sede = sede;
        this.edificio = edificio;
        this.piso = piso;
        this.tipo = tipo;
    }

    public Integer getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDetalle() {
        return detalle;
    }

    public SedeEntidad getSede() {
        return sede;
    }

    public String getEdificio() {
        return edificio;
    }

    public Integer getPiso() {
        return piso;
    }

    public String getTipo() {
        return tipo;
    }
}
