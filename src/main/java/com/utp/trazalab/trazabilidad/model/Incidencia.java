package com.utp.trazalab.trazabilidad.model;

import com.utp.trazalab.usuarios.model.Usuario;
import com.utp.trazalab.trazabilidad.repository.converter.EstadoIncidenciaConverter;
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
public class Incidencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incidencia_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "computadora_id", nullable = false)
    private Computadora computadora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(name = "fecha_registro", nullable = false, columnDefinition = "TIMESTAMP")
    private Instant fechaRegistro;

    @Convert(converter = EstadoIncidenciaConverter.class)
    @Column(nullable = false, length = 15)
    private EstadoIncidencia estado;

    protected Incidencia() {
    }

    public Incidencia(Computadora computadora, Usuario usuario, String descripcion, Instant fechaRegistro, EstadoIncidencia estado) {
        this.computadora = computadora;
        this.usuario = usuario;
        this.descripcion = descripcion;
        this.fechaRegistro = fechaRegistro;
        this.estado = estado;
    }

    public Integer getId() {
        return id;
    }

    public Computadora getComputadora() {
        return computadora;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public Instant getFechaRegistro() {
        return fechaRegistro;
    }

    public EstadoIncidencia getEstado() {
        return estado;
    }
}
