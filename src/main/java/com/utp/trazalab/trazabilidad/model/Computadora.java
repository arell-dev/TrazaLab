package com.utp.trazalab.trazabilidad.model;

import com.utp.trazalab.trazabilidad.repository.converter.EstadoComputadoraConverter;
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

@Entity
@Table(name = "computadora")
public class Computadora {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "computadora_id")
    private Integer id;

    @Column(name = "codigo_qr", nullable = false, length = 100, unique = true)
    private String codigoQr;

    @Column(name = "numero_serie", nullable = false, length = 80, unique = true)
    private String numeroSerie;

    @Column(nullable = false, length = 60)
    private String marca;

    @Column(nullable = false, length = 80)
    private String modelo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ubicacion_id", nullable = false)
    private Ubicacion ubicacion;

    @Convert(converter = EstadoComputadoraConverter.class)
    @Column(nullable = false, length = 25)
    private EstadoComputadora estado;

    protected Computadora() {
    }

    public Computadora(String codigoQr, String numeroSerie, String marca, String modelo, Ubicacion ubicacion, EstadoComputadora estado) {
        this.codigoQr = codigoQr;
        this.numeroSerie = numeroSerie;
        this.marca = marca;
        this.modelo = modelo;
        this.ubicacion = ubicacion;
        this.estado = estado;
    }

    public Integer getId() {
        return id;
    }

    public String getCodigoQr() {
        return codigoQr;
    }

    public String getNumeroSerie() {
        return numeroSerie;
    }

    public String getMarca() {
        return marca;
    }

    public String getModelo() {
        return modelo;
    }

    public Ubicacion getUbicacion() {
        return ubicacion;
    }

    public EstadoComputadora getEstado() {
        return estado;
    }
}
