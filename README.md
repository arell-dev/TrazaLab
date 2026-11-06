# TrazaLab

## Integrantes

- Barrutia Ojeda Mauricio
- Guevara Guerrero Ashley
- Jimenez Chinchay Mateo Sebastian
- Mendoza Sacsa Zamira Luana

TrazaLab registra la trazabilidad técnica de computadoras de laboratorio. La aplicación web permite consultar y registrar incidencias en PostgreSQL y descargar el listado en PDF o CSV.

## Capacidades disponibles

| Capacidad | Descripción |
|---|---|
| Registro de incidencias | El formulario captura manualmente el QR y la descripción; el servidor fija el usuario reportante de demostración. |
| Clasificación | Se puede clasificar una incidencia por categoría y tipo de falla para alimentar los reportes de recurrencia. |
| Consulta de incidencias | Presenta los registros más recientes primero, en páginas de 25 filas. |
| Exportación | Descarga incidencias en PDF o CSV y reportes de recurrencias en PDF o CSV. |
| Mantenimientos | Registra intervenciones preventivas o correctivas y acciones de instalación, intervención, reemplazo o retiro de componentes. |
| Reportes | Filtra recurrencias por periodo, equipo y tipo; considera recurrentes dos o más fallas del mismo tipo en el equipo. Exporta PDF y CSV. |
| Logging de aplicación | Escribe eventos de operación mediante SLF4J y Logback. |

El mantenimiento no cambia el estado operativo de la computadora ni cierra una incidencia. Los mantenimientos correctivos requieren asociar una falla del mismo equipo.

## Arquitectura

```text
Navegador
  → Spring MVC / Thymeleaf / Bootstrap 5.3.8
  → controller + DTO de formulario
  → service (reglas, transacción y eventos)
  → persistence.jpa.springdata / entity / converter
  → Hibernate / JPA
  → PostgreSQL
```

Las entidades y repositorios JPA están organizados en `persistence.jpa`, siguiendo la estructura usada por el proyecto ilustrativo Opalina. Los controladores MVC están en `controller`; no existe una capa adicional de «Adaptador Web».

| Paquete o recurso | Responsabilidad |
|---|---|
| `controller` | Recibe solicitudes HTTP, valida formularios y renderiza vistas o descargas. |
| `service` | Aplica reglas de negocio, transacciones y registro de eventos. |
| `dto` | Separa datos de formulario, identidad demo y datos de respuesta. |
| `persistence.jpa.entity` | Mapea entidades persistentes al esquema de PostgreSQL. |
| `persistence.jpa.springdata` | Define las consultas y operaciones JPA. |
| `persistence.jpa.converter` | Convierte estados entre Java y el esquema SQL. |
| `templates/fragments` | Comparte encabezado, menú, mensajes y scripts. |
| `templates/incidencias` | Contiene las vistas de consulta y registro. |
| `templates/mantenimientos`, `templates/reportes` | Contiene los paneles de registro técnico y consulta de recurrencias. |
| `static/css`, `static/js` | `colores.css` reúne tokens y variables Bootstrap; `componentes.css` define la interfaz y `app.js` sus interacciones. |
| `export` | Genera archivos de incidencias en PDF y CSV. |

La [arquitectura detallada](docs/Arquitectura.md) distingue los flujos implementados y el trabajo pendiente.

## Tecnologías

| Tecnología | Uso |
|---|---|
| Java 21 y Spring Boot 4.1.2 | Plataforma y ciclo de vida de la aplicación. |
| Spring MVC y Thymeleaf | Rutas web y plantillas HTML. |
| Bootstrap 5.3.8 | Componentes y diseño responsivo. |
| Spring Data JPA, Hibernate y PostgreSQL 16 | Persistencia y validación del esquema. |
| Flyway | Versionado del esquema SQL. |
| Spring Security | CSRF habilitado; autenticación desactivada en la etapa demo. |
| JUnit y Testcontainers | Pruebas con PostgreSQL temporal. |
| Apache PDFBox 3.0.8 | Reportes PDF. |
| Apache Commons CSV 1.14.1 | Exportación CSV. |
| SLF4J y Logback | Registro de eventos de aplicación. |

Las dependencias y versiones están declaradas en [`pom.xml`](pom.xml). La autenticación todavía no forma parte de esta etapa.

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

4. Arranca TrazaLab:

   ```powershell
   .\database\run.ps1
   ```

5. Abre `http://localhost:8080/incidencias`.

También se puede iniciar directamente con `.\mvnw.cmd spring-boot:run`. Spring Boot carga las variables desde `.env` y conecta con PostgreSQL.

El usuario reportante demo se selecciona mediante `TRAZALAB_DEMO_USER_EMAIL` o usa `ana.torres.demo@trazalab.local` por defecto. El responsable de mantenimiento se selecciona mediante `TRAZALAB_DEMO_TECH_EMAIL` o usa `tecnico.trazalab.demo@trazalab.local`. Debe ser un usuario activo con rol técnico, según las reglas del esquema. La opción de insertar datos demo está desactivada por defecto para no modificar bases existentes; se activa explícitamente con `TRAZALAB_SEED_ENABLED=true` solo en un entorno preparado para ello. El sembrado crea el usuario técnico demo si no existe.

Flyway aplica las migraciones bajo `src/main/resources/db/migration`. Hibernate utiliza `spring.jpa.hibernate.ddl-auto=validate` y no crea ni actualiza tablas. Para una base que ya tenía datos antes de Flyway, sigue [`database/README.md`](database/README.md#migraciones-y-volumenes-existentes); el procedimiento genera un respaldo antes de registrar la línea base y conserva el volumen.

## Exportación

Desde el listado de incidencias se pueden descargar PDF o CSV. Los archivos incluyen identificador, reportante, QR, descripción, fecha y estado. Se generan por lotes y se transmiten desde el directorio configurado por `TRAZALAB_EXPORT_DIRECTORY`, cuyo valor predeterminado es `evidencias/`. Los reportes de recurrencias también permiten descargar PDF o CSV; Excel no forma parte de las exportaciones disponibles.

## Logging

Los eventos funcionales se escriben en `logs/trazalab-app.log`. `APP_EVENT` registra acción, recurso, resultado y, cuando corresponde, referencias o cantidades. No contiene descripciones, nombres, correos ni contraseñas.

## Pruebas

Ejecuta la suite con Docker disponible:

```powershell
.\mvnw.cmd test
```

El 06/11/2026, `./mvnw.cmd -q test` terminó con 18 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas en PostgreSQL temporal mediante Testcontainers. La suite cubre persistencia, rutas web, clasificación de fallas, reglas de mantenimiento, componentes e informes. La matriz con caso, requisito, ejecución y resultado está en [`docs/testing/README.md`](docs/testing/README.md). Maven deja resultados y logs de prueba en `target/surefire-reports`.

## Alcance y documentación

Los flujos de incidencias clasificadas, mantenimientos y recurrencias están implementados en modo demo. El QR se introduce como texto; no hay lector de cámara ni autenticación. La consulta de historial técnico completo por computadora, la gestión integral del inventario, la autenticación y la autorización por sesión siguen pendientes.

- [Índice de documentación](docs/README.md)
- [Arquitectura](docs/Arquitectura.md)
- [Matriz de trazabilidad](docs/Matriz%20Trazabilidad.md)
- [Seguridad y límites](docs/Seguridad%20y%20Limites.md)
- [Versiones y fuentes](docs/Versiones%20y%20Fuente.md)
- [Diagrama de arquitectura y trazabilidad](docs/TrazaLab_Arquitectura_Trazabilidad.drawio)
- [Uso y migración de PostgreSQL](database/README.md)

Los archivos del docente de `TestLab_Semana13_COMPLETO` son referencias de testing. Se documenta la transferencia y ejecución del proyecto en `docs/`; esos materiales no se editan.
