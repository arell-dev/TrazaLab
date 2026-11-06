# PostgreSQL desde la consola del proyecto

Esta guía muestra cómo iniciar la base definida en el [`compose.yaml`](../compose.yaml) de la raíz y abrir `psql` en el contenedor Docker desde PowerShell, ubicado en la raíz del proyecto.

## Iniciar PostgreSQL

Comprueba que `.env` tenga credenciales locales válidas. Luego, en la raíz de TrazaLab, ejecuta:

```powershell
docker compose --env-file .env up -d
```

Comprueba que el contenedor esté activo:

```powershell
docker ps --filter "name=trazalab-postgres"
```

El contenedor publica PostgreSQL en el puerto `5433` del equipo y se llama `trazalab-postgres`.

## Entrar a la consola interactiva de PostgreSQL

Abre `psql` directamente dentro del contenedor:

```powershell
docker exec -it trazalab-postgres psql -U trazalab_app -d trazalab
```

El prompt cambiará a `trazalab=#`. Ya dentro de `psql`, puedes ejecutar:

```sql
\dt
\d incidencia
SELECT incidencia_id, computadora_id, usuario_id, descripcion, fecha_registro, estado
FROM incidencia
ORDER BY incidencia_id DESC
LIMIT 10;
```

Para consultar junto con el QR y el nombre del usuario:

```sql
SELECT i.incidencia_id,
       c.codigo_qr,
       u.nombres,
       u.apellidos,
       i.descripcion,
       i.fecha_registro,
       i.estado
FROM incidencia AS i
JOIN computadora AS c ON c.computadora_id = i.computadora_id
JOIN usuario AS u ON u.usuario_id = i.usuario_id
ORDER BY i.incidencia_id DESC
LIMIT 10;
```

Sal de `psql` con:

```text
\q
```

## Ejecutar una consulta sin abrir la consola interactiva

Para obtener una salida rápida desde PowerShell:

```powershell
docker exec trazalab-postgres psql -U trazalab_app -d trazalab -c "SELECT incidencia_id, computadora_id, usuario_id, descripcion, fecha_registro, estado FROM incidencia ORDER BY incidencia_id DESC LIMIT 10;"
```

## Si no conecta

- Confirma que Docker Desktop esté iniciado y que `docker ps --filter "name=trazalab-postgres"` muestre el contenedor.
- `POSTGRES_USER` y `POSTGRES_PASSWORD` solo inicializan el rol cuando se crea el volumen por primera vez. Cambiar `.env` por sí solo no actualiza la contraseña de una base ya inicializada.
- Para cambiar la contraseña sin borrar los datos, inicia el contenedor, abre `psql` como arriba y ejecuta `\password trazalab_app`. Escribe la nueva clave cuando `psql` la solicite; después actualiza `SPRING_DATASOURCE_PASSWORD` en `.env` para que coincida.
- No elimines el volumen si necesitas conservar los registros existentes.
- Las migraciones Flyway de `src/main/resources/db/migration` son la fuente ejecutable del esquema. El archivo `01_trazalab_schema.sql` conserva el esquema inicial como referencia; Docker ya no lo ejecuta al crear el volumen.

El flujo completo de la aplicación, las capas JPA y la trazabilidad de RF-05 están documentados en el [README principal](../README.md) y en [Arquitectura](../docs/Arquitectura.md).

## Migraciones y volúmenes existentes

En un volumen nuevo, inicia PostgreSQL y luego la aplicación. Flyway crea el esquema desde `V1__crear_esquema_trazalab.sql`; Hibernate solo valida el mapeo.

Para adoptar Flyway en un volumen anterior, no uses `docker compose down -v`. Ejecuta `./database/baseline-existing.ps1` desde la raíz del proyecto. El script requiere la estructura inicial conocida (14 tablas y 4 vistas), comprueba que Flyway no se haya inicializado antes, crea un respaldo en `ProyectoFinal/_backups` y registra la versión 1 sin volver a ejecutar `V1`. Si una comprobación falla, detente y conserva la base tal como está.

Después de baselinar, inicia la aplicación normalmente. Confirma que `flyway_schema_history` registra la versión 1 y que `spring.jpa.hibernate.ddl-auto=validate` permite arrancar. Las futuras modificaciones deben añadirse en una migración con versión superior; no edites una migración ya aplicada.
