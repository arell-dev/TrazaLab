# Arquitectura de TrazaLab

El diagrama editable actualizado está en [`TrazaLab_Arquitectura_Trazabilidad.drawio`](TrazaLab_Arquitectura_Trazabilidad.drawio). Describe los flujos web activos y deja como futuro el contrato con el recolector C++.

## Criterio de diseño

La aplicación sigue una estructura MVC con servicios de aplicación y persistencia JPA organizada, tomando como referencia la estructura de Opalina. Los controladores de páginas y formularios están en `controller`; los controladores REST están en `controller/api`. No se agrega una capa separada de «Adaptador Web». Las entidades, conversores y repositorios Spring Data se ubican en `persistence.jpa`.

Los DTO permanecen agrupados en `dto`, con formularios y respuestas planas por caso de uso. `exception` contiene excepciones de recursos inexistentes y conflictos de negocio, además de `GlobalExceptionHandler`, que limita las respuestas RFC 9457 `ProblemDetail` al paquete REST. Los controladores MVC mantienen sus mensajes y validaciones de formulario.

La interfaz actual está construida con Spring MVC, Thymeleaf y Bootstrap 5.3.8. Los fragmentos compartidos están en `templates/fragments`, las pantallas en carpetas por módulo y los recursos del cliente en `static/css` y `static/js`.

El esquema PostgreSQL se versiona con migraciones Flyway en `src/main/resources/db/migration`, mientras Hibernate valida el mapeo. El [`compose.yaml`](../compose.yaml) está en la raíz para levantar la base desde el proyecto; `database/` conserva la guía operativa y el procedimiento de adopción para volúmenes existentes.

## Flujo web implementado

```text
Navegador
  → Thymeleaf / Bootstrap
  → IncidenciaWebController
  → RegistroIncidenciaFormulario + Bean Validation
  → IncidenciaAplicacionServicio (registro de eventos)
  → RegistroIncidenciaPersistenteServicio (reglas y transacción)
  → persistence.jpa.springdata
  → Hibernate / JPA
  → PostgreSQL
```

`RegistroIncidenciaFormulario` solo recibe el código QR y la descripción. El controlador consulta el usuario demo configurado y construye el DTO de aplicación; no acepta un `usuarioId` enviado desde el navegador. El servicio valida que el usuario esté activo y tenga un rol reportante, resuelve el QR y guarda la incidencia dentro de una transacción.

La consulta web muestra incidencias por páginas de hasta 25 registros; el servicio mantiene su límite máximo de 100. La pantalla de incidencias no ofrece exportaciones. Los archivos CSV, XLSX y PDF se generan únicamente desde Reportes y respetan los filtros activos.

`IncidenciaAplicacionServicio` registra los eventos mediante `LoggingAplicacion`. `Slf4jLoggingAplicacion` escribe `APP_EVENT` con SLF4J y Logback; no registra descripciones, nombres ni correos.

## Distribución de módulos

```text
com.utp.trazalab
├── config                  Reloj y configuración web / seguridad
├── controller              Controladores de páginas y formularios MVC
│   └── api                 Controladores REST
├── domain.enums            Estados del dominio sin anotaciones JPA
├── dto                     Formularios y datos de entrada / salida
├── exception               Errores de dominio y respuestas de error REST
├── export                  Exportador Excel de informes
├── logging                 Contrato y registro de eventos de aplicación
├── persistence.jpa
│   ├── converter           Conversión entre estados y columnas SQL
│   ├── entity              Entidades mapeadas al esquema PostgreSQL
│   └── springdata          Interfaces Spring Data JPA
└── service                 Casos de uso, reglas y transacciones
```

Los recursos web están organizados así:

```text
templates/
├── fragments/              Encabezado, navegación, mensajes y scripts
├── incidencias/            Consulta y formulario de incidencias
├── mantenimientos/         Registro de intervenciones y componentes
└── reportes/               Filtros y resultados de recurrencias
static/
├── css/                    colores.css (tokens y Bootstrap) y componentes.css
└── js/                     Interacciones pequeñas de la interfaz
```

## Límites y estado funcional

| Módulo | Responsabilidad | Estado actual |
|---|---|---|
| `controller` | Recibir solicitudes MVC, validar formularios y devolver vistas o archivos | Incidencias, mantenimientos y reportes |
| `controller.api` | Publicar endpoints REST y entregar respuestas JSON | Comprobación de salud de PostgreSQL (`/api/health`) |
| `dto` | Separar datos de formulario de la identidad y de las entidades persistentes | Formularios, fallas recurrentes y resultados de mantenimiento |
| `exception` | Clasificar errores REST y construir respuestas `ProblemDetail` | Validación 400, recurso 404 y conflictos 409; acotado a `controller.api` |
| `service` | Aplicar reglas de negocio, transacciones y consultas | Incidencias, clasificación, mantenimientos y recurrencias |
| `persistence.jpa.entity` | Mapear entidades del esquema existente | Equipos, incidencias, fallas, mantenimientos, componentes, catálogos y usuarios |
| `persistence.jpa.springdata` | Consultar y guardar entidades con Spring Data JPA | Flujos web activos |
| `export` | Generar XLSX del informe de recurrencias | Reportes: CSV, XLSX y PDF; incidencias: sin exportaciones |
| `mantenimientos` | Registrar intervenciones y componentes sin cerrar incidencias ni cambiar el estado de computadora | Implementado; preserva asignaciones temporales de componentes |
| `reportes` | Filtrar y exportar recurrencias por periodo, equipo y tipo | Implementado; CSV, XLSX y PDF; dos o más fallas clasificadas del mismo tipo por equipo |
| Integración con recolector C++ | Recibir información de la aplicación independiente | Diseñado; contrato pendiente |

La aplicación web de esta etapa es de demostración: Spring Security conserva CSRF y permite las rutas sin autenticación; no se implementan login ni sesión. `trazalab.demo.usuario-correo` fija la identidad reportante y `trazalab.demo.mantenimiento-correo` fija la identidad técnica del servidor. La base exige que el responsable técnico esté activo y tenga rol técnico. La carga de datos de demostración está desactivada por defecto para preservar el contenido existente.

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

Las entidades principales se guardan con Spring Data JPA. `RegistroMantenimientoServicio` usa `JdbcTemplate` en la misma transacción para el historial temporal de asignaciones y la relación de acciones de componentes. `ReporteFallasServicio` usa una consulta SQL agrupada y parametrizada para contar fallas y filtrar periodo, equipo y tipo.

Para el arranque local y la guía de base de datos, consulta [`README.md`](../README.md) y [`database/README.md`](../database/README.md).

## Trazabilidad y pruebas

| Elemento | Correspondencia | Estado |
|---|---|---|
| RF-05 | Formulario Thymeleaf → `IncidenciaWebController` → servicios → JPA → PostgreSQL | Parcial como requisito integral: registro web funcional en modo demo; autenticación pendiente |
| RF-03 | `/mantenimientos` → `MantenimientoWebController` → `RegistroMantenimientoServicio` → JPA e historial de componentes | Intervenciones y acciones de componentes implementadas; identidad demo con rol técnico |
| RF-06 | `/reportes` → `ReporteWebController` → `ReporteFallasServicio` y `ExportadorReporteFallasExcel` → PostgreSQL y CSV/XLSX/PDF | Filtros por periodo, QR y tipo; mínimo de dos fallas clasificadas por equipo |
| RF-01 / RF-02 | Servicio de historial por QR escrito como texto | Consulta web de historial pendiente; lector físico no implementado |
| EXT-01 | `/reportes/exportar/{formato}` → `ReporteWebController` y exportadores CSV/XLSX/PDF | Descargas de recurrencias implementadas; el listado de incidencias no expone exportación |
| Suite | `RegistroIncidenciaJpaTest` con MockMvc y PostgreSQL Testcontainers | 23 pruebas aprobadas el 06/11/2026; incluye mantenimiento, componentes, recurrencias, exportación y API de salud |

La matriz detallada está en [`Matriz Trazabilidad.md`](Matriz%20Trazabilidad.md). El método, los casos y la evidencia de ejecución están en [`testing/README.md`](testing/README.md).
