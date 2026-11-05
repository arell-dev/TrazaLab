package com.utp.trazalab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistroIncidenciaFormulario {
    @NotBlank(message = "Ingresa el código QR del equipo.")
    @Size(max = 100, message = "El código QR no puede superar 100 caracteres.")
    private String codigoQr;

    @NotBlank(message = "La descripción de la falla es obligatoria.")
    @Size(max = 500, message = "La descripción no puede superar 500 caracteres.")
    private String descripcion;

    public String getCodigoQr() {
        return codigoQr;
    }

    public void setCodigoQr(String codigoQr) {
        this.codigoQr = codigoQr;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
