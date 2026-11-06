# OBS-04 — Exportaciones limitadas a Reportes

**Fecha:** 06/11/2026

**Alcance:** aplicación TrazaLab y documentación versionada en este repositorio.

## Hallazgo

Una corrección anterior retiró XLSX de la aplicación. El alcance vigente requiere CSV, XLSX y PDF solo para reportes; las incidencias no deben ofrecer descargas. La matriz académica fuente permanece fuera del repositorio.

## Corrección

- Retiradas la ruta, el servicio y los exportadores de incidencias para PDF y CSV.
- Habilitado XLSX con Apache POI y `SXSSFWorkbook` en `/reportes/exportar/excel`; el cierre del workbook libera sus recursos temporales.
- Conservadas CSV y PDF en Reportes y añadida la prueba de lectura del libro generado.
- Retirado el archivo Excel duplicado de `docs/`; los archivos fuente de APF03 quedan fuera de este cambio.
- Actualizadas las pruebas, la matriz Markdown, la arquitectura, el inventario de dependencias y los enlaces de documentación.

## Verificación

`RegistroIncidenciaJpaTest` comprueba la ausencia de exportación en incidencias y descarga CSV/XLSX/PDF desde Reportes. La ejecución actual de Maven y su estado se registran en [`../testing/README.md`](../testing/README.md).
