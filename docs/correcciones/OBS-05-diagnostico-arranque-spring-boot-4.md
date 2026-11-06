# OBS-05 — Diagnóstico de arranque compatible con Spring Boot 4

**Fecha:** 06/11/2026

**Alcance:** verificación de conectividad de la base de datos al iniciar la aplicación.

## Hallazgo

La primera implementación del diagnóstico intentó detectar el puerto HTTP mediante `WebServerApplicationContext`. Esa clase no está disponible en las dependencias web usadas por este proyecto con Spring Boot 4.1.2 y la compilación falló.

## Corrección

- El diagnóstico de arranque ahora consulta `current_database()` con `JdbcTemplate` y registra el nombre de la base conectada.
- La comprobación HTTP se mantiene como `GET /api/health`, separada del evento de arranque.

## Verificación

`RegistroIncidenciaJpaTest` comprueba `GET /api/health` contra PostgreSQL temporal. La última suite terminó con 18 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas.
