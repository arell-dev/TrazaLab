package com.utp.trazalab.model;

import com.utp.trazalab.database.converter.EstadoIncidenciaJpaConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "incidencia")
public class IncidenciaEntidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incidencia_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "computadora_id", nullable = false)
    private ComputadoraEntidad computadora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private UsuarioEntidad usuario;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "fecha_registro", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant fechaRegistro;

    @Convert(converter = EstadoIncidenciaJpaConverter.class)
    @Column(nullable = false, length = 15)
    private EstadoIncidencia estado;

    protected IncidenciaEntidad() {
    }

    public IncidenciaEntidad(ComputadoraEntidad computadora, UsuarioEntidad usuario, String descripcion, Instant fechaRegistro, EstadoIncidencia estado) {
        this.computadora = computadora;
        this.usuario = usuario;
        this.descripcion = descripcion;
        this.fechaRegistro = fechaRegistro;
        this.estado = estado;
    }

    public Integer getId() { return id; }
    public ComputadoraEntidad getComputadora() { return computadora; }
    public UsuarioEntidad getUsuario() { return usuario; }
    public String getDescripcion() { return descripcion; }
    public Instant getFechaRegistro() { return fechaRegistro; }
    public EstadoIncidencia getEstado() { return estado; }
}
