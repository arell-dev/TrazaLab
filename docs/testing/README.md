# Trazabilidad de pruebas de TrazaLab

## Referencia del curso

La guía se elaboró revisando `Ejemplo docente/S13/TestLab_Semana13_COMPLETO/TestLab.Semana13/README.md` y sus carpetas `REFERENCE` y `START`. Los materiales originales del docente se mantienen sin cambios.

El laboratorio define la cadena `requisito → caso de prueba → ejecución → resultado → defecto/corrección → evidencia`. La versión `REFERENCE` hace explícitos los resultados y efectos laterales: ante horario ocupado verifica la excepción y que no se confirme; ante un caso válido verifica el código, estado y una confirmación; ante una especialidad distinta comprueba que no cambie el DAO. También usa una fixture con DAOs falsos para aislar las reglas de servicio. Su integración opcional con PostgreSQL requiere habilitarla por variable de entorno.

La versión `START` contiene pruebas más compactas y menos evidencia sobre efectos laterales. Por ejemplo, comprueba que se lance una excepción ante horario ocupado, pero no comprueba que el DAO no confirme una reserva. TrazaLab adopta las verificaciones de resultado y no mutación observables en `REFERENCE`, con PostgreSQL real temporal en vez de simular el límite de persistencia.

## Ejecución

Desde la raíz de `trazalab`, con Docker Desktop activo:

```powershell
.\mvnw.cmd -q test
```

La suite de integración está organizada por flujo en `EquipoIntegracionTest`, `IncidenciaIntegracionTest`, `MantenimientoIntegracionTest`, `ReporteIntegracionTest`, `UsuarioIntegracionTest` y `SistemaIntegracionTest`. Estas clases comparten `BaseIntegracion`, que configura PostgreSQL 16 desechable con Testcontainers, Flyway, validación del mapeo de Hibernate y MockMvc. Las pruebas de concurrencia tienen clases propias y `ApiExceptionHandlerTest` verifica los estados y cuerpos `ProblemDetail`.

El 19/11/2026, `./mvnw.cmd clean verify` terminó con **64 pruebas aprobadas, 0 fallos, 0 errores y 0 omitidas**. De ellas, 50 corresponden a las seis clases de integración por flujo; las otras 14 abarcan las pruebas de excepciones, health, disponibilidad y concurrencia. Se cotejaron los 48 casos de integración existentes en el punto de partida con los casos divididos y se conservaron sin duplicados. Los resultados están en `target/surefire-reports` y corresponden al estado local probado.

Antes de dividirse por flujo, la clase `RegistroIncidenciaJpaTest` reunía los casos de integración. En esa estructura, la ejecución histórica del 07/11/2026 terminó con **25 pruebas aprobadas**; la del 17/11/2026, con **41 pruebas aprobadas**; y la suite de la PR #21, con **50 pruebas aprobadas** el 18/11/2026. Después se restauró V7 al SQL ya aplicado para conservar su checksum; la verificación posterior terminó con **49 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas**. El 19/11/2026 se registraron **64 pruebas aprobadas** en la estructura anterior a la división. Los resultados históricos corresponden a esas versiones y se conservan como evidencia de sus ejecuciones.

La prueba V5→V7 verifica que `PC-001` ubicado en `LAB-01` pase a `A0301` y que otro equipo en `LAB-01` permanezca allí. En la base local, Flyway validó el esquema en v7 sin ejecutar migraciones y la aplicación arrancó con `/api/health` respondiendo correctamente. Las pruebas usan PostgreSQL temporal y no acreditan un piloto real.

La suite también contiene pruebas para login correcto con una cuenta insertada en PostgreSQL, contraseña incorrecta, cuenta inactiva, redirección de usuarios anónimos y autorización de la gestión de usuarios por rol. La configuración de prueba crea el hash BCrypt en la base temporal; no configura credenciales administrativas mediante propiedades. Las cuentas utilizadas por estas pruebas se insertan en una base temporal específica para cada ejecución.

Los resultados por clase quedan en `target/surefire-reports`. No se informa un porcentaje JaCoCo porque el proyecto aún no configura ese agente o informe.

## Matriz de ejecución

| Requisito / regla | Caso automatizado | Verificación principal | Estado |
|---|---|---|---|
| RF-05, registro de incidencia | `registraYRecuperaLaIncidenciaEnPostgresql` | Inserta y consulta la incidencia, equipo y reportante en PostgreSQL. | Ejecutado |
| RF-05, validación | `rechazaDescripcionVaciaSinGuardar` y `mantieneElFormularioYValidaLosCamposObligatorios` | Rechaza entradas inválidas sin persistir y devuelve errores al formulario. | Ejecutado |
| RF-05, recurso inexistente | `mantieneElFormularioCuandoElCodigoQrNoExiste` | Devuelve el error al formulario y confirma que no persiste la incidencia. | Ejecutado |
| RF-01 / RF-02, QR válido | `consultaEquipoYUltimasCincoIncidenciasPorCodigoQr` | Devuelve los datos del equipo y las cinco incidencias más recientes en orden descendente sin exponer el reportante. | Ejecutado |
| RF-01 / RF-02, QR inexistente | `responde404CuandoElQrDelEquipoNoExiste` | Devuelve `ProblemDetail` 404 para un equipo inexistente. | Ejecutado |
| RNF-02, identidad de la sesión | `registraLaIncidenciaConUsuarioFijadoPorElServidor` | Ignora un `usuarioId` enviado por navegador y obtiene la cuenta desde el principal autenticado en la sesión. | Ejecutado |
| UI, paneles | `renderizaLosPanelesWebActivos` | Renderiza listado de incidencias, registro de mantenimiento e informe de recurrencias. | Ejecutado |
| RF-04, catálogo de aulas | Pruebas de disponibilidad y `impideRegistrarDosVecesElMismoCodigoDeAulaEnUnaSede` | Verifica el catálogo, la selección inicial compatible y el rechazo de códigos duplicados. | Ejecutado (18/11/2026) |
| RF-04, filtros en cascada | `aceptaElAulaCuandoCoincideConLaTorreYElPisoSeleccionados` y `rechazaElAulaCuandoNoCoincideConLaTorreSeleccionada` | Consulta un aula válida y rechaza aula con torre o piso que no corresponde. | Ejecutado (18/11/2026) |
| RF-04, selectores en cascada, búsqueda y filtros | Verificación de interacción en navegador | Seis teclas con 80 ms entre sí generaron una solicitud AJAX. La búsqueda y los filtros de estado y aula mantuvieron la lista visible durante la consulta; el esqueleto solo apareció en la carga inicial. `completo=true` renderizó por SSR sin consulta duplicada. Un error 500 simulado ocultó filas anteriores y el reintento recuperó los resultados. | Navegador local ejecutado (18/11/2026) |
| RF-04, aula incompleta | `panelDeDisponibilidadOmiteAulasSinTorreOPisoYAdvierteQueRequierenValidacion` | Excluye del selector las aulas con torre o piso desconocidos, contabiliza sus equipos en el aviso y mantiene disponible el panel. | Ejecutado (18/11/2026) |
| RF-04, equipos por aula | `panelDeDisponibilidadMuestraElEquipoDeLaUbicacionSeleccionada` y `panelDeDisponibilidadSeparaLasAulasPorTorre` | Comprueba etiqueta, conteo y aislamiento de los equipos de cada aula, además del aviso de ubicaciones anteriores. | Ejecutado (18/11/2026) |
| RF-04, ubicación anterior | `rechazaComoFiltroLasUbicacionesAnterioresAlCatalogoDeAulas` y `migraDesdeVersionCincoYReasignaSoloElEquipoDemoA0301` | Evita consultar una ubicación legada como si fuera aula del catálogo; valida la actualización V5→V7, conserva otro equipo que sigue en LAB-01 y califica el esquema en las consultas de prueba para no alterar conexiones compartidas. | Ejecutado (18/11/2026) |
| RF-03, invariantes de mantenimiento | `registraMantenimientoSinCambiarEstadoNiCerrarIncidencia` | Conserva el estado operativo y el estado registrado de la incidencia asociada. | Ejecutado |
| RF-03, componentes | `instalarYReemplazarComponenteMantieneHistorialDeAsignaciones` | Cierra la asignación anterior, crea la nueva y registra las acciones ligadas a mantenimientos. | Ejecutado |
| RF-03, vínculo correctivo | `mantenimientoCorrectivoExigeFallaDelMismoEquipo` | Rechaza una falla de otro equipo y permite asociar la falla correcta. | Ejecutado |
| RF-06, agrupación | `clasificaFallasYCalculaRecurrenciaPorEquipoYTipoEnElPeriodo` | Agrupa por computadora y tipo; dos casos generan recurrencia, uno de otro tipo no. | Ejecutado |
| RF-06, exportación | `exportaRecurrenciasFiltradasEnCsvExcelYPdfDesdeLaWeb` | Descarga CSV, XLSX (validado con Apache POI) y PDF con los filtros del reporte. | Ejecutado |
| EXT-01, alcance de exportación | `renderizaLosPanelesWebActivos` | Comprueba que la lista de incidencias no muestra enlaces ni controles de exportación. | Ejecutado |
| OBS-01, paginación de historial | `recorrePorPaginasUnHistorialDeCincoMilIncidencias` | Recorre 5 000 incidencias en 50 páginas y valida orden estable sin duplicados. | Ejecutado |
| API REST, errores | `ApiExceptionHandlerTest` | Verifica respuestas `ProblemDetail` y estados HTTP 400, 404 y 409 sin filtrar detalles internos de PostgreSQL. | Ejecutado |
| RNF-02, autenticación desde PostgreSQL | `loginCorrectoRedirigeAlPanelDeDisponibilidad` y `rechazaContrasenaIncorrectaYCuentaInactiva` | Crea una cuenta activa con hash BCrypt en la base temporal y comprueba login correcto, clave incorrecta y cuenta inactiva. | Ejecutado (17/11/2026) |
| RNF-02, autorización por rol | `limitaLaAdministracionDeUsuariosAlRolAdministradorYExigeCsrf` | Comprueba que el rol administrador accede a usuarios y que otros roles reciben 403; verifica CSRF en POST. | Ejecutado (17/11/2026) |

La suite también cubre paginación estable, límites de página, historial por QR y asociación del usuario autenticado. La salida efectiva se debe volver a consultar después de cada cambio; esta matriz no sustituye el XML de Surefire.

## Límites conocidos

- La pantalla de escaneo incluye cámara y entrada manual; el formulario de incidencia recibe el QR como texto. La lectura con cámara en dispositivos físicos sigue sin verificarse.
- La sesión identifica la cuenta que registra el evento, pero no verifica quién usa físicamente el equipo; aún no hay validación de piloto.
- La recurrencia solo incluye incidencias clasificadas, porque la clasificación de nuevas incidencias es opcional.
- Se usan datos sintéticos en una base temporal; no se declara evidencia de uso piloto.

### Disponibilidad, búsqueda y filtros

El 18/11/2026 se ejecutó la suite completa con PostgreSQL 16 y Testcontainers: 53 pruebas aprobadas, sin fallos, errores ni omisiones. Las pruebas combinan búsqueda, estados y aula, comprueban el rechazo de un estado inválido y verifican la paginación para 0, 1, 10, 11, 20, 21 y 31 resultados.

La revisión de interacción en navegador confirmó que seis teclas escritas con 80 ms entre sí generan una sola consulta; los resultados permanecen visibles al buscar y cambiar filtros de estado o aula. La ruta `completo=true` conserva el renderizado del servidor y no dispara una consulta AJAX duplicada. Un error 500 simulado oculta los resultados anteriores y el reintento recupera la lista. La navegación atrás/adelante restaura los filtros. Con JavaScript desactivado, el formulario aplicó el aula y el servidor mostró los resultados por SSR. Se revisaron los tamaños 390 × 844 y 1440 × 1000 sin desbordamiento horizontal. También se comprobó la consulta manual de `PC-001`, su ficha y el avance del formulario de incidencias hasta el paso 2, sin enviar el registro. La cámara física no se probó. Esta revisión no forma parte de la suite automatizada.
