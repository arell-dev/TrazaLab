package com.utp.trazalab.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "componente")
public class ComponenteEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "componente_id")
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 60)
    private String tipo;

    @Column(name = "numero_serie", length = 80, unique = true)
    private String numeroSerie;

    protected ComponenteEntidad() {
    }

    public ComponenteEntidad(String nombre, String tipo, String numeroSerie) {
        this.nombre = nombre;
        this.tipo = tipo;
        this.numeroSerie = numeroSerie;
    }

    public Integer getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTipo() {
        return tipo;
    }

    public String getNumeroSerie() {
        return numeroSerie;
    }
}
