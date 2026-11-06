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
@Table(name = "tipo_falla")
public class TipoFallaEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tipo_falla_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_falla_id", nullable = false)
    private CategoriaFallaEntidad categoria;

    @Column(nullable = false, length = 80)
    private String nombre;

    protected TipoFallaEntidad() {
    }

    public Integer getId() {
        return id;
    }

    public CategoriaFallaEntidad getCategoria() {
        return categoria;
    }

    public String getNombre() {
        return nombre;
    }
}
