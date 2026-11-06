# OBS-04 — Retiro de exportación XLSX

**Fecha:** 06/11/2026

**Alcance:** aplicación TrazaLab y documentación versionada en este repositorio.

## Hallazgo

La aplicación generaba libros XLSX para las incidencias, incluía Apache POI como dependencia y mantenía una copia Excel de la matriz de trazabilidad dentro de `docs/`. Los paneles de exportación de incidencias y de recurrencias no necesitan una salida XLSX.

## Corrección

- Retirado el exportador XLSX y la dependencia Apache POI.
- Retirado el archivo Excel duplicado de `docs/`; los archivos fuente de APF03 quedan fuera de este cambio.
- Conservadas las descargas PDF y CSV de incidencias y de reportes de recurrencias.
- Actualizadas las pruebas, la matriz Markdown, la arquitectura, el inventario de dependencias y los enlaces de documentación.

## Verificación

`RegistroIncidenciaJpaTest` cubre las descargas PDF/CSV de incidencias y recurrencias, y comprueba que la ruta Excel de reportes no esté disponible. La ejecución actual de Maven y su estado se registran en [`../testing/README.md`](../testing/README.md).
