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
@Table(name = "mantenimiento")
public class MantenimientoEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mantenimiento_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "computadora_id", nullable = false)
    private ComputadoraEntidad computadora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntidad usuario;

    @Column(name = "falla_id")
    private Integer fallaId;

    @Column(name = "tipo", nullable = false, length = 15)
    private String tipo;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    protected MantenimientoEntidad() {
    }

    public MantenimientoEntidad(
            ComputadoraEntidad computadora,
            UsuarioEntidad usuario,
            Integer fallaId,
            String tipo,
            String descripcion,
            LocalDateTime fecha) {
        this.computadora = computadora;
        this.usuario = usuario;
        this.fallaId = fallaId;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.fecha = fecha;
    }

    public Integer getId() {
        return id;
    }

    public ComputadoraEntidad getComputadora() {
        return computadora;
    }

    public UsuarioEntidad getUsuario() {
        return usuario;
    }

    public Integer getFallaId() {
        return fallaId;
    }

    public String getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
