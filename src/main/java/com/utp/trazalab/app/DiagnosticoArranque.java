package com.utp.trazalab.app;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DiagnosticoArranque {
    private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticoArranque.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final JdbcTemplate jdbcTemplate;

    public DiagnosticoArranque(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verificarConexiones(ApplicationReadyEvent evento) {
        verificarBaseDeDatos();

        if (evento.getApplicationContext() instanceof WebServerApplicationContext contextoWeb) {
            verificarApi(contextoWeb.getWebServer().getPort());
            return;
        }

        LOGGER.info("Conectividad a la API: no evaluada (contexto sin servidor HTTP)");
    }

    private void verificarBaseDeDatos() {
        String nombreBaseDatos = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
        LOGGER.info("Conexión a la base de datos '{}': ✅", nombreBaseDatos);
    }

    private void verificarApi(int puerto) {
        URI endpoint = URI.create("http://localhost:%d/api/health".formatted(puerto));
        HttpRequest solicitud = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        try {
            HttpResponse<String> respuesta = HTTP_CLIENT.send(solicitud, HttpResponse.BodyHandlers.ofString());
            boolean disponible = respuesta.statusCode() == 200 && respuesta.body().contains("disponible");

            if (disponible) {
                LOGGER.info("Conectividad a la API ({}): ✅", endpoint);
                return;
            }

            LOGGER.warn("Conectividad a la API ({}): ❌ HTTP {}", endpoint, respuesta.statusCode());
        } catch (IOException error) {
            LOGGER.warn("Conectividad a la API ({}): ❌ {}", endpoint, error.getMessage());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Conectividad a la API ({}): interrumpida", endpoint, error);
        }
    }
}
