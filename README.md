# TrazaLab

## Integrantes

- Barrutia Ojeda Mauricio
- Guevara Guerrero Ashley
- Jimenez Chinchay Mateo Sebastian
- Mendoza Sacsa Zamira Luana

TrazaLab registra la trazabilidad técnica de computadoras de laboratorio. La aplicación web permite consultar y registrar incidencias en PostgreSQL y generar reportes de fallas recurrentes.

## Capacidades disponibles

| Capacidad | Descripción |
|---|---|
| Registro de incidencias | El formulario verifica el QR antes de avanzar y muestra los datos básicos del equipo y sus cinco incidencias más recientes; el servidor atribuye el registro a la cuenta autenticada. |
| Clasificación | Se puede clasificar una incidencia por categoría y tipo de falla para alimentar los reportes de recurrencia. |
| Consulta de incidencias | Presenta los registros más recientes primero, en páginas de 25 filas. |
| Exportación | Descarga reportes de recurrencias en CSV, Excel o PDF. El listado de incidencias no ofrece exportación. |
| Mantenimientos | Registra intervenciones preventivas o correctivas y acciones de instalación, intervención, reemplazo o retiro de componentes. |
| Reportes | Filtra recurrencias por periodo, equipo y tipo; considera recurrentes dos o más fallas del mismo tipo en el equipo. Exporta CSV, Excel y PDF. |
| Acceso y usuarios | Autentica cuentas activas desde PostgreSQL con contraseñas BCrypt y restringe las rutas según el rol almacenado. |
| Logging de aplicación | Escribe eventos de operación mediante SLF4J y Logback. |

El mantenimiento no cambia el estado operativo de la computadora ni cierra una incidencia. Los mantenimientos correctivos requieren asociar una falla del mismo equipo.

## Arquitectura

El código Java se organiza por capacidades en `usuarios`, `trazabilidad` y `reportes`. Cada módulo reúne sus controladores, servicios, modelos y repositorios en paquetes separados. `app` contiene el arranque y los extremos generales. `exception` centraliza los errores de la API.

Las vistas Thymeleaf permanecen en `templates` y los recursos de interfaz en `static`. Los exportadores CSV, Excel y PDF pertenecen a `reportes/export`.

La [arquitectura detallada](docs/Arquitectura.md) distingue los flujos implementados y el trabajo pendiente.

## Tecnologías

| Tecnología | Uso |
|---|---|
| Java 21 y Spring Boot 4.1.2 | Plataforma y ciclo de vida de la aplicación. |
| Spring MVC y Thymeleaf | Rutas web y plantillas HTML. |
| Bootstrap 5.3.8 | Componentes y diseño responsivo. |
| Spring Data JPA, Hibernate y PostgreSQL 16 | Persistencia y validación del esquema. |
| Flyway | Versionado del esquema SQL. |
| Spring Security | Login por formulario, sesiones, CSRF y autorización por rol. Las cuentas se consultan desde PostgreSQL. |
| JUnit y Testcontainers | Pruebas con PostgreSQL temporal. |
| Apache PDFBox 3.0.8 | Reportes PDF. |
| Apache POI 5.5.1 | Reportes Excel (XLSX). |
| Apache Commons CSV 1.14.1 | Exportación CSV. |
| SLF4J y Logback | Registro de eventos de aplicación. |

Las dependencias y versiones están declaradas en [`pom.xml`](pom.xml). El login valida el estado, el hash BCrypt y el rol de cada cuenta en la tabla `usuario`.

## Requisitos

- JDK 21.
- Docker Desktop con Docker Compose.
- PowerShell en Windows.

## Configuración y ejecución

1. Crea la configuración local a partir de la plantilla:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Establece una contraseña local en `SPRING_DATASOURCE_PASSWORD`.
3. Inicia PostgreSQL desde la raíz del repositorio:

   ```powershell
   docker compose --env-file .env up -d
   ```

4. Recupera la base de datos correspondiente y comprueba que contenga las cuentas activas necesarias para acceder. Las credenciales `SPRING_DATASOURCE_*` de `.env` permiten conectar con PostgreSQL.

5. Arranca TrazaLab:

   ```powershell
   .\database\run.ps1
   ```

6. Abre `http://localhost:8080/usuarios/login` e inicia sesión con una cuenta activa registrada en la base de datos.

También se puede iniciar directamente con `.\mvnw.cmd spring-boot:run`. Spring Boot carga las variables desde `.env` y conecta con PostgreSQL.

La carga opcional de datos de ejemplo está desactivada por defecto. Si se activa con `TRAZALAB_SEED_ENABLED=true`, puede registrar `PC-001` en el aula A0301. Las cuentas se consultan en PostgreSQL y las acciones se atribuyen a la sesión autenticada.

Flyway aplica las migraciones bajo `src/main/resources/db/migration`. Hibernate utiliza `spring.jpa.hibernate.ddl-auto=validate` y no crea ni actualiza tablas. Para una base que ya tenía datos antes de Flyway, sigue [`database/README.md`](database/README.md#migraciones-y-volumenes-existentes); el procedimiento genera un respaldo antes de registrar la línea base y conserva el volumen.

## Exportación

Las descargas están disponibles únicamente en Reportes y respetan los filtros de periodo, equipo y tipo. Se ofrecen CSV, Excel (XLSX) y PDF para las fallas recurrentes. El listado de incidencias no tiene rutas ni botones de exportación.

Los listados de usuarios y reportes presentan 20 registros por página. Los indicadores y las exportaciones de reportes consideran todos los resultados que cumplen los filtros.

## Verificaciones y logs

Con la aplicación iniciada, ejecuta las comprobaciones desde PowerShell:

```powershell
curl.exe -i http://localhost:8080/health
curl.exe -i http://localhost:8080/db-health
curl.exe -i http://localhost:8080/api/health
```

`/health` confirma que la aplicación responde con HTTP 200 y `{"status":"UP"}`. `/db-health` consulta PostgreSQL con `SELECT 1`: devuelve HTTP 200 y `{"status":"UP"}` cuando la conexión funciona, o HTTP 503 y `{"status":"DOWN"}` si falla. `/api/health` conserva la comprobación conjunta y devuelve `{"estado":"disponible"}` o HTTP 503 con `{"estado":"no disponible"}`. Ajusta el puerto si cambias `server.port`.

Los registros se guardan en `logs` por defecto. La variable `TRAZALAB_LOG_DIR` permite elegir otra carpeta antes de iniciar la aplicación:

- `logs/arranque/arranque.log` registra el ciclo de arranque y sus fallos.
- `logs/actividad/actividad.log` contiene eventos funcionales `APP_EVENT`.
- `logs/errores/errores.log` reúne mensajes de nivel ERROR de la aplicación y el framework.
- `logs/chequeos/chequeos.log` contiene las comprobaciones de arranque y las solicitudes de salud.

Cada categoría rota por tamaño y fecha, con archivos comprimidos en su subcarpeta `archive`, límite de 10 MB por archivo, historial de 30 días y tope de 500 MB por categoría. Los errores también permanecen en su categoría de origen cuando corresponda. `APP_EVENT` registra acción, recurso, resultado y, cuando corresponde, referencias o cantidades.

## Pruebas

Ejecuta la suite con Docker disponible:

```powershell
.\mvnw.cmd test
```

La ejecución local de `clean verify` del 19/11/2026 terminó con 64 pruebas aprobadas, sin fallos, errores ni omisiones, con PostgreSQL temporal mediante Testcontainers. Las pruebas de integración se organizan por flujo e incluyen la validación de QR y la consulta ordenada de las cinco incidencias recientes. La matriz con caso, requisito, ejecución y resultado está en [`docs/testing/README.md`](docs/testing/README.md). Maven deja los resultados en `target/surefire-reports`.

## Alcance y documentación

Los flujos de incidencias clasificadas, mantenimientos y recurrencias están implementados en código. La pantalla de escaneo permite leer QR con la cámara del navegador o ingresar el código manualmente; la lectura por cámara requiere permisos y un contexto seguro. El login, la autorización por rol y la atribución de incidencias y mantenimientos usan la cuenta activa almacenada en PostgreSQL y asociada a la sesión. La ficha del equipo muestra una cronología paginada de incidencias, fallas, mantenimientos y componentes. La cobertura frente a los requisitos y su validación en piloto permanecen pendientes.

- [Índice de documentación](docs/README.md)
- [Arquitectura](docs/Arquitectura.md)
- [Matriz de trazabilidad](docs/Matriz%20Trazabilidad.md)
- [Seguridad y límites](docs/Seguridad%20y%20Limites.md)
- [Versiones y fuentes](docs/Versiones%20y%20Fuente.md)
- [Diagrama de arquitectura y trazabilidad](docs/TrazaLab_Arquitectura_Trazabilidad.drawio)
- [Uso y migración de PostgreSQL](database/README.md)

Los archivos del docente de `TestLab_Semana13_COMPLETO` son referencias de testing. Se documenta la transferencia y ejecución del proyecto en `docs/`; esos materiales no se editan.
