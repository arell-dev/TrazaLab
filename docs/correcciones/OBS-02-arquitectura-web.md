# OBS-02 · Arquitectura JPA y migración a Spring MVC

**Fecha:** 05/11/2026
**Rama:** `refactor/arquitectura-opalina`
**Estado:** corregido; verificado con la suite de integración.

## Hallazgo

| Causa | Impacto | Prioridad |
|---|---|---|
| `spring.main.web-application-type=none` mantenía el proyecto como consola y las clases de `controller` eran componentes de aplicación, no rutas HTTP. Entidades y repositorios estaban agrupados en `model` y `repository`. | La app no podía ofrecer la interfaz web solicitada y el código persistente era menos fácil de localizar y extender. | Alta |
| La consola ejecutaba un `CommandLineRunner` al iniciar y el sembrado demo se habilitaba por defecto. | El arranque no era adecuado para Spring MVC y podía insertar registros al conectar una base existente. | Alta |

## Corrección aplicada

- Se habilitó Spring MVC y Thymeleaf con Bootstrap 5.3.8, fragmentos compartidos y recursos `static/css` / `static/js`.
- Se reubicaron las entidades, conversores, enums y repositorios Spring Data en `persistence.jpa` y `domain.enums`.
- Se reemplazó el menú de consola por rutas MVC para consultar y registrar incidencias, además de descargar los formatos de exportación ya disponibles.
- `RegistroIncidenciaFormulario` no recibe el identificador de usuario. `IncidenciaWebController` obtiene una identidad demo configurada en servidor y crea la solicitud de aplicación.
- Security permite las rutas sin login en esta fase, mantiene protección CSRF y no genera una contraseña de usuario al arrancar.
- `trazalab.seed.enabled` queda desactivado por defecto para evitar cambios automáticos en bases existentes.
- Las rutas de mantenimiento y reportes informan que sus funcionalidades aún están pendientes.

## Error durante la implementación

La primera compilación tras mover las entidades detectó imports faltantes de `EstadoComputadora` y `EstadoIncidencia`, ahora ubicados en `domain.enums`. Se añadieron los imports explícitos en las dos entidades; la compilación y la suite posterior finalizaron correctamente. El error ocurrió antes de ejecutar la aplicación y no produjo cambios en PostgreSQL.

## Verificación

- `./mvnw.cmd -q test`: 11 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas; PostgreSQL temporal mediante Testcontainers.
- Las pruebas web validan el render del formulario y el listado, las rutas provisionales, los errores de entrada y que un `usuarioId` enviado por el cliente no altera la identidad decidida por el servidor.
- Las siete pruebas JPA anteriores continúan cubriendo registro, validación, paginación, exportación por lotes y comparación de memoria retenida.
- No se ejecutó Flyway ni se modificó el volumen PostgreSQL local durante esta rama.

## Trazabilidad

- Código: [`src/main/java/com/utp/trazalab/controller`](../../src/main/java/com/utp/trazalab/controller), [`src/main/java/com/utp/trazalab/persistence/jpa`](../../src/main/java/com/utp/trazalab/persistence/jpa) y [`src/main/resources/templates`](../../src/main/resources/templates).
- Pruebas: [`RegistroIncidenciaJpaTest`](../../src/test/java/com/utp/trazalab/integration/RegistroIncidenciaJpaTest.java).
- Rama y PR: se completarán al publicar la revisión en GitHub.
