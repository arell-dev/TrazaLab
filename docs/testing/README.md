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

`RegistroIncidenciaJpaTest` inicia PostgreSQL 16 desechable con Testcontainers, aplica Flyway, valida el mapeo con Hibernate y ejecuta las pruebas JPA y MockMvc. `GlobalExceptionHandlerTest` comprueba los estados y cuerpos `ProblemDetail` para validación, recursos inexistentes y conflictos. En la revisión de esta rama, `clean test` terminó con **23 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas** (06/11/2026). Esto no altera el volumen de la base local ni acredita un piloto real.

Los resultados por clase quedan en `target/surefire-reports`. No se informa un porcentaje JaCoCo porque el proyecto aún no configura ese agente o informe.

## Matriz de ejecución

| Requisito / regla | Caso automatizado | Verificación principal | Estado |
|---|---|---|---|
| RF-05, registro de incidencia | `registraYRecuperaLaIncidenciaEnPostgresql` | Inserta y consulta la incidencia, equipo y reportante en PostgreSQL. | Ejecutado |
| RF-05, validación | `rechazaDescripcionVaciaSinGuardar` y `mantieneElFormularioYValidaLosCamposObligatorios` | Rechaza entradas inválidas sin persistir y devuelve errores al formulario. | Ejecutado |
| RF-05, recurso inexistente | `mantieneElFormularioCuandoElCodigoQrNoExiste` | Devuelve el error al formulario y confirma que no persiste la incidencia. | Ejecutado |
| RNF-02, identidad de demostración | `registraLaIncidenciaConUsuarioFijadoPorElServidor` | Ignora un `usuarioId` enviado por navegador y usa el usuario configurado en servidor. | Ejecutado |
| UI, paneles | `renderizaLosPanelesWebActivos` | Renderiza listado de incidencias, registro de mantenimiento e informe de recurrencias. | Ejecutado |
| RF-03, invariantes de mantenimiento | `registraMantenimientoSinCambiarEstadoNiCerrarIncidencia` | Conserva el estado operativo y el estado registrado de la incidencia asociada. | Ejecutado |
| RF-03, componentes | `instalarYReemplazarComponenteMantieneHistorialDeAsignaciones` | Cierra la asignación anterior, crea la nueva y registra las acciones ligadas a mantenimientos. | Ejecutado |
| RF-03, vínculo correctivo | `mantenimientoCorrectivoExigeFallaDelMismoEquipo` | Rechaza una falla de otro equipo y permite asociar la falla correcta. | Ejecutado |
| RF-06, agrupación | `clasificaFallasYCalculaRecurrenciaPorEquipoYTipoEnElPeriodo` | Agrupa por computadora y tipo; dos casos generan recurrencia, uno de otro tipo no. | Ejecutado |
| RF-06, exportación | `exportaRecurrenciasFiltradasEnCsvYPdfDesdeLaWeb` | Descarga CSV y PDF con filtros y confirma que la ruta Excel responde 400. | Ejecutado |
| EXT-01, alcance de exportación | `renderizaLosPanelesWebActivos` | Comprueba que la lista de incidencias no muestra enlaces ni controles de exportación. | Ejecutado |
| OBS-01, paginación de historial | `recorrePorPaginasUnHistorialDeCincoMilIncidencias` | Recorre 5 000 incidencias en 50 páginas y valida orden estable sin duplicados. | Ejecutado |
| API REST, errores | `GlobalExceptionHandlerTest` | Verifica respuestas `ProblemDetail` y estados HTTP 400, 404 y 409 sin filtrar detalles internos de PostgreSQL. | Ejecutado |

La suite también cubre paginación estable, límites de página, historial por QR, asociación del usuario activo y validación del usuario de demostración. La salida efectiva se debe volver a consultar después de cada cambio; esta matriz no sustituye el XML de Surefire.

## Límites conocidos

- No se prueba la cámara ni el lector físico de QR: el flujo usa texto manual.
- No se prueba autenticación, sesión ni autorización web por rol; las rutas permanecen abiertas en modo demo. El esquema sí exige rol técnico al persistir un mantenimiento.
- La recurrencia solo incluye incidencias clasificadas, porque la clasificación de nuevas incidencias es opcional.
- Se usan datos sintéticos en una base temporal; no se declara evidencia de uso piloto.
