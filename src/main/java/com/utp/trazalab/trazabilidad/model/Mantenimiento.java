package com.utp.trazalab.trazabilidad.model;

import com.utp.trazalab.usuarios.model.Usuario;
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
public class Mantenimiento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mantenimiento_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "computadora_id", nullable = false)
    private Computadora computadora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "falla_id")
    private Integer fallaId;

    @Column(name = "tipo", nullable = false, length = 15)
    private String tipo;

    @Column(nullable = false, columnDefinition = "text")
    private String descripcion;

    @Column(nullable = false)
    private LocalDateTime fecha;

    protected Mantenimiento() {
    }

    public Mantenimiento(Computadora computadora, Usuario usuario, Integer fallaId, String tipo, String descripcion, LocalDateTime fecha) {
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

    public Computadora getComputadora() {
        return computadora;
    }

    public Usuario getUsuario() {
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
