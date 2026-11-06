package com.utp.trazalab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegistroMantenimientoFormulario {
    @NotBlank(message = "Ingresa el código QR del equipo.")
    @Size(max = 100, message = "El código QR no puede superar 100 caracteres.")
    private String codigoQr;

    @Pattern(regexp = "|preventivo|correctivo", message = "Selecciona el tipo de mantenimiento.")
    private String tipo = "preventivo";

    private Integer fallaId;

    @NotBlank(message = "La descripción del mantenimiento es obligatoria.")
    @Size(max = 1000, message = "La descripción no puede superar 1000 caracteres.")
    private String descripcion;

    private String accionComponente;
    private String serieComponenteActual;
    private String nombreComponenteNuevo;
    private String tipoComponenteNuevo;
    private String serieComponenteNuevo;

    public String getCodigoQr() {
        return codigoQr;
    }

    public void setCodigoQr(String codigoQr) {
        this.codigoQr = codigoQr;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Integer getFallaId() {
        return fallaId;
    }

    public void setFallaId(Integer fallaId) {
        this.fallaId = fallaId;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getAccionComponente() {
        return accionComponente;
    }

    public void setAccionComponente(String accionComponente) {
        this.accionComponente = accionComponente;
    }

    public String getSerieComponenteActual() {
        return serieComponenteActual;
    }

    public void setSerieComponenteActual(String serieComponenteActual) {
        this.serieComponenteActual = serieComponenteActual;
    }

    public String getNombreComponenteNuevo() {
        return nombreComponenteNuevo;
    }

    public void setNombreComponenteNuevo(String nombreComponenteNuevo) {
        this.nombreComponenteNuevo = nombreComponenteNuevo;
    }

    public String getTipoComponenteNuevo() {
        return tipoComponenteNuevo;
    }

    public void setTipoComponenteNuevo(String tipoComponenteNuevo) {
        this.tipoComponenteNuevo = tipoComponenteNuevo;
    }

    public String getSerieComponenteNuevo() {
        return serieComponenteNuevo;
    }

    public void setSerieComponenteNuevo(String serieComponenteNuevo) {
        this.serieComponenteNuevo = serieComponenteNuevo;
    }
}
