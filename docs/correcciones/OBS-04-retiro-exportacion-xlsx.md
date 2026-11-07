# OBS-04 — Exportaciones por panel

**Fecha:** 06/11/2026

**Alcance vigente:** Reportes ofrece CSV, Excel (XLSX) y PDF. Incidencias no muestra controles de exportación ni expone rutas de descarga.

## Historial y corrección

- Una corrección anterior retiró exportaciones de Incidencias.
- La PR #9 habilitó XLSX en Reportes mediante Apache POI y `SXSSFWorkbook`; CSV y PDF también quedaron disponibles en ese panel.
- La PR #14 retiró por error XLSX de Reportes al confundirlo con los controles que deben faltar en Incidencias.
- La PR #15 restablece la exportación XLSX en Reportes y conserva Incidencias sin botones de descarga.

## Estado actual

- `/reportes` ofrece descargas CSV, Excel y PDF. Las tres aplican los filtros del periodo, equipo y tipo seleccionados.
- `/incidencias` no ofrece controles de descarga ni rutas `/incidencias/exportar/...`.
- `ExportadorReporteFallasExcel` usa `SXSSFWorkbook` y cierra el libro para liberar sus recursos temporales.
- La dependencia Apache POI permanece porque el XLSX forma parte del reporte.

## Verificación

`RegistroIncidenciaJpaTest` comprueba que la vista de Incidencias no incluya controles de exportación, que Reportes presente CSV/Excel/PDF y que las tres descargas respeten los filtros. El resultado de `clean test` se registra en [`../testing/README.md`](../testing/README.md).
