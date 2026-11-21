# Historial de Arquitectura y Herramientas

Los números de las entradas conservan su identificador original; el contenido describe decisiones y resultados de esa etapa.

## 02. Arquitectura JPA y migración a Spring MVC

**Fecha:** 05/11/2026
**Rama:** `refactor/arquitectura-opalina`
**Estado:** corregido; verificado con la suite de integración.

> Nota de vigencia: este registro describe la corrección realizada el 05/11/2026. La aplicación ahora usa autenticación y autorización por rol; consulta la arquitectura y la matriz de trazabilidad actuales para el estado vigente.

### Hallazgo

| Causa | Impacto | Prioridad |
|---|---|---|
| `spring.main.web-application-type=none` mantenía el proyecto como consola y las clases de `controller` eran componentes de aplicación, no rutas HTTP. Entidades y repositorios estaban agrupados en `model` y `repository`. | La app no podía ofrecer la interfaz web solicitada y el código persistente era menos fácil de localizar y extender. | Alta |
| La consola ejecutaba un `CommandLineRunner` al iniciar y el sembrado demo se habilitaba por defecto. | El arranque no era adecuado para Spring MVC y podía insertar registros al conectar una base existente. | Alta |

### Corrección aplicada

- Se habilitó Spring MVC y Thymeleaf con Bootstrap 5.3.8, fragmentos compartidos y recursos `static/css` / `static/js`.
- Se reubicaron las entidades, conversores, enums y repositorios Spring Data en `persistence.jpa` y `domain.enums`.
- Se reemplazó el menú de consola por rutas MVC para consultar y registrar incidencias, además de descargar los formatos de exportación ya disponibles.
- `RegistroIncidenciaFormulario` no recibe el identificador de usuario. `IncidenciaWebController` obtiene una identidad demo configurada en servidor y crea la solicitud de aplicación.
- Security permite las rutas sin login en esta fase, mantiene protección CSRF y no genera una contraseña de usuario al arrancar.
- `trazalab.seed.enabled` queda desactivado por defecto para evitar cambios automáticos en bases existentes.
- Las rutas de mantenimiento y reportes informan que sus funcionalidades aún están pendientes.

### Error durante la implementación

La primera compilación tras mover las entidades detectó imports faltantes de `EstadoComputadora` y `EstadoIncidencia`, ahora ubicados en `domain.enums`. Se añadieron los imports explícitos en las dos entidades; la compilación y la suite posterior finalizaron correctamente. El error ocurrió antes de ejecutar la aplicación y no produjo cambios en PostgreSQL.

### Verificación

- `./mvnw.cmd -q test`: 11 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas; PostgreSQL temporal mediante Testcontainers.
- Las pruebas web validan el render del formulario y el listado, las rutas provisionales, los errores de entrada y que un `usuarioId` enviado por el cliente no altera la identidad decidida por el servidor.
- Las siete pruebas JPA anteriores continúan cubriendo registro, validación, paginación, exportación por lotes y comparación de memoria retenida.
- No se ejecutó Flyway ni se modificó el volumen PostgreSQL local durante esta rama.

### Trazabilidad

- Código: `src/main/java/com/utp/trazalab/controller` y `src/main/java/com/utp/trazalab/persistence/jpa` (rutas históricas; la organización actual está en [`src/main/java/com/utp/trazalab`](../src/main/java/com/utp/trazalab)) y [`src/main/resources/templates`](../src/main/resources/templates).
- Pruebas: la clase histórica `RegistroIncidenciaJpaTest` se dividió por flujo; los casos actuales están en [pruebas de integración](../src/test/java/com/utp/trazalab/integration/).
- Rama y PR: [`refactor/arquitectura-opalina` · PR #5](https://github.com/arell-dev/TrazaLab/pull/5).

## 05. Diagnóstico de arranque compatible con Spring Boot 4

**Fecha:** 06/11/2026

**Alcance:** salida de arranque y verificación de conectividad de PostgreSQL y API.

### Hallazgo

La primera implementación registraba la base de datos, pero no verificaba la API. Además, el intento de detectar el puerto usó el paquete previo a Spring Boot 4 y no compiló.

### Corrección

- Se conserva la salida estándar de Spring Boot con el banner de consola y la información de inicio activados explícitamente.
- Al terminar el arranque, `DiagnosticoArranque` consulta `current_database()` y registra el nombre de la base.
- En un contexto con servidor HTTP, consulta por HTTP `GET /api/health` usando el puerto real del servidor. En contextos de prueba MockMvc informa que la verificación HTTP no aplica.
- La barra lateral usa el logotipo UTP entregado con el diseño Stitch, mide 240 px en escritorio y pasa a navegación inferior en pantallas estrechas. Se ajustaron radios, foco y botón primario a los tokens de Stitch.
- Los mensajes de éxito se presentan mediante el toast discreto existente; no se reintroducen mensajes extensos en el flujo.

### Verificación

`./mvnw.cmd -q clean test` terminó con 23 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas: 18 de integración y 5 de manejo de excepciones. `RegistroIncidenciaJpaTest` comprueba `GET /api/health` contra PostgreSQL temporal.

También se ejecutó `./mvnw.cmd -q spring-boot:run` con el PostgreSQL local activo. La consola mostró el banner de Spring Boot 4.1.2, `Conexión a la base de datos 'trazalab': ✅` y `Conectividad a la API (http://localhost:8080/api/health): ✅`. Se revisaron visualmente las pantallas de incidencias, mantenimientos y reportes en escritorio, y la navegación inferior en una vista de 390 × 844 px.

## 06. Diagnósticos del editor Java

**Fecha:** 06/11/2026

**Alcance:** avisos del analizador Java y sincronización del proyecto Maven.

### Hallazgos

- El editor había marcado una variable local `incidencia` como no usada en el benchmark. En el estado actual, el benchmark recorre los resultados con un `Iterator` y no declara esa variable.
- Spring Tools marcó como no seguras las referencias a propiedades escritas como texto en el ordenamiento de incidencias.
- La corrección anterior usó `Sort.TypedSort` y `Sort.sort(Class)`, pero Spring Data Commons 4.1 los declara obsoletos. El editor mostró cuatro avisos, dos por cada método de ordenamiento.
- Tras modificar `pom.xml`, Java Language Support indicó que debía recargarse la configuración Maven del editor.

### Corrección

- El benchmark consume los resultados mediante `Iterator` y mantiene el conteo de filas leídas. El aviso de variable local no se reproduce en el código actual.
- Los ordenamientos ascendente y descendente usan `Sort.by(IncidenciaEntidad::getFechaRegistro, IncidenciaEntidad::getId)`, seguido de `ascending()` o `descending()`. Se conserva el criterio secundario por ID y se eliminan las llamadas obsoletas.
- El aviso de `pom.xml` solicita sincronizar el classpath del editor. La compilación y las pruebas Maven usan la configuración actual; para sincronizar VS Code se puede ejecutar **Java: Reload Projects**.

### Verificación

`mvnw -q test` terminó con 18 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas. Testcontainers ejecutó PostgreSQL 16.15 y Flyway aplicó las dos migraciones. La dependencia local `spring-data-commons:4.1.2` confirma la firma de `Sort.by(TypedPropertyPath...)`; una búsqueda en el código fuente no encontró usos restantes de `Sort.TypedSort` ni `Sort.sort(Class)`.

Referencia: [Spring Data Commons 4.1.2 — API de `Sort`](https://docs.spring.io/spring-data/commons/reference/api/java/org/springframework/data/domain/Sort.html).

## 07. Estructura REST y manejo de excepciones

**Fecha:** 06/11/2026

**Alcance:** organizar los controladores y los errores de API siguiendo los paquetes de referencia de Opalina, sin añadir una capa «Adaptador Web».

### Hallazgo

El endpoint REST de estado estaba junto a los controladores MVC en `controller`, y el proyecto no separaba las excepciones de recurso inexistente y conflicto de negocio. El paquete `dto` ya agrupaba formularios y respuestas, por lo que no necesitaba una reorganización adicional.

### Cambio

- Se movió `EstadoApiController` a `controller/api`; la ruta `GET /api/health` y su respuesta se mantienen.
- Se añadieron `RecursoNoEncontradoException` y `ConflictoNegocioException` para expresar fallos de consulta y conflictos del dominio.
- `GlobalExceptionHandler` genera respuestas RFC 9457 `ProblemDetail` con estados 400, 404 y 409, y solo se aplica a `controller.api`.
- Los formularios MVC siguen renderizando sus errores. Las excepciones de recurso y conflicto conservan la compatibilidad con el manejo actual de `IllegalArgumentException`.
- Se retiró el único `package-info.java` del código de aplicación.

### Verificación

`GlobalExceptionHandlerTest` comprueba los estados HTTP y el contenido para validaciones, recursos inexistentes, conflictos de negocio y errores de integridad sin exponer detalles internos. `RegistroIncidenciaJpaTest` verifica que `/api/health` mantiene su ruta y contrato, y que un QR inexistente devuelve el error al formulario sin persistir la incidencia.

## 08. Avisos en pruebas Java

### Observaciones

- `GlobalExceptionHandlerTest.FormularioApi.registrar(String)` solo se consulta mediante reflexión para construir el `MethodParameter` de la prueba de validación. El analizador Java no reconoce ese uso reflectivo y lo reporta como método sin uso local.
- `RegistroIncidenciaJpaTest` importaba `Assertions.assertFalse` sin utilizarlo.

### Corrección

- Se añadió `@SuppressWarnings("unused")` únicamente al método de prueba que sirve como fixture de reflexión.
- Se eliminó el import estático no utilizado.

### Verificación

Se ejecutaron `GlobalExceptionHandlerTest` y `RegistroIncidenciaJpaTest` con Maven. Ambas clases finalizaron correctamente.

### Seguimiento (07/11/2026)

- El analizador seguía señalando `FormularioApi.registrar(String)` aunque el uso reflectivo estaba documentado con `@SuppressWarnings("unused")`. Se cambió el método de privado a público para eliminar la condición que origina el aviso de miembro privado sin uso local.
- El diagnóstico sobre `Assertions.assertFalse` no coincide con el archivo actual: ese import ya no existe en `RegistroIncidenciaJpaTest.java`, por lo que no requirió otra edición.
- `GlobalExceptionHandlerTest` pasó sus cinco pruebas con Maven. La compilación también incluyó las fuentes de prueba del proyecto.

### Seguimiento (16/11/2026)

- El editor volvió a marcar como no utilizado el método local `FormularioApi.registrar(String)`, que solo se consultaba por reflexión.
- La prueba ahora obtiene `MethodParameter` del método `registrar` real de `IncidenciaWebController` y usa el nombre real del objeto de formulario. Se eliminó la clase fixture que generaba el aviso.
- No se ejecutaron pruebas durante esta corrección.
