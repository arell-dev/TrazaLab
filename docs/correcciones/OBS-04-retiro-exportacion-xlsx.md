# OBS-04 — Historial y retiro final de exportación XLSX

**Fecha:** 06/11/2026

**Alcance vigente:** Reportes solo ofrece descargas CSV y PDF. Se retiró XLSX de Reportes y no existe exportación desde Incidencias.

## Historial

- Una corrección inicial retiró XLSX de la aplicación.
- La PR #9 habilitó temporalmente la salida XLSX en Reportes mediante Apache POI; Incidencias siguió sin descargas.
- Por decisión posterior, la PR #14 retira el XLSX de Reportes, el exportador, la dependencia Apache POI y la validación de libros generados. CSV y PDF permanecen en Reportes.

## Corrección vigente

- Reportes conserva únicamente `GET /reportes/exportar/csv` y `GET /reportes/exportar/pdf`.
- El valor `excel` ya no es parte de `FormatoExportacion`; la ruta desconocida se rechaza con HTTP 400.
- La interfaz no muestra una opción Excel.
- Se retiró `ExportadorReporteFallasExcel` y Apache POI del `pom.xml`.
- README, arquitectura, inventario de dependencias, matriz de trazabilidad y pruebas describen el alcance CSV/PDF.
- Se conservan las matrices académicas fuente fuera del repositorio.

## Verificación

`RegistroIncidenciaJpaTest` comprueba las descargas CSV/PDF con los filtros seleccionados, confirma que `/reportes` no muestra la opción Excel y que `/reportes/exportar/excel` devuelve HTTP 400. El resultado vigente de Maven se registra en [`../testing/README.md`](../testing/README.md).
