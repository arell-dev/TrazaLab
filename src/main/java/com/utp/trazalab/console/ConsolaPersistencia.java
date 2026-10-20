package com.utp.trazalab.console;

import com.utp.trazalab.controller.ExportacionIncidenciasController;
import com.utp.trazalab.controller.IncidenciaController;
import com.utp.trazalab.dto.HistorialComputadoraDto;
import com.utp.trazalab.dto.IncidenciaRegistradaDto;
import com.utp.trazalab.dto.RegistroIncidenciaDto;
import com.utp.trazalab.dto.UsuarioResumenDto;
import com.utp.trazalab.export.FormatoExportacion;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("persistencia")
@Order(2)
@ConditionalOnProperty(name = "trazalab.persistencia.console.enabled", havingValue = "true", matchIfMissing = true)
public class ConsolaPersistencia implements CommandLineRunner {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));

    private final IncidenciaController controlador;
    private final ExportacionIncidenciasController exportacion;

    public ConsolaPersistencia(IncidenciaController controlador, ExportacionIncidenciasController exportacion) {
        this.controlador = controlador;
        this.exportacion = exportacion;
    }

    @Override
    public void run(String... args) {
        try (Scanner entrada = new Scanner(System.in)) {
            boolean salir = false;
            while (!salir) {
                mostrarMenu();
                if (!entrada.hasNextLine()) {
                    break;
                }
                String opcion = entrada.nextLine().trim();
                try {
                    switch (opcion) {
                        case "1" -> consultarIncidencias();
                        case "2" -> registrarIncidencia(entrada);
                        case "3" -> consultarHistorial(entrada);
                        case "4" -> exportarIncidencias(entrada);
                        case "0" -> salir = true;
                        default -> System.out.println("Opción no válida. Elige 1, 2, 3, 4 o 0.");
                    }
                } catch (IllegalArgumentException error) {
                    System.out.println("Aviso: " + error.getMessage());
                } catch (IllegalStateException error) {
                    System.out.println("Error: " + error.getMessage());
                }
                System.out.println();
            }
        }
        System.out.println("Fin de TrazaLab.");
    }

    private void mostrarMenu() {
        System.out.println("TrazaLab | Gestión de incidencias y equipos");
        System.out.println("1. Consultar incidencias existentes");
        System.out.println("2. Registrar incidencia");
        System.out.println("3. Ver historial de una computadora");
        System.out.println("4. Exportar incidencias a PDF, Excel o CSV");
        System.out.println("0. Salir");
        System.out.print("Opción: ");
        System.out.flush();
    }

    private void consultarIncidencias() {
        var incidencias = controlador.consultarIncidencias();
        if (incidencias.isEmpty()) {
            System.out.println("No hay incidencias registradas.");
            return;
        }

        System.out.println("Incidencias (de la más reciente a la más antigua):");
        incidencias.forEach(this::mostrarIncidencia);
    }

    private void registrarIncidencia(Scanner entrada) {
        System.out.println("Usuarios registrados:");
        for (UsuarioResumenDto usuario : controlador.consultarUsuarios()) {
            System.out.println(usuario.id() + ". " + usuario.nombres() + " " + usuario.apellidos()
                    + " | " + usuario.rol() + " | " + usuario.estado());
        }

        Integer usuarioId = leerId(entrada, "ID del usuario reportante: ");
        if (usuarioId == null) {
            return;
        }
        String codigoQr = leer(entrada, "Código QR de la computadora: ");
        if (codigoQr == null) {
            return;
        }
        String descripcion = leer(entrada, "Descripción de la incidencia: ");
        if (descripcion == null) {
            return;
        }

        RegistroIncidenciaDto solicitud = new RegistroIncidenciaDto();
        solicitud.setUsuarioId(usuarioId);
        solicitud.setCodigoQr(codigoQr);
        solicitud.setDescripcion(descripcion);
        IncidenciaRegistradaDto incidencia = controlador.registrar(solicitud);
        System.out.println("Incidencia #" + incidencia.id() + " guardada en PostgreSQL el "
                + FECHA.format(incidencia.fechaRegistro()) + ".");
    }

    private void consultarHistorial(Scanner entrada) {
        String codigoQr = leer(entrada, "Código QR de la computadora: ");
        if (codigoQr == null) {
            return;
        }
        HistorialComputadoraDto historial = controlador.consultarHistorialComputadora(codigoQr);
        System.out.println(historial.codigoQr() + " | Serie: " + historial.numeroSerie());
        System.out.println(historial.marca() + " " + historial.modelo() + " | " + historial.ubicacion());
        System.out.println("Estado: " + historial.estado());
        if (historial.incidencias().isEmpty()) {
            System.out.println("La computadora todavía no tiene incidencias registradas.");
            return;
        }

        System.out.println("Historial técnico (del más antiguo al más reciente):");
        historial.incidencias().forEach(this::mostrarIncidencia);
    }

    private void mostrarIncidencia(IncidenciaRegistradaDto incidencia) {
        System.out.println(FECHA.format(incidencia.fechaRegistro()) + " | Incidencia #" + incidencia.id()
                + " | " + incidencia.codigoQr() + " | " + incidencia.descripcion()
                + " [" + incidencia.estado() + "] | " + incidencia.reportante());
    }

    private void exportarIncidencias(Scanner entrada) {
        System.out.println("Formato del reporte:");
        System.out.println("1. PDF");
        System.out.println("2. Excel (.xlsx)");
        System.out.println("3. CSV (.csv)");
        System.out.println("0. Volver al menú");
        String opcion = leer(entrada, "Formato: ");
        if (opcion == null || opcion.equals("0")) {
            return;
        }

        FormatoExportacion formato = switch (opcion) {
            case "1" -> FormatoExportacion.PDF;
            case "2" -> FormatoExportacion.EXCEL;
            case "3" -> FormatoExportacion.CSV;
            default -> throw new IllegalArgumentException("Selecciona PDF, Excel, CSV o vuelve con 0.");
        };
        var resultado = exportacion.exportar(formato);
        System.out.println("\nExportación completada");
        System.out.println("Formato: " + resultado.formato());
        System.out.println("Incidencias: " + resultado.cantidadRegistros());
        System.out.println("Archivo: " + resultado.nombreArchivo());
        System.out.println("Ubicación: " + Path.of(resultado.ruta()).getParent());
    }

    private Integer leerId(Scanner entrada, String etiqueta) {
        String valor = leer(entrada, etiqueta);
        if (valor == null) {
            return null;
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("El ID de usuario debe ser numérico.");
        }
    }

    private String leer(Scanner entrada, String etiqueta) {
        System.out.print(etiqueta);
        return entrada.hasNextLine() ? entrada.nextLine().trim() : null;
    }
}
