# Arquitectura de TrazaLab

El diagrama editable actualizado está en [`TrazaLab_Arquitectura_Trazabilidad.drawio`](TrazaLab_Arquitectura_Trazabilidad.drawio). Describe el flujo web activo y marca como futuros los módulos que todavía no tienen lógica de producción.

## Criterio de diseño

La aplicación sigue una estructura MVC con servicios de aplicación y persistencia JPA organizada, tomando como referencia la estructura de Opalina. Los controladores HTTP se mantienen en `controller`; no se agrega una capa separada de «Adaptador Web». Las entidades, conversores y repositorios Spring Data se ubican en `persistence.jpa`.

La interfaz actual está construida con Spring MVC, Thymeleaf y Bootstrap 5.3.8. Los fragmentos compartidos están en `templates/fragments`, las pantallas en carpetas por módulo y los recursos del cliente en `static/css` y `static/js`.

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

La consulta web muestra incidencias por páginas de hasta 25 registros; el servicio mantiene su límite máximo de 100. Las exportaciones se descargan desde la misma pantalla y usan los exportadores existentes por lotes. Los archivos se transmiten desde disco como recursos, sin cargar todo el archivo en memoria.

`IncidenciaAplicacionServicio` y `ExportacionIncidenciasServicio` registran los eventos mediante `LoggingAplicacion`. `Slf4jLoggingAplicacion` escribe `APP_EVENT` con SLF4J y Logback; no registra descripciones, nombres ni correos.

## Distribución de módulos

```text
com.utp.trazalab
├── config                  Reloj y configuración web / seguridad
├── controller              Controladores MVC HTTP
├── domain.enums            Estados del dominio sin anotaciones JPA
├── dto                     Formularios y datos de entrada / salida
├── export                  Exportación PDF, XLSX y CSV
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
└── modulos/                Pantallas provisionales para módulos aún pendientes
static/
├── css/                    Variables de Bootstrap y estilos de aplicación
└── js/                     Interacciones pequeñas de la interfaz
```

## Límites y estado funcional

| Módulo | Responsabilidad | Estado actual |
|---|---|---|
| `controller` | Recibir solicitudes HTTP, validar formularios y devolver vistas o archivos | Implementado para incidencias y exportación |
| `dto` | Separar datos de formulario de la identidad y de las entidades persistentes | Implementado para incidencias |
| `service` | Aplicar reglas de negocio, transacciones y registro funcional | Implementado para incidencias |
| `persistence.jpa.entity` | Mapear entidades del esquema existente | Implementado para rol, usuario, ubicación, computadora e incidencia |
| `persistence.jpa.springdata` | Consultar y guardar entidades con Spring Data JPA | Implementado para el flujo de incidencias |
| `export` | Generar archivos PDF, Excel y CSV por lotes | Implementado para el listado de incidencias |
| `mantenimientos` | Registrar intervenciones y componentes sin cerrar incidencias ni cambiar el estado de computadora | Pantalla provisional; lógica pendiente |
| `reportes` | Aplicar filtros y producir recurrencias de fallas | Pantalla provisional; lógica pendiente |
| Integración con recolector C++ | Recibir información de la aplicación independiente | Diseñado; contrato pendiente |

La aplicación web de esta etapa es de demostración: Spring Security conserva CSRF y permite las rutas sin autenticación; no se implementan login, sesión ni autorización de usuario. `trazalab.demo.usuario-correo` fija la identidad reportante del servidor. La carga de datos de demostración está desactivada por defecto para preservar el contenido existente.

## Persistencia y ejecución

- PostgreSQL es el almacenamiento persistente; no se usa un repositorio en memoria.
- Hibernate valida las entidades contra el esquema con `spring.jpa.hibernate.ddl-auto=validate`; no crea ni actualiza tablas.
- Flyway aplica migraciones SQL inmutables desde `src/main/resources/db/migration`.
- `spring.jpa.open-in-view=false` evita mantener sesiones JPA abiertas al renderizar vistas.
- Las credenciales locales se cargan desde `.env`, excluido de Git.
- La línea base de un volumen preexistente se registra solo después de validar el esquema y generar un respaldo. No se activa `baseline-on-migrate` de forma general.
- La propiedad `trazalab.seed.enabled` queda en `false` por defecto. No se elimina ni reinicializa el volumen local.

Para el arranque local y la guía de base de datos, consulta [`README.md`](../README.md) y [`database/README.md`](../database/README.md).

## Trazabilidad y pruebas

| Elemento | Correspondencia | Estado |
|---|---|---|
| RF-05 | Formulario Thymeleaf → `IncidenciaWebController` → servicios → JPA → PostgreSQL | Parcial como requisito integral: registro web funcional en modo demo; autenticación pendiente |
| RF-01 / RF-02 | Servicio de historial por QR escrito como texto | Consulta web de historial pendiente; lector físico no implementado |
| EXT-01 | `ExportacionWebController` y exportadores PDF, XLSX y CSV | Descarga del listado implementada; no equivale al análisis RF-06 |
| Registro web | `RegistroIncidenciaJpaTest` con MockMvc y PostgreSQL Testcontainers | 4 verificaciones web y 7 de persistencia aprobadas en la ejecución registrada el 05/11/2026 |

La matriz detallada está en [`Matriz Trazabilidad.md`](Matriz%20Trazabilidad.md). Las pruebas documentan tanto el flujo web ejecutado como los módulos que siguen diseñados o pendientes.
