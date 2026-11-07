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
import java.time.LocalDateTime;

@Entity
@Table(name = "falla")
public class FallaEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "falla_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incidencia_id", nullable = false)
    private IncidenciaEntidad incidencia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tipo_falla_id", nullable = false)
    private TipoFallaEntidad tipo;

    @Column(name = "descripcion_tecnica", nullable = false, columnDefinition = "text")
    private String descripcionTecnica;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    protected FallaEntidad() {
    }

    public FallaEntidad(
        IncidenciaEntidad incidencia,
        TipoFallaEntidad tipo,
        String descripcionTecnica,
        LocalDateTime fechaRegistro) {
        this.incidencia = incidencia;
        this.tipo = tipo;
        this.descripcionTecnica = descripcionTecnica;
        this.fechaRegistro = fechaRegistro;
    }
}
