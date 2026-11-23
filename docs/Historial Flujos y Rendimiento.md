# Historial de Flujos y Rendimiento

Los números de las entradas conservan su identificador original; el contenido describe decisiones y resultados de esa etapa.

## 01. Consultas y exportaciones paginadas

> Registro histórico de la implementación del 05/11/2026. Las exportaciones por lotes de incidencias descritas aquí fueron retiradas después. La paginación sigue vigente para el listado e historial; las exportaciones actuales pertenecen a Reportes.

### Observación

Las consultas de incidencias e historial cargaban listas completas. Los exportadores también recibían todas las incidencias en memoria, lo que podía elevar el uso de memoria al crecer el conjunto. El informe correctivo identifica el riesgo como potencial; no había una medición de rendimiento previa.

### Cambio aplicado

- La lista general y el historial de una computadora ahora aceptan `Pageable` y devuelven `Page`.
- Las consultas conservan un orden determinista: fecha e ID descendentes para el listado, y ascendentes para el historial.
- El tamaño de una página de consulta se limita a 100 registros.
- `IncidenciaWebController` presenta la lista en páginas de 25 registros; el servicio admite tamaños de hasta 100.
- En la implementación histórica, la exportación capturaba el ID máximo y el total inicial para leer lotes de 250 incidencias y excluir altas posteriores; esa ruta se retiró al limitar las exportaciones a Reportes.
- CSV, Excel y PDF se generaban por lotes en esa etapa. La exportación XLSX vigente corresponde al informe de fallas recurrentes.

### Errores de implementación y corrección

| Error observado | Causa | Corrección |
|---|---|---|
| `mvn test` no compiló Excel porque `configurarHoja` todavía recibía `XSSFSheet`. | El exportador se cambió a `SXSSFWorkbook`, pero quedó un tipo XSSF en un método auxiliar. | El helper ahora recibe `Sheet`, compatible con la hoja de SXSSF. |
| `mvn test` no compiló PDF: el constructor de `PDDocument` no acepta `MemoryUsageSetting` en PDFBox 3.0.8. | Se usó la API de una versión anterior de PDFBox. | Se usa `IOUtils.createTempFileOnlyStreamCache()`, API disponible en la dependencia instalada. |
| La compilación de pruebas no encontró `EstadoIncidencia`. | Faltaba importar el enum al ampliar el fixture de integración. | Se agregó el import y se repitió la suite. |

### Verificación

Las ejecuciones históricas con PostgreSQL de Testcontainers comprobaron páginas vacías y parciales, desempate por ID con fechas iguales, recorrido del historial, tope de 100 filas y exportación de 251 incidencias. La prueba vigente `recorrePorPaginasUnHistorialDeCincoMilIncidencias` recorre 5 000 registros en 50 páginas y valida orden sin duplicados; las exportaciones CSV, XLSX y PDF se verifican desde Reportes.

Ejecución inicial de la PR #4: `./mvnw.cmd -q test` · 7 pruebas aprobadas · 0 fallos · 0 errores · 0 omitidas. Después de migrar la interfaz a Spring MVC, la suite ampliada se ejecutó de nuevo con 11 pruebas aprobadas el 05/11/2026.

### Comparación de tiempo y memoria

Una ejecución comparó ambos recorridos sobre las mismas 5 000 incidencias sintéticas, con descripciones de aproximadamente 460 caracteres, en PostgreSQL 16 de Testcontainers:

| Recorrido | Tiempo observado | Heap retenido después de GC |
|---|---:|---:|
| Carga completa y mapeo a DTO | 166,68 ms | 5 119 824 bytes |
| Lectura/exportación por lotes | 448,01 ms | 26 688 bytes |

En esta ejecución, el recorrido por lotes demoró 2,69 veces más y retuvo menos heap después de solicitar GC. La diferencia de tiempo refleja las consultas paginadas adicionales. La medición de memoria no es el pico de memoria; los tiempos y el heap retenido varían entre ejecuciones y no representan un benchmark estadístico ni una garantía para otras máquinas o bases de datos.

#### Reejecución después de la migración web

El 05/11/2026 se repitió la misma comparación dentro de la suite ampliada, en una ejecución con 5 000 filas de PostgreSQL 16 en Testcontainers:

| Recorrido | Tiempo observado | Heap retenido después de GC |
|---|---:|---:|
| Carga completa y mapeo a DTO | 160,81 ms | 5 116 312 bytes |
| Lectura por lotes | 450,13 ms | 26 680 bytes |

En esta repetición el recorrido por lotes tomó aproximadamente 2,80 veces más y dejó menos heap retenido después de GC. Es una medición puntual, no el pico de memoria ni una garantía de rendimiento.

Estas comparaciones son evidencia histórica de la exportación por lotes de incidencias y no describen el flujo vigente. La suite actual verifica paginación funcional, sin mantener el benchmark temporal dentro de las pruebas.

## 03. Reglas de mantenimiento y ventana de recurrencia

**Fecha:** 05/11/2026
**Rama de implementación:** `feat/mantenimientos-reportes`
**Estado:** corregido y verificado con 17 pruebas aprobadas en PostgreSQL Testcontainers.

### Hallazgos durante la ejecución

| Causa observada | Impacto | Corrección aplicada |
|---|---|---|
| El trigger `fn_validar_mantenimiento()` exige que la cuenta técnica esté activa y tenga rol de Técnico de Soporte o Técnico de Laboratorio. La cuenta demo reportante tenía rol Docente. | PostgreSQL rechazó el primer guardado de mantenimiento; no se debilitó la regla de base de datos. | Se separó la propiedad de identidad técnica demo (`TRAZALAB_DEMO_TECH_EMAIL`), se valida el rol en el servicio y el sembrado opcional crea una cuenta técnica demo si no existe. La identidad se sigue determinando en servidor. |
| El trigger exige asociar una falla válida a todo mantenimiento correctivo y que esa falla pertenezca al mismo equipo. | El primer intento de probar un mantenimiento correctivo sin asociación fue rechazado correctamente por PostgreSQL. | El formulario ofrece fallas clasificadas recientes y el servicio valida la coincidencia de equipo antes de guardar. |
| El reloj usaba UTC para construir `TIMESTAMP` locales mientras el periodo del reporte se tomaba en Lima. | Cerca de medianoche una falla podía quedar en el día siguiente y excluirse del reporte por fecha. | El bean `Clock` usa `America/Lima`, el huso definido para fechas operativas y JPA. La fecha de incidencia continúa como `Instant`. |
| La prueba MockMvc añadida llamó métodos que lanzan `Exception` sin declararla; además, esperaba dos resultados aunque los dos casos forman un solo grupo recurrente. | Maven detuvo la ejecución durante `testCompile`; la suite no llegó a ejecutarse en ese intento. | La prueba declara `throws Exception`, conserva la frecuencia de dos casos y espera un grupo («1 resultado»). El panel también pluraliza el contador. La ejecución posterior aprobó 16/16. |
| La exportación PDF escribía cada recurrencia en una sola línea sin medir el ancho de sus campos. | Códigos QR, series o nombres largos podían quedar recortados fuera del borde de la página. | Se ajustan las filas al ancho útil, se repite el encabezado al paginar y una prueba verifica las posiciones del texto en una fila extensa. La suite final aprobó 17/17. |

### Seguimiento de autenticación y sembrado · 16/11/2026

La referencia a `TRAZALAB_DEMO_TECH_EMAIL` en el hallazgo anterior describe la configuración histórica de esa fecha. Actualmente, el inicio de sesión consulta cuentas activas, hash BCrypt y rol en PostgreSQL. Incidencias y mantenimientos se atribuyen a la cuenta autenticada. Las cuentas de acceso deben existir en la base recuperada. Las pruebas indicadas en esta observación son históricas.

### Trazabilidad

- Servicio de mantenimiento: `RegistroMantenimientoServicio` (nombre histórico; consultar [`trazabilidad`](../src/main/java/com/utp/trazalab/trazabilidad)).
- Servicios web: `MantenimientoWebController` (nombre histórico; ver [`MantenimientoController`](../src/main/java/com/utp/trazalab/trazabilidad/controller/MantenimientoController.java)) y `ReporteWebController` (nombre histórico; ver [`ReporteController`](../src/main/java/com/utp/trazalab/reportes/controller/ReporteController.java)).
- Migración de catálogo: [`V2__catalogo_tipos_falla.sql`](../src/main/resources/db/migration/V2__catalogo_tipos_falla.sql).
- Casos y ejecución: [`testing/README.md`](testing/README.md) y las [pruebas de integración](../src/test/java/com/utp/trazalab/integration/), que sustituyen a la clase histórica `RegistroIncidenciaJpaTest`.

## 04. Exportaciones por panel

**Fecha:** 06/11/2026

**Alcance vigente:** Reportes ofrece CSV, Excel (XLSX) y PDF. Incidencias no muestra controles de exportación ni expone rutas de descarga.

### Historial y corrección

- Una corrección anterior retiró exportaciones de Incidencias.
- La PR #9 habilitó XLSX en Reportes mediante Apache POI y `SXSSFWorkbook`; CSV y PDF también quedaron disponibles en ese panel.
- La PR #14 retiró por error XLSX de Reportes al confundirlo con los controles que deben faltar en Incidencias.
- La PR #15 restablece la exportación XLSX en Reportes y conserva Incidencias sin botones de descarga.

### Estado actual

- `/reportes` ofrece descargas CSV, Excel y PDF. Las tres aplican los filtros del periodo, equipo y tipo seleccionados.
- `/incidencias` no ofrece controles de descarga ni rutas `/incidencias/exportar/...`.
- `ExportadorReporteFallasExcel` usa `SXSSFWorkbook` y cierra el libro para liberar sus recursos temporales.
- La dependencia Apache POI permanece porque el XLSX forma parte del reporte.

### Verificación

`RegistroIncidenciaJpaTest` comprueba que la vista de Incidencias no incluya controles de exportación, que Reportes presente CSV/Excel/PDF y que las tres descargas respeten los filtros. El resultado de `clean test` se registra en [`testing/README.md`](testing/README.md).

## 09. Diseño adaptable para tabletas

### Observación

En una ventana de 768 px, la navegación lateral permanecía fija porque el breakpoint cubría solo pantallas menores a 768 px. El espacio restante comprimía los campos de Mantenimientos y las columnas de Incidencias.

### Corrección

Se extendió el breakpoint de navegación adaptable hasta 991.98 px. En ese rango, la navegación pasa a la barra inferior y el contenido utiliza el ancho disponible.

### Verificación

- Se revisaron Incidencias, Mantenimientos y Reportes en el navegador local con una ventana de 768 px.
- La consulta QR de `PC-001` mostró los datos del equipo y sus cinco incidencias recientes.
- El menú de Reportes conserva CSV, Excel y PDF; Incidencias no muestra controles de exportación.
- `mvnw.cmd -q test` finalizó correctamente.

## 10. Paginación y medición local

### Observación y cambios

Las vistas de usuarios y reportes materializaban todos sus resultados. Las consultas de disponibilidad repetían conteos y la ficha realizaba consultas adicionales del equipo. Se reorganizaron las consultas de disponibilidad e historial y se incorporó paginación de 20 filas para usuarios y reportes. Los indicadores de reportes se calculan sobre todos los grupos filtrados y las exportaciones conservan el conjunto completo.

### Medición

La comparación se ejecutó el 19/11/2026 en una sola máquina con PostgreSQL 16 desechable, esquema Flyway V1–V7 y Java con `-Xms256m -Xmx1536m`. Se usaron bases sintéticas independientes de 1.000, 10.000 y 100.000 usuarios e incidencias, con la mitad de equipos y una falla por incidencia. El 10 % de incidencias se concentró en un equipo. No se generaron mantenimientos ni componentes para el historial. Las versiones comparadas fueron `d78a4a7`, la refactorización sin paginar y la versión local paginada. Cada endpoint recibió tres solicitudes de calentamiento y 30 solicitudes HTTP autenticadas. Los planes SQL se obtuvieron con `EXPLAIN (ANALYZE, BUFFERS)`.

Con 100.000 registros, la versión de `d78a4a7` obtuvo p50/p95 de 994,952/1.227,614 ms para reportes y 1.925,495/2.173,105 ms para usuarios. Las respuestas fueron de 55.039.119 y 38.685.876 bytes respectivamente. La versión local paginada obtuvo 672,607/716,501 ms y 39.308 bytes para reportes, y 30,935/46,282 ms y 16.709 bytes para usuarios.

En disponibilidad, el p50/p95 de `d78a4a7` fue 68,573/87,251 ms y el de la refactorización sin paginar fue 39,815/58,230 ms. Para historial, fue 82,401/94,395 ms frente a 69,893/96,286 ms. Estas mediciones no demuestran una mejora del p95 de historial.

Los resultados dependen del equipo, de la distribución y de la carga sintética. Los tiempos HTTP incluyen la aplicación y la transferencia de la respuesta; no representan una prueba de concurrencia ni un piloto. Los scripts, muestras y planes se conservaron temporalmente en `%TEMP%/trazalab-bench-4jjsetah`.

Se midieron también las descargas sobre la carga de 100.000 fallas, con un calentamiento y tres solicitudes consecutivas por formato. La mediana y el máximo fueron 447,828/613,834 ms para CSV, 989,264/1.018,895 ms para Excel y 1.232,265/1.241,352 ms para PDF. Los archivos ocuparon 3.858.933, 1.557.787 y 1.063.765 bytes, respectivamente. El pico de memoria del proceso durante toda la secuencia fue 737.677.312 bytes; esta cifra incluye el arranque y los tres formatos y no determina el pico de cada descarga. Tres muestras no permiten estimar un p95 fiable.

Se realizaron además cuatro descargas simultáneas de cada formato, con un proceso Java nuevo por formato y el mismo conjunto de datos. Las 12 respuestas fueron HTTP 200. Los tiempos oscilaron entre 1.567,24 y 1.579,88 ms para CSV, entre 2.604,43 y 2.607,08 ms para Excel, y entre 2.506,82 y 2.527,30 ms para PDF. El pico de memoria residente del proceso fue 918.585.344 bytes para CSV, 723.505.152 para Excel y 693.481.472 para PDF. Cada pico incluye el arranque y las cuatro solicitudes; no equivale a memoria por descarga ni a memoria de la base de datos. Esta ejecución no establece un límite de capacidad ni un comportamiento en producción.

CSV, Excel y PDF generan la lista completa y el archivo en memoria. La medición demuestra la respuesta para esta carga y concurrencia en el entorno indicado. El uso de memoria debe reevaluarse con el volumen y los recursos del despliegue académico antes de decidir si requiere procesamiento acotado.

### Verificación funcional

La prueba de integración usa 21 grupos para comprobar el límite por página, indicadores globales, filtros y exportación completa. La suite local posterior a la paginación terminó con 69 pruebas aprobadas y ninguna omitida. En un navegador con una base desechable, las dos páginas de usuarios y reportes mostraron los registros esperados. La validación en laboratorios y dispositivos físicos permanece pendiente.
