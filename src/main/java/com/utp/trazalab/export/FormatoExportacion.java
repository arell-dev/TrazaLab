package com.utp.trazalab.export;

public enum FormatoExportacion {
    PDF("PDF", "pdf"),
    EXCEL("Excel", "xlsx"),
    CSV("CSV", "csv");

    private final String etiqueta;
    private final String extension;

    FormatoExportacion(String etiqueta, String extension) {
        this.etiqueta = etiqueta;
        this.extension = extension;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public String extension() {
        return extension;
    }
}
