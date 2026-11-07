# Corrección OBS-01: consultas y exportaciones paginadas

> Registro histórico de la implementación del 05/11/2026. Las exportaciones por lotes de incidencias descritas aquí fueron retiradas después. La paginación sigue vigente para el listado e historial; las exportaciones actuales pertenecen a Reportes.

## Observación

Las consultas de incidencias e historial cargaban listas completas. Los exportadores también recibían todas las incidencias en memoria, lo que podía elevar el uso de memoria al crecer el conjunto. El informe correctivo identifica el riesgo como potencial; no había una medición de rendimiento previa.

## Cambio aplicado

- La lista general y el historial de una computadora ahora aceptan `Pageable` y devuelven `Page`.
- Las consultas conservan un orden determinista: fecha e ID descendentes para el listado, y ascendentes para el historial.
- El tamaño de una página de consulta se limita a 100 registros.
- `IncidenciaWebController` presenta la lista en páginas de 25 registros; el servicio admite tamaños de hasta 100.
- En la implementación histórica, la exportación capturaba el ID máximo y el total inicial para leer lotes de 250 incidencias y excluir altas posteriores; esa ruta se retiró al limitar las exportaciones a Reportes.
- CSV, Excel y PDF se generaban por lotes en esa etapa. El XLSX de Reportes se habilitó después mediante PR #9 y se retiró junto con Apache POI en PR #14; actualmente Reportes ofrece CSV y PDF.

## Errores de implementación y corrección

| Error observado | Causa | Corrección |
|---|---|---|
| `mvn test` no compiló Excel porque `configurarHoja` todavía recibía `XSSFSheet`. | El exportador se cambió a `SXSSFWorkbook`, pero quedó un tipo XSSF en un método auxiliar. | El helper ahora recibe `Sheet`, compatible con la hoja de SXSSF. |
| `mvn test` no compiló PDF: el constructor de `PDDocument` no acepta `MemoryUsageSetting` en PDFBox 3.0.8. | Se usó la API de una versión anterior de PDFBox. | Se usa `IOUtils.createTempFileOnlyStreamCache()`, API disponible en la dependencia instalada. |
| La compilación de pruebas no encontró `EstadoIncidencia`. | Faltaba importar el enum al ampliar el fixture de integración. | Se agregó el import y se repitió la suite. |

## Verificación

Las ejecuciones históricas con PostgreSQL de Testcontainers comprobaron páginas vacías y parciales, desempate por ID con fechas iguales, recorrido del historial, tope de 100 filas y exportación de 251 incidencias. La prueba vigente `recorrePorPaginasUnHistorialDeCincoMilIncidencias` recorre 5 000 registros en 50 páginas y valida orden sin duplicados. Actualmente las exportaciones CSV y PDF se verifican desde Reportes; la ruta Excel se rechaza.

Ejecución inicial de la PR #4: `./mvnw.cmd -q test` · 7 pruebas aprobadas · 0 fallos · 0 errores · 0 omitidas. Después de migrar la interfaz a Spring MVC, la suite ampliada se ejecutó de nuevo con 11 pruebas aprobadas el 05/11/2026.

## Comparación de tiempo y memoria

Una ejecución comparó ambos recorridos sobre las mismas 5 000 incidencias sintéticas, con descripciones de aproximadamente 460 caracteres, en PostgreSQL 16 de Testcontainers:

| Recorrido | Tiempo observado | Heap retenido después de GC |
|---|---:|---:|
| Carga completa y mapeo a DTO | 166,68 ms | 5 119 824 bytes |
| Lectura/exportación por lotes | 448,01 ms | 26 688 bytes |

En esta ejecución, el recorrido por lotes demoró 2,69 veces más y retuvo menos heap después de solicitar GC. La diferencia de tiempo refleja las consultas paginadas adicionales. La medición de memoria no es el pico de memoria; los tiempos y el heap retenido varían entre ejecuciones y no representan un benchmark estadístico ni una garantía para otras máquinas o bases de datos.

### Reejecución después de la migración web

El 05/11/2026 se repitió la misma comparación dentro de la suite ampliada, en una ejecución con 5 000 filas de PostgreSQL 16 en Testcontainers:

| Recorrido | Tiempo observado | Heap retenido después de GC |
|---|---:|---:|
| Carga completa y mapeo a DTO | 160,81 ms | 5 116 312 bytes |
| Lectura por lotes | 450,13 ms | 26 680 bytes |

En esta repetición el recorrido por lotes tomó aproximadamente 2,80 veces más y dejó menos heap retenido después de GC. Es una medición puntual, no el pico de memoria ni una garantía de rendimiento.

Estas comparaciones son evidencia histórica de la exportación por lotes de incidencias y no describen el flujo vigente. La suite actual verifica paginación funcional, sin mantener el benchmark temporal dentro de las pruebas.
