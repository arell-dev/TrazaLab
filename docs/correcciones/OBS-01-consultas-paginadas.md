# Corrección OBS-01: consultas y exportaciones paginadas

## Observación

Las consultas de incidencias e historial cargaban listas completas. Los exportadores también recibían todas las incidencias en memoria, lo que podía elevar el uso de memoria al crecer el conjunto. El informe correctivo identifica el riesgo como potencial; no había una medición de rendimiento previa.

## Cambio aplicado

- La lista general y el historial de una computadora ahora aceptan `Pageable` y devuelven `Page`.
- Las consultas conservan un orden determinista: fecha e ID descendentes para el listado, y ascendentes para el historial.
- El tamaño de una página de consulta se limita a 100 registros.
- La consola recorre las páginas sin mantener toda la consulta en memoria.
- La exportación captura el ID máximo y el total inicial. Lee lotes de 250 incidencias, excluye las incidencias creadas después de iniciar el reporte y evita que nuevas filas desplacen los resultados de páginas posteriores.
- CSV escribe cada registro al archivo. Excel usa `SXSSFWorkbook` con una ventana de 100 filas. PDF construye cada página a partir del iterador y utiliza almacenamiento temporal de PDFBox.

## Errores de implementación y corrección

| Error observado | Causa | Corrección |
|---|---|---|
| `mvn test` no compiló Excel porque `configurarHoja` todavía recibía `XSSFSheet`. | El exportador se cambió a `SXSSFWorkbook`, pero quedó un tipo XSSF en un método auxiliar. | El helper ahora recibe `Sheet`, compatible con la hoja de SXSSF. |
| `mvn test` no compiló PDF: el constructor de `PDDocument` no acepta `MemoryUsageSetting` en PDFBox 3.0.8. | Se usó la API de una versión anterior de PDFBox. | Se usa `IOUtils.createTempFileOnlyStreamCache()`, API disponible en la dependencia instalada. |
| La compilación de pruebas no encontró `EstadoIncidencia`. | Faltaba importar el enum al ampliar el fixture de integración. | Se agregó el import y se repitió la suite. |

## Verificación

La suite `RegistroIncidenciaJpaTest` pasa con PostgreSQL de Testcontainers. Comprueba páginas vacías y parciales, desempate por ID con fechas iguales, recorrido del historial, tope de 100 filas y exportación de 251 registros a CSV, Excel y PDF. El caso de exportación añade una incidencia después de capturar el límite inicial y comprueba que el reporte conserve exactamente el conjunto inicial.

Ejecución: `./mvnw.cmd -q test` · 7 pruebas aprobadas · 0 fallos · 0 errores · 0 omitidas.

## Comparación de tiempo y memoria

Una ejecución comparó ambos recorridos sobre las mismas 5 000 incidencias sintéticas, con descripciones de aproximadamente 460 caracteres, en PostgreSQL 16 de Testcontainers:

| Recorrido | Tiempo observado | Heap retenido después de GC |
|---|---:|---:|
| Carga completa y mapeo a DTO | 166,68 ms | 5 119 824 bytes |
| Lectura/exportación por lotes | 448,01 ms | 26 688 bytes |

En esta ejecución, el recorrido por lotes demoró 2,69 veces más y retuvo menos heap después de solicitar GC. La diferencia de tiempo refleja las consultas paginadas adicionales. La medición de memoria no es el pico de memoria; los tiempos y el heap retenido varían entre ejecuciones y no representan un benchmark estadístico ni una garantía para otras máquinas o bases de datos.
