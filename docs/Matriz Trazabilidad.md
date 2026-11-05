# Matriz de trazabilidad de TrazaLab

La referencia académica actualizada es [S10_Matriz_Trazabilidad_y_Transferencia.xlsx](S10_Matriz_Trazabilidad_y_Transferencia.xlsx), copia de `APF03/Matriz de Transferencia y Trazabilidad.xlsx`. Allí cada requisito enlaza entrada, código, persistencia, prueba, evidencia y estado. Las rutas APF son relativas a `ProyectoFinal`.

## Estado por requisito

| Requisito | Correspondencia verificable | Estado |
|---|---|---|
| RF-01, historial técnico | `RegistroIncidenciaPersistenteServicio.consultarHistorialComputadora` consulta el equipo por QR y pagina sus incidencias. Todavía no hay pantalla web para esta ficha. | Parcial: mantenimientos, fallas clasificadas y componentes pendientes. |
| RF-02, identificación por QR | `RegistroIncidenciaFormulario` recibe el código como texto; el servicio lo resuelve mediante `ComputadoraJpaRepositorio.findByCodigoQrIgnoreCase`. | Parcial: consulta manual implementada en registro; lector físico y ficha web pendientes. |
| RF-03, mantenimiento | El esquema tiene `mantenimiento` y `mantenimiento_componente`; la ruta `/mantenimientos` es una pantalla provisional. No hay servicio ni repositorio Java de mantenimiento. | Diseñado; comportamiento funcional pendiente. |
| RF-04, disponibilidad | El esquema almacena estado y ubicación y define `consultar_disponibilidad`; no existe pantalla funcional. El diseño P01 incluye al docente. | Diseñado. |
| RF-05, registro de incidencia | `POST /incidencias` → `RegistroIncidenciaFormulario` → `IncidenciaWebController` → `IncidenciaAplicacionServicio` → `RegistroIncidenciaPersistenteServicio` → `persistence.jpa.springdata` → PostgreSQL. Bean Validation protege QR y descripción; el servidor determina la identidad demo. | Parcial como requisito integral: registro web y persistencia probados; autenticación pendiente. |
| RF-06, recurrencias | La vista SQL `vw_fallas_recurrentes` está definida; `/reportes` es provisional y no existe aún un servicio de recurrencias. | Diseñado. |
| RNF-01, clasificación de fallas | Catálogos y relaciones definidos en SQL; no hay clasificación desde Java. | Diseñado. |
| RNF-02, acceso por rol | El servicio de registro exige que la identidad configurada exista, esté activa y tenga un rol reportante. La aplicación demo permite acceso anónimo a las rutas. | Parcial; identidad fija de demostración, autenticación y autorización pendientes. |
| RNF-03, trazabilidad del reporte | `IncidenciaEntidad` conserva usuario y fecha; el servicio valida la asociación y registra el evento funcional. Intervenciones técnicas pendientes. | Parcial. |
| RE-01, piloto | Alcance de 15 a 20 equipos definido en APF01; no se ha validado ese piloto. | Diseñado. |
| EXT-01, exportación de incidencias | `/incidencias/exportar/{formato}` → `ExportacionWebController` → `ExportacionIncidenciasServicio` → exportadores PDF, Excel y CSV. | Implementado para el listado; no equivale a RF-06. |

## Evidencia comprobada y límites

El 05/11/2026, `./mvnw.cmd -q test` ejecutó 11 pruebas en PostgreSQL temporal mediante Testcontainers: **11 aprobadas, 0 fallidas, 0 errores y 0 omitidas**. Las cuatro verificaciones web comprueban el render del formulario y listado, las rutas provisionales, el rechazo de campos obligatorios y que el usuario enviado desde el cliente no altera la identidad fijada por servidor. Las siete pruebas restantes cubren persistencia, consultas paginadas, exportaciones y lectura por lotes.

Las pruebas corren en una base descartable de Testcontainers; no modifican ni acreditan un piloto con datos reales. Los paneles de mantenimiento y reportes muestran su estado provisional y no se cuentan como funcionalidad ejecutada.

La guía de mantenimiento del esquema y la línea base local están en [`../database/README.md`](../database/README.md). La documentación de errores de esta refactorización está en [`correcciones/OBS-02-arquitectura-web.md`](correcciones/OBS-02-arquitectura-web.md).

El [diagrama unificado](TrazaLab_Arquitectura_Trazabilidad.drawio) muestra el flujo actual y las extensiones previstas.
