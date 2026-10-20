# PostgreSQL desde la consola del proyecto

Esta guía muestra cómo iniciar la base y abrir `psql` en el contenedor Docker desde PowerShell, ubicado en la raíz del proyecto.

## Iniciar PostgreSQL

Comprueba que `.env` tenga credenciales locales válidas. Luego, en la raíz de TrazaLab, ejecuta:

```powershell
docker compose --env-file .env -f database/compose.yaml up -d
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
- El esquema inicial [`01_trazalab_schema.sql`](01_trazalab_schema.sql) solo se ejecuta al crear el volumen por primera vez.

El flujo completo de la aplicación, las capas JPA y la trazabilidad de RF-05 están documentados en el [README principal](../README.md) y en [Arquitectura](../docs/Arquitectura.md).
