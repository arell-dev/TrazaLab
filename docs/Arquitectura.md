# Arquitectura de TrazaLab

El [diagrama editable de arquitectura](TrazaLab_Arquitectura_Trazabilidad.drawio) refleja los módulos web, servicios, persistencia y comprobaciones de salud de la estructura actual. La integración con el agente de monitoreo figura como propuesta pendiente. Se verificaron su estructura XML, referencias internas y presentación de sus tres páginas en draw.io.

## Criterio de diseño

La aplicación sigue una estructura MVC con servicios y persistencia JPA. El código se agrupa por capacidad en `usuarios`, `trazabilidad` y `reportes`, con paquetes hermanos para controladores, servicios, repositorios y modelos donde corresponde. Las clases transversales permanecen en `app`, `config`, `exception` y `logging`.

Los formularios y DTO se ubican junto a su módulo. `ApiExceptionHandler` limita las respuestas REST `ProblemDetail` mediante el alcance configurado en la propia clase. Los controladores MVC mantienen sus mensajes y validaciones de formulario.

La interfaz actual está construida con Spring MVC, Thymeleaf y Bootstrap 5.3.8. Los fragmentos compartidos están en `templates/fragments`, las pantallas en carpetas por módulo y los recursos del cliente en `static/css` y `static/js`.

El esquema PostgreSQL se versiona con migraciones Flyway en `src/main/resources/db/migration`, mientras Hibernate valida el mapeo. El [`compose.yaml`](../compose.yaml) está en la raíz para levantar la base desde el proyecto; `database/` conserva la guía operativa y el procedimiento de adopción para volúmenes existentes.

## Flujo web implementado

```text
Navegador
  → Thymeleaf / Bootstrap
  → IncidenciaController
  → IncidenciaForm + Bean Validation
  → IncidenciaService (registro de eventos)
  → RegistroIncidencia (reglas y transacción)
  → trazabilidad.repository
  → Hibernate / JPA
  → PostgreSQL
```

`IncidenciaForm` recibe el código QR, la descripción y un tipo de falla opcional. El controlador obtiene el correo del principal autenticado, resuelve la cuenta activa en PostgreSQL y construye `IncidenciaSolicitudDto`; no acepta un `usuarioId` enviado desde el navegador. El servicio valida el rol reportante, resuelve el QR y guarda la incidencia dentro de una transacción.

Antes de avanzar desde el primer paso, la interfaz consulta `GET /api/computadoras/historial?codigoQr=...`. `ComputadoraApiController` delega en el servicio de aplicación y el servicio JPA devuelve una ficha breve del equipo junto con las cinco incidencias más recientes, ordenadas por fecha e identificador descendente. La respuesta usa DTO de lista estable y no incluye el nombre del reportante. Un QR vacío devuelve 400 y uno inexistente devuelve 404 mediante `ProblemDetail`.

La consulta web muestra incidencias por páginas de hasta 25 registros; el servicio mantiene su límite máximo de 100. La pantalla de incidencias no ofrece exportaciones. Los archivos CSV, XLSX y PDF se generan únicamente desde Reportes y respetan los filtros activos.

Las cuentas y las recurrencias se presentan en páginas de 20 registros. Los indicadores y la distribución de Reportes se calculan sobre todas las recurrencias que cumplen los filtros; las descargas incluyen el conjunto completo. Las mediciones locales y sus límites se describen en [OBS-10](correcciones/OBS-10-paginacion-y-escalabilidad.md).

`IncidenciaService` registra los eventos mediante `LoggingAplicacion`. `Slf4jLoggingAplicacion` escribe `APP_EVENT` con SLF4J y Logback; no registra descripciones, nombres ni correos.

## Distribución de módulos

```text
com.utp.trazalab
|-- app                  Arranque, pagina inicial y salud
|-- config               Configuracion de reloj
|-- exception            Excepciones y respuestas REST
|-- logging              Eventos de aplicacion
|-- usuarios             Cuentas, roles y seguridad
|-- trazabilidad         Equipos, incidencias, historial y mantenimientos
|   |-- controller       Solicitudes MVC y API de equipos
|   |   `-- form         Formularios web de incidencias y mantenimiento
|   |-- dto              Datos de solicitud y respuesta de los flujos tecnicos
|   |-- model            Entidades y estados del dominio
|   |-- repository       Spring Data y conversores de estados
|   `-- service          Reglas, consultas y transacciones
`-- reportes             Controlador, servicio, consulta SQL, DTO y exportadores
```

Los recursos web están organizados así:

```text
templates/
├── fragments/              Encabezado, navegación, mensajes y scripts
├── equipos/                Disponibilidad, escaneo y ficha técnica
├── incidencias/            Consulta y formulario de incidencias
├── mantenimientos/         Registro de intervenciones y componentes
├── reportes/               Filtros y resultados de recurrencias
└── usuarios/               Acceso y gestión de cuentas
static/
├── css/                    colores.css (tokens y Bootstrap) y componentes.css
└── js/                     Interacciones pequeñas de la interfaz
```

## Límites y estado funcional

| Módulo | Responsabilidad | Estado actual |
|---|---|---|
| `usuarios` | Sesión, cuentas y roles | Acceso con cuentas persistidas en PostgreSQL |
| `trazabilidad` | Disponibilidad, QR, incidencias, historial, fallas y mantenimientos | Flujos web y API en el código |
| `reportes` | Consulta y exportación de recurrencias | CSV, XLSX y PDF para fallas clasificadas |
| `app`, `config`, `exception`, `logging` | Arranque, reloj, errores y eventos transversales | Implementación compartida |
| Integración con agente | Recibir información de una aplicación independiente | Diseñado; contrato pendiente |

Spring Security autentica el login mediante `UsuarioDetailsService`, que consulta cuentas activas y su hash BCrypt en PostgreSQL. Las rutas se autorizan por el rol asociado a la cuenta; `ROLE_Administrador` accede a la gestión de usuarios. Las cuentas de acceso deben existir en la base de datos recuperada. Las acciones web se atribuyen al principal autenticado. La carga opcional de equipos de ejemplo está desactivada por defecto.

## Persistencia y ejecución

- PostgreSQL es el almacenamiento persistente; no se usa un repositorio en memoria.
- Docker Compose define PostgreSQL en `compose.yaml` en la raíz del proyecto, con volumen persistente y healthcheck.
- Hibernate valida las entidades contra el esquema con `spring.jpa.hibernate.ddl-auto=validate`; no crea ni actualiza tablas.
- Flyway aplica migraciones SQL inmutables desde `src/main/resources/db/migration`.
- `spring.jpa.open-in-view=false` evita mantener sesiones JPA abiertas al renderizar vistas.
- Las credenciales locales se cargan desde `.env`, excluido de Git.
- La línea base de un volumen preexistente se registra solo después de validar el esquema y generar un respaldo. No se activa `baseline-on-migrate` de forma general.
- La propiedad `trazalab.seed.enabled` queda en `false` por defecto. No se elimina ni reinicializa el volumen local.
- La clasificación de incidencias es opcional; el reporte solo considera fallas clasificadas.
- Un mantenimiento correctivo requiere una falla de una incidencia del mismo equipo.

Las entidades principales se guardan con Spring Data JPA. `MantenimientoService` coordina la transacción y `AsignacionComponenteRepository` usa `JdbcTemplate` para el historial temporal de asignaciones y la relación de acciones de componentes. `ReporteRepository` usa una consulta SQL agrupada y parametrizada para contar fallas y filtrar periodo, equipo y tipo.

`HealthController` expone `/health` para comprobar la respuesta de la aplicación, `/db-health` para consultar PostgreSQL y `/api/health` como comprobación conjunta. Los registros de arranque, actividad, errores y chequeos se distribuyen según `logback-spring.xml`; los comandos y rutas se describen en el [README](../README.md#verificaciones-y-logs).

Para el arranque local y la guía de base de datos, consulta [`README.md`](../README.md) y [`database/README.md`](../database/README.md).

## Trazabilidad y pruebas

| Elemento | Correspondencia | Estado |
|---|---|---|
| RF-05 | Formulario Thymeleaf → `IncidenciaController` → servicios → JPA → PostgreSQL | Parcial como requisito integral: login implementado y reportante tomado de la sesión; la validación del flujo integral en piloto está pendiente |
| RNF-02 | `/usuarios/login` → `UsuarioDetailsService` → `UsuarioRepository` → PostgreSQL | Login y autorización por rol implementados; las cuentas de acceso deben estar en la base de datos recuperada |
| RF-03 | `/mantenimientos` → `MantenimientoController` → `MantenimientoService` → JPA e historial de componentes | Intervenciones y acciones implementadas; el responsable se toma de la cuenta autenticada con rol técnico |
| RF-06 | `/reportes` → `ReporteController` → `ReporteService` y `ExcelExporter` → PostgreSQL y CSV/XLSX/PDF | Filtros por periodo, QR y tipo; mínimo de dos fallas clasificadas por equipo |
| RF-01 / RF-02 | `/api/computadoras/historial?codigoQr=...` → servicio de aplicación → repositorio JPA | Ficha del equipo y cinco incidencias recientes visibles en el registro; cronología web de eventos técnicos implementada; validación integral en piloto pendiente |
| EXT-01 | `/reportes/exportar/{formato}` → `ReporteController` y exportadores CSV/XLSX/PDF | Descargas de recurrencias implementadas; el listado de incidencias no expone exportación |
| Suite | Pruebas unitarias y de integración con MockMvc y PostgreSQL Testcontainers | Los informes locales del 19/11/2026 registran 64 pruebas aprobadas sin fallos, errores ni omisiones; no acreditan un piloto |

La refactorización de paquetes está en curso. La cifra de pruebas refleja los informes locales disponibles en `target/surefire-reports`; los cambios realizados después de esa ejecución requieren una nueva verificación. Las pruebas históricas corresponden a la versión ejecutada en su fecha.

La matriz detallada está en [`Matriz Trazabilidad.md`](Matriz%20Trazabilidad.md). El método, los casos y la evidencia de ejecución están en [`testing/README.md`](testing/README.md).
