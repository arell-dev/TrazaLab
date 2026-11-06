# OBS-06 — Diagnósticos del editor Java

**Fecha:** 06/11/2026

**Alcance:** avisos del analizador Java y sincronización del proyecto Maven.

## Hallazgos

- El benchmark de exportación declaraba una incidencia local que no usaba.
- El servicio de incidencias expresaba el ordenamiento con nombres de propiedades como texto, por lo que Spring Tools lo marcaba como referencia no segura.
- Tras modificar `pom.xml`, Java Language Support indicó que debía recargarse la configuración Maven del editor.

## Corrección

- El benchmark consume los resultados mediante `Iterator` y mantiene el conteo de filas leídas.
- Los ordenamientos ascendentes y descendentes ahora usan `Sort.TypedSort` y referencias de método de `IncidenciaEntidad`.
- El aviso de `pom.xml` es de sincronización del editor; el comando de recarga es **Java: Reload Projects** en VS Code.

## Verificación

`mvnw -q test` terminó con 18 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas. Testcontainers ejecutó PostgreSQL 16.15 y Flyway aplicó las dos migraciones.
