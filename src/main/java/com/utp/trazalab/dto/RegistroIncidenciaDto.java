package com.utp.trazalab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class RegistroIncidenciaDto {
    @NotNull(message = "Selecciona quién reporta la incidencia.")
    @Positive(message = "Selecciona un usuario válido.")
    private Integer usuarioId;

    @NotBlank(message = "Selecciona una computadora.")
    @Size(max = 100, message = "El código QR no puede superar 100 caracteres.")
    private String codigoQr;

    @NotBlank(message = "La descripción es obligatoria.")
    @Size(max = 500, message = "La descripción no puede superar 500 caracteres.")
    private String descripcion;

    public Integer getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Integer usuarioId) {
        this.usuarioId = usuarioId;
    }

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
