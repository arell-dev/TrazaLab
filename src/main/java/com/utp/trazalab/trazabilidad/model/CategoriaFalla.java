package com.utp.trazalab.trazabilidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categoria_falla")
public class CategoriaFalla {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "categoria_falla_id")
    private Integer id;

    @Column(nullable = false, length = 60)
    private String nombre;

    protected CategoriaFalla() {
    }

    public Integer getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
