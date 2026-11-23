package com.utp.trazalab.operacion;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import jdk.net.ExtendedSocketOptions;
import jdk.net.UnixDomainPrincipal;

public final class CanalDrenaje implements AutoCloseable {
    private final ControlActividad actividad;
    private final Path ruta;
    private final ServerSocketChannel servidor;
    private final Object identidadSocket;
    private final Thread hilo;

    public CanalDrenaje(ControlActividad actividad, Path ruta) throws IOException {
        this.actividad = actividad;
        this.ruta = ruta.toAbsolutePath().normalize();
        Path directorio = this.ruta.getParent();

        if (directorio == null || Files.isSymbolicLink(directorio) || !Files.isDirectory(directorio, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Directorio del canal inválido.");
        }

        int modo = (int) Files.getAttribute(directorio, "unix:mode", LinkOption.NOFOLLOW_LINKS) & 0777;
        int propietario = (int) Files.getAttribute(directorio, "unix:uid", LinkOption.NOFOLLOW_LINKS);
        int proceso = (int) Files.getAttribute(Path.of("/proc/self"), "unix:uid");

        if (modo != 0700 || propietario != proceso || Files.exists(this.ruta, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("El directorio no es privado o el socket ya existe.");
        }

        servidor = ServerSocketChannel.open(StandardProtocolFamily.UNIX);

        try {
            servidor.bind(UnixDomainSocketAddress.of(this.ruta));
            Files.setPosixFilePermissions(this.ruta, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            identidadSocket = Files.readAttributes(this.ruta, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey();
        } catch (IOException | RuntimeException excepcion) {
            servidor.close();
            throw excepcion;
        }

        hilo = new Thread(this::atender, "canal-drenaje");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void atender() {
        while (servidor.isOpen()) {
            try (SocketChannel conexion = servidor.accept()) {
                UnixDomainPrincipal credenciales = conexion.getOption(ExtendedSocketOptions.SO_PEERCRED);

                if (credenciales == null || !"root".equals(credenciales.user().getName())) {
                    continue;
                }

                procesar(conexion);
            } catch (IOException | RuntimeException excepcion) {

                if (servidor.isOpen()) {
                    System.err.println("Canal de drenaje: no se pudo atender una conexión.");
                }

            }
        }
    }

    private void procesar(SocketChannel conexion) throws IOException {
        conexion.configureBlocking(false);

        try (Selector selector = Selector.open()) {
            conexion.register(selector, SelectionKey.OP_READ);
            ByteBuffer entrada = ByteBuffer.allocate(32);
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (entrada.hasRemaining() && System.nanoTime() - limite < 0) {
                long espera = Math.max(1, TimeUnit.NANOSECONDS.toMillis(limite - System.nanoTime()));

                if (selector.select(espera) == 0) {
                    continue;
                }

                selector.selectedKeys().clear();
                int leidos = conexion.read(entrada);

                if (leidos < 0) {
                    return;
                }

                if (leidos > 0 && entrada.get(entrada.position() - 1) == '\n') {
                    String comando = new String(entrada.array(), 0, entrada.position() - 1, StandardCharsets.US_ASCII);
                    String resultado = switch (comando) {
                        case "INICIAR" -> actividad.iniciarDrenaje(Duration.ofMinutes(30)) ? "DRENANDO\n" : "NO_DISPONIBLE\n";
                        case "ESTADO" -> actividad.drenajeVigente() ? "DRENANDO\n" : "NO_DRENANDO\n";
                        case "OBSERVAR" -> observarActividad();
                        case "CANCELAR" -> {
                            actividad.cancelarDrenaje();
                            yield "CANCELADO\n";
                        }
                        default -> "COMANDO_INVALIDO\n";
                    };

                    ByteBuffer salida = StandardCharsets.US_ASCII.encode(resultado);
                    conexion.register(selector, SelectionKey.OP_WRITE);
                    while (salida.hasRemaining() && System.nanoTime() - limite < 0) {

                        if (selector.select(1) > 0) {
                            selector.selectedKeys().clear();
                            conexion.write(salida);
                        }

                    }
                    return;
                }
            }
        }
    }

    private String observarActividad() {
        EstadoActividad estado = actividad.estado();
        return "ESTADO|" + estado.relojValido() + "|" + estado.inactividadNanos() + "|" + estado.solicitudesActivas() + "|" + estado.drenando() + "\n";
    }

    @Override
    public void close() throws IOException {
        servidor.close();
        Object actual = Files.exists(ruta, LinkOption.NOFOLLOW_LINKS) ? Files.readAttributes(ruta, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).fileKey() : null;

        if (identidadSocket != null && identidadSocket.equals(actual)) {
            Files.delete(ruta);
        }

    }
}
