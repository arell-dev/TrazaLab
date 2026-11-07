# Matriz de trazabilidad de TrazaLab

Esta matriz documenta la correspondencia verificable entre cada requisito, el código, la persistencia, las pruebas y su estado. Los archivos de APF03 se mantienen como fuentes académicas externas al repositorio; no se conserva una copia Excel dentro del proyecto.

## Estado por requisito

| Requisito | Correspondencia verificable | Estado |
|---|---|---|
| RF-01, historial técnico | `GET /api/computadoras/historial?codigoQr=...` consulta el equipo y devuelve sus cinco incidencias más recientes; `RegistroIncidenciaPersistenteServicio.consultarHistorialComputadora` conserva la consulta paginada. `MantenimientoEntidad` y los componentes guardan sus historiales. | Parcial: ficha breve disponible durante el registro; falta consolidar todos los eventos técnicos. |
| RF-02, identificación por QR | `RegistroIncidenciaFormulario` recibe el código como texto; el servicio resuelve con `ComputadoraJpaRepositorio.findByCodigoQrIgnoreCase` y la API devuelve datos básicos del equipo. | Parcial: verificación manual y ficha de equipo implementadas; lector físico pendiente. |
| RF-03, mantenimiento | `POST /mantenimientos` → `MantenimientoWebController` → `RegistroMantenimientoServicio` → JPA y tablas `computadora_componente` / `mantenimiento_componente`. Valida QR, identidad técnica y falla del mismo equipo para correctivos. | Implementado en modo demo; no cambia el estado de la computadora ni cierra incidencias. |
| RF-04, disponibilidad | El esquema almacena estado y ubicación y define `consultar_disponibilidad`; no existe pantalla funcional. El diseño P01 incluye al docente. | Diseñado. |
| RF-05, registro de incidencia | `GET /api/computadoras/historial` valida el QR antes de avanzar; `POST /incidencias` → `RegistroIncidenciaFormulario` → `IncidenciaWebController` → servicios → JPA → PostgreSQL. Bean Validation protege QR y descripción; el servidor determina la identidad demo. | Parcial como requisito integral: consulta reciente, registro web y persistencia probados; autenticación pendiente. |
| RF-06, recurrencias | `/reportes` → `ReporteWebController` → `ReporteFallasServicio` consulta fallas por equipo y tipo en un periodo de hasta 30 días; permite filtrar por QR/tipo y exportar CSV/XLSX/PDF. Exige 2 casos como mínimo. | Implementado para fallas clasificadas. |
| RNF-01, clasificación de fallas | `RegistroIncidenciaFormulario.tipoFallaId` → `TipoFallaJpaRepositorio` → `FallaEntidad`; Flyway V2 agrega tipos iniciales sin borrar datos. | Parcial: clasificación opcional para mantener compatibilidad con incidencias existentes. |
| RNF-02, acceso por rol | El servicio de registro exige que la identidad configurada exista, esté activa y tenga un rol reportante. La aplicación demo permite acceso anónimo a las rutas. | Parcial; identidad fija de demostración, autenticación y autorización pendientes. |
| RNF-03, trazabilidad del reporte | Incidencias, fallas y mantenimientos almacenan responsable, equipo y fecha; el mantenimiento correctivo se asocia a una falla del mismo equipo. | Parcial; aplicación demo sin inicio de sesión. |
| RE-01, piloto | Alcance de 15 a 20 equipos definido en APF01; no se ha validado ese piloto. | Diseñado. |
| EXT-01, exportación de reportes | `/reportes/exportar/{formato}` → `ReporteWebController` → exportadores CSV/XLSX/PDF, conservando filtros de periodo, QR y tipo. El listado de incidencias no tiene exportaciones. | Implementado para recurrencias; no equivale al historial integral de RF-01. |

## Evidencia comprobada y límites

La ejecución de `./mvnw.cmd -q clean test` terminó con **25 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas** en PostgreSQL temporal mediante Testcontainers (07/11/2026). Incluye consulta reciente por QR, exportación XLSX en Reportes, ausencia de descargas en Incidencias y respuestas centralizadas de errores REST. El detalle por requisito y caso está en [`testing/README.md`](testing/README.md).

Las pruebas corren en una base descartable de Testcontainers; no modifican ni acreditan un piloto con datos reales. Los archivos del docente se consultaron como referencia y no se modificaron.

La guía de mantenimiento del esquema y la línea base local están en [`../database/README.md`](../database/README.md). Los hallazgos y correcciones están en [`correcciones/`](correcciones/) y el plan de pruebas en [`testing/`](testing/).

El [diagrama unificado](TrazaLab_Arquitectura_Trazabilidad.drawio) muestra el flujo actual y las extensiones previstas.
