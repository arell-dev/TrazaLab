# OBS-05 — Diagnóstico de arranque compatible con Spring Boot 4

**Fecha:** 06/11/2026

**Alcance:** salida de arranque y verificación de conectividad de PostgreSQL y API.

## Hallazgo

La primera implementación registraba la base de datos, pero no verificaba la API. Además, el intento de detectar el puerto usó el paquete previo a Spring Boot 4 y no compiló.

## Corrección

- Se conserva la salida estándar de Spring Boot con el banner de consola y la información de inicio activados explícitamente.
- Al terminar el arranque, `DiagnosticoArranque` consulta `current_database()` y registra el nombre de la base.
- En un contexto con servidor HTTP, consulta por HTTP `GET /api/health` usando el puerto real del servidor. En contextos de prueba MockMvc informa que la verificación HTTP no aplica.
- La barra lateral usa el logotipo UTP entregado con el diseño Stitch, mide 240 px en escritorio y pasa a navegación inferior en pantallas estrechas. Se ajustaron radios, foco y botón primario a los tokens de Stitch.
- Los mensajes de éxito se presentan mediante el toast discreto existente; no se reintroducen mensajes extensos en el flujo.

## Verificación

`./mvnw.cmd -q clean test` terminó con 23 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas: 18 de integración y 5 de manejo de excepciones. `RegistroIncidenciaJpaTest` comprueba `GET /api/health` contra PostgreSQL temporal.

También se ejecutó `./mvnw.cmd -q spring-boot:run` con el PostgreSQL local activo. La consola mostró el banner de Spring Boot 4.1.2, `Conexión a la base de datos 'trazalab': ✅` y `Conectividad a la API (http://localhost:8080/api/health): ✅`. Se revisaron visualmente las pantallas de incidencias, mantenimientos y reportes en escritorio, y la navegación inferior en una vista de 390 × 844 px.
