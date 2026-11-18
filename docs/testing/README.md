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

`RegistroIncidenciaJpaTest` inicia PostgreSQL 16 desechable con Testcontainers, aplica Flyway, valida el mapeo con Hibernate y ejecuta las pruebas JPA y MockMvc. `GlobalExceptionHandlerTest` comprueba los estados y cuerpos `ProblemDetail` para validación, recursos inexistentes y conflictos. La ejecución histórica del 07/11/2026 terminó con **25 pruebas aprobadas**. El 17/11/2026, `mvn -q test` terminó con **41 pruebas aprobadas**. El 18/11/2026, la ejecución actual terminó con **50 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas** (45 de integración y 5 unitarias), incluidas el manejo de aulas incompletas y la actualización desde V5 a V7 en esquemas desechables. La migración mueve `PC-001` desde `LAB-01` y conserva `LAB-02` cuando ya es su ubicación. Flyway V7 también se aplicó a la base local y se confirmó `PC-001` en `A0301`. Las pruebas automatizadas usan PostgreSQL temporal y no acreditan un piloto real.

La suite también contiene pruebas para login correcto con una cuenta insertada en PostgreSQL, contraseña incorrecta, cuenta inactiva, redirección de usuarios anónimos y autorización de la gestión de usuarios por rol. La configuración de prueba crea el hash BCrypt en la base temporal; no configura credenciales administrativas mediante propiedades. El alta inicial interactiva bajo el perfil `crear-administrador` todavía requiere una comprobación manual desde una consola con base de datos descartable.

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
| RF-04, catálogo de aulas | `panelDeDisponibilidadMuestraSoloLasAulasDelCatalogoYNoSeleccionaUnaPorDefecto` y `impideRegistrarDosVecesElMismoCodigoDeAulaEnUnaSede` | Verifica los nueve códigos de Torre A y cuatro de Torre B, el rechazo de códigos duplicados y que el panel inicia sin aula seleccionada. | Ejecutado (18/11/2026) |
| RF-04, filtros en cascada | `aceptaElAulaCuandoCoincideConLaTorreYElPisoSeleccionados` y `rechazaElAulaCuandoNoCoincideConLaTorreSeleccionada` | Consulta un aula válida y rechaza aula con torre o piso que no corresponde. | Ejecutado (18/11/2026) |
| RF-04, selectores en cascada | Sin caso automatizado | La torre filtra pisos, el piso filtra aulas y cada cambio completa la primera opción dependiente. El botón Aplicar ubicación envía la selección mediante GET y sirve de alternativa si JavaScript no carga. En navegador de escritorio se verificó A→3→A0301 con `PC-001` y B→6→B0605 sin equipos de muestra. | Navegador de escritorio ejecutado (18/11/2026); móvil pendiente |
| RF-04, aula incompleta | `panelDeDisponibilidadOmiteAulasSinTorreOPisoYAdvierteQueRequierenValidacion` | Excluye del selector las aulas con torre o piso desconocidos, contabiliza sus equipos en el aviso y mantiene disponible el panel. | Ejecutado (18/11/2026) |
| RF-04, equipos por aula | `panelDeDisponibilidadMuestraElEquipoDeLaUbicacionSeleccionada` y `panelDeDisponibilidadSeparaLasAulasPorTorre` | Comprueba etiqueta, conteo y aislamiento de los equipos de cada aula, además del aviso de ubicaciones anteriores. | Ejecutado (18/11/2026) |
| RF-04, ubicación anterior | `rechazaComoFiltroLasUbicacionesAnterioresAlCatalogoDeAulas`, `migraDesdeVersionCincoYReasignaSoloElEquipoDemoA0301` y `noReasignaPc001CuandoYaEstaEnOtraUbicacion` | Evita consultar una ubicación legada como si fuera aula del catálogo; valida la actualización V5→V7, conserva otro equipo que sigue en LAB-01 y no reemplaza la ubicación de `PC-001` cuando ya está en LAB-02. Las consultas de prueba califican el esquema para no alterar el estado de conexiones compartidas. | Ejecutado (18/11/2026) |
| RF-03, invariantes de mantenimiento | `registraMantenimientoSinCambiarEstadoNiCerrarIncidencia` | Conserva el estado operativo y el estado registrado de la incidencia asociada. | Ejecutado |
| RF-03, componentes | `instalarYReemplazarComponenteMantieneHistorialDeAsignaciones` | Cierra la asignación anterior, crea la nueva y registra las acciones ligadas a mantenimientos. | Ejecutado |
| RF-03, vínculo correctivo | `mantenimientoCorrectivoExigeFallaDelMismoEquipo` | Rechaza una falla de otro equipo y permite asociar la falla correcta. | Ejecutado |
| RF-06, agrupación | `clasificaFallasYCalculaRecurrenciaPorEquipoYTipoEnElPeriodo` | Agrupa por computadora y tipo; dos casos generan recurrencia, uno de otro tipo no. | Ejecutado |
| RF-06, exportación | `exportaRecurrenciasFiltradasEnCsvExcelYPdfDesdeLaWeb` | Descarga CSV, XLSX (validado con Apache POI) y PDF con los filtros del reporte. | Ejecutado |
| EXT-01, alcance de exportación | `renderizaLosPanelesWebActivos` | Comprueba que la lista de incidencias no muestra enlaces ni controles de exportación. | Ejecutado |
| OBS-01, paginación de historial | `recorrePorPaginasUnHistorialDeCincoMilIncidencias` | Recorre 5 000 incidencias en 50 páginas y valida orden estable sin duplicados. | Ejecutado |
| API REST, errores | `GlobalExceptionHandlerTest` | Verifica respuestas `ProblemDetail` y estados HTTP 400, 404 y 409 sin filtrar detalles internos de PostgreSQL. | Ejecutado |
| RNF-02, autenticación desde PostgreSQL | `loginCorrectoRedirigeAlPanelDeDisponibilidad` y `rechazaContrasenaIncorrectaYCuentaInactiva` | Crea una cuenta activa con hash BCrypt en la base temporal y comprueba login correcto, clave incorrecta y cuenta inactiva. | Ejecutado (17/11/2026) |
| RNF-02, autorización por rol | `limitaLaAdministracionDeUsuariosAlRolAdministradorYExigeCsrf` | Comprueba que el rol administrador accede a usuarios y que otros roles reciben 403; verifica CSRF en POST. | Ejecutado (17/11/2026) |

La suite también cubre paginación estable, límites de página, historial por QR y asociación del usuario autenticado. La salida efectiva se debe volver a consultar después de cada cambio; esta matriz no sustituye el XML de Surefire.

## Límites conocidos

- No se prueba la cámara ni el lector físico de QR: el flujo usa texto manual.
- La sesión identifica la cuenta que registra el evento, pero no verifica quién usa físicamente el equipo; aún no hay validación de piloto.
- La recurrencia solo incluye incidencias clasificadas, porque la clasificación de nuevas incidencias es opcional.
- Se usan datos sintéticos en una base temporal; no se declara evidencia de uso piloto.
