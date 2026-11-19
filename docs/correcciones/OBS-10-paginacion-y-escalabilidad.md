# OBS-10 · Paginación y medición local

## Observación y cambios

Las vistas de usuarios y reportes materializaban todos sus resultados. Las consultas de disponibilidad repetían conteos y la ficha realizaba consultas adicionales del equipo. Se reorganizaron las consultas de disponibilidad e historial y se incorporó paginación de 20 filas para usuarios y reportes. Los indicadores de reportes se calculan sobre todos los grupos filtrados y las exportaciones conservan el conjunto completo.

## Medición

La comparación se ejecutó el 19/11/2026 en una sola máquina con PostgreSQL 16 desechable, esquema Flyway V1–V7 y Java con `-Xms256m -Xmx1536m`. Se usaron bases sintéticas independientes de 1.000, 10.000 y 100.000 usuarios e incidencias, con la mitad de equipos y una falla por incidencia. El 10 % de incidencias se concentró en un equipo. No se generaron mantenimientos ni componentes para el historial. Las versiones comparadas fueron `d78a4a7`, la refactorización sin paginar y la versión local paginada. Cada endpoint recibió tres solicitudes de calentamiento y 30 solicitudes HTTP autenticadas. Los planes SQL se obtuvieron con `EXPLAIN (ANALYZE, BUFFERS)`.

Con 100.000 registros, la versión de `d78a4a7` obtuvo p50/p95 de 994,952/1.227,614 ms para reportes y 1.925,495/2.173,105 ms para usuarios. Las respuestas fueron de 55.039.119 y 38.685.876 bytes respectivamente. La versión local paginada obtuvo 672,607/716,501 ms y 39.308 bytes para reportes, y 30,935/46,282 ms y 16.709 bytes para usuarios.

En disponibilidad, el p50/p95 de `d78a4a7` fue 68,573/87,251 ms y el de la refactorización sin paginar fue 39,815/58,230 ms. Para historial, fue 82,401/94,395 ms frente a 69,893/96,286 ms. Estas mediciones no demuestran una mejora del p95 de historial.

Los resultados dependen del equipo, de la distribución y de la carga sintética. Los tiempos HTTP incluyen la aplicación y la transferencia de la respuesta; no representan una prueba de concurrencia ni un piloto. Los scripts, muestras y planes se conservaron temporalmente en `%TEMP%/trazalab-bench-4jjsetah`.

Se midieron también las descargas sobre la carga de 100.000 fallas, con un calentamiento y tres solicitudes consecutivas por formato. La mediana y el máximo fueron 447,828/613,834 ms para CSV, 989,264/1.018,895 ms para Excel y 1.232,265/1.241,352 ms para PDF. Los archivos ocuparon 3.858.933, 1.557.787 y 1.063.765 bytes, respectivamente. El pico de memoria del proceso durante toda la secuencia fue 737.677.312 bytes; esta cifra incluye el arranque y los tres formatos y no determina el pico de cada descarga. Tres muestras no permiten estimar un p95 fiable.

CSV, Excel y PDF aún generan la lista completa y el archivo en memoria. La prueba anterior demuestra que los tres formatos respondieron para esta carga secuencial; queda pendiente medir descargas simultáneas y memoria por formato antes de decidir si se requiere procesamiento acotado.

## Verificación funcional

La prueba de integración usa 21 grupos para comprobar el límite por página, indicadores globales, filtros y exportación completa. La suite local posterior a la paginación terminó con 69 pruebas aprobadas y ninguna omitida. En un navegador con una base desechable, las dos páginas de usuarios y reportes mostraron los registros esperados. La validación en laboratorios y dispositivos físicos permanece pendiente.
