# TrazaLab

## Integrantes

- Barrutia Ojeda Mauricio
- Guevara Guerrero Ashley
- Jimenez Chinchay Mateo Sebastian
- Mendoza Sacsa Zamira Luana

TrazaLab registra la trazabilidad técnica de computadoras de laboratorio. La aplicación web permite consultar y registrar incidencias en PostgreSQL y descargar el listado en PDF, Excel o CSV.

## Capacidades disponibles

| Capacidad | Descripción |
|---|---|
| Registro de incidencias | El formulario captura manualmente el QR y la descripción; el servidor fija el usuario reportante de demostración. |
| Consulta de incidencias | Presenta los registros más recientes primero, en páginas de 25 filas. |
| Exportación | Descarga PDF, XLSX o CSV mediante consultas por lotes. |
| Logging de aplicación | Escribe eventos de operación mediante SLF4J y Logback. |

Los paneles de mantenimiento y reportes están en construcción. Sus rutas muestran un estado provisional hasta que se integren sus servicios y consultas.

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
| `static/css`, `static/js` | Contiene variables Bootstrap, estilos e interacciones de la interfaz. |
| `export` | Genera los archivos de incidencias en PDF, Excel y CSV. |

La [arquitectura detallada](docs/Arquitectura.md) distingue código implementado, pantallas provisionales y trabajo pendiente.

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
| Apache POI 5.5.1 | Libros XLSX. |
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
   docker compose --env-file .env -f database/compose.yaml up -d
   ```

4. Arranca TrazaLab:

   ```powershell
   .\database\run.ps1
   ```

5. Abre `http://localhost:8080/incidencias`.

También se puede iniciar directamente con `.\mvnw.cmd spring-boot:run`. Spring Boot carga las variables desde `.env` y conecta con PostgreSQL.

El usuario reportante demo se selecciona mediante `TRAZALAB_DEMO_USER_EMAIL` o usa `ana.torres.demo@trazalab.local` por defecto. La opción de insertar datos demo está desactivada por defecto para no modificar bases existentes; se activa explícitamente con `TRAZALAB_SEED_ENABLED=true` solo en un entorno preparado para ello.

Flyway aplica las migraciones bajo `src/main/resources/db/migration`. Hibernate utiliza `spring.jpa.hibernate.ddl-auto=validate` y no crea ni actualiza tablas. Para una base que ya tenía datos antes de Flyway, sigue [`database/README.md`](database/README.md#migraciones-y-volumenes-existentes); el procedimiento genera un respaldo antes de registrar la línea base y conserva el volumen.

## Exportación

Desde el listado de incidencias se pueden descargar PDF, Excel o CSV. Los archivos incluyen identificador, reportante, QR, descripción, fecha y estado. Se generan por lotes y se transmiten desde el directorio configurado por `TRAZALAB_EXPORT_DIRECTORY`, cuyo valor predeterminado es `evidencias/`.

## Logging

Los eventos funcionales se escriben en `logs/trazalab-app.log`. `APP_EVENT` registra acción, recurso, resultado y, cuando corresponde, referencias o cantidades. No contiene descripciones, nombres, correos ni contraseñas.

## Pruebas

Ejecuta la suite con Docker disponible:

```powershell
.\mvnw.cmd test
```

La verificación del 05/11/2026 ejecutó 11 pruebas con PostgreSQL temporal: **11 aprobadas, 0 fallidas, 0 errores y 0 omitidas**. Incluye render de vistas y registro web con identidad fijada, además de persistencia, paginación, exportación por lotes y comparación de consumo retenido de memoria. Maven deja resultados y logs de prueba en `target/surefire-reports`.

## Alcance y documentación

La interfaz web y la persistencia de incidencias están implementadas. El formulario requiere introducir el QR como texto y funciona en modo de demostración sin login. La consulta de historial individual por equipo, el registro de mantenimientos con componentes, los filtros de reportes y las recurrencias de fallas están pendientes; las pantallas provisionales lo indican.

- [Arquitectura](docs/Arquitectura.md)
- [Matriz de trazabilidad](docs/Matriz%20Trazabilidad.md)
- [Seguridad y límites](docs/Seguridad%20y%20Limites.md)
- [Versiones y fuentes](docs/Versiones%20y%20Fuente.md)
- [Diagrama de arquitectura y trazabilidad](docs/TrazaLab_Arquitectura_Trazabilidad.drawio)
- [Uso y migración de PostgreSQL](database/README.md)

Los archivos del docente dentro de `APFFinal` son referencias de testing. Se documenta la transferencia y ejecución del proyecto en `docs/`; esos materiales no se editan.
