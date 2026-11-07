# OBS-07 — Estructura REST y manejo de excepciones

**Fecha:** 06/11/2026

**Alcance:** organizar los controladores y los errores de API siguiendo los paquetes de referencia de Opalina, sin añadir una capa «Adaptador Web».

## Hallazgo

El endpoint REST de estado estaba junto a los controladores MVC en `controller`, y el proyecto no separaba las excepciones de recurso inexistente y conflicto de negocio. El paquete `dto` ya agrupaba formularios y respuestas, por lo que no necesitaba una reorganización adicional.

## Cambio

- Se movió `EstadoApiController` a `controller/api`; la ruta `GET /api/health` y su respuesta se mantienen.
- Se añadieron `RecursoNoEncontradoException` y `ConflictoNegocioException` para expresar fallos de consulta y conflictos del dominio.
- `GlobalExceptionHandler` genera respuestas RFC 9457 `ProblemDetail` con estados 400, 404 y 409, y solo se aplica a `controller.api`.
- Los formularios MVC siguen renderizando sus errores. Las excepciones de recurso y conflicto conservan la compatibilidad con el manejo actual de `IllegalArgumentException`.
- Se retiró el único `package-info.java` del código de aplicación.

## Verificación

`GlobalExceptionHandlerTest` comprueba los estados HTTP y el contenido para validaciones, recursos inexistentes, conflictos de negocio y errores de integridad sin exponer detalles internos. `RegistroIncidenciaJpaTest` verifica que `/api/health` mantiene su ruta y contrato, y que un QR inexistente devuelve el error al formulario sin persistir la incidencia.
