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

`RegistroIncidenciaJpaTest` inicia PostgreSQL 16 desechable con Testcontainers, aplica Flyway, valida el mapeo con Hibernate y ejecuta las pruebas JPA y MockMvc. `GlobalExceptionHandlerTest` comprueba los estados y cuerpos `ProblemDetail` para validación, recursos inexistentes y conflictos. La ejecución histórica del 07/11/2026 terminó con **25 pruebas aprobadas**. El 17/11/2026, `mvn -q test` terminó con **41 pruebas aprobadas, 0 fallidas, 0 errores y 0 omitidas** (36 de integración y 5 unitarias), incluidas las pruebas de autenticación y autorización descritas abajo. Las pruebas usan PostgreSQL temporal; no alteran el volumen local ni acreditan un piloto real.

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
