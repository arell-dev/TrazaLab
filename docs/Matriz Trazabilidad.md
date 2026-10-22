# Matriz de trazabilidad de TrazaLab

Esta matriz describe el menú persistente implementado y separa las evidencias ejecutables de los diseños de APF03. La matriz académica S10 se conserva en [S10_Matriz_Trazabilidad_y_Transferencia.xlsx](S10_Matriz_Trazabilidad_y_Transferencia.xlsx); sus nombres propuestos y estados deben contrastarse con el código y los resultados reales.

## Registro implementado

| Requisito | Entrada y DTO | Controlador | Servicio | Repository / DAO y almacenamiento | Evidencia | Estado |
|---|---|---|---|---|---|---|
| RF-05: registrar incidencia asociada a computadora y usuario | `console/ConsolaPersistencia` construye `RegistroIncidenciaDto` | `IncidenciaController.registrar()` valida el DTO y devuelve `IncidenciaRegistradaDto` | `RegistroIncidenciaPersistenteServicio.registrar()` valida existencia/estado del usuario, rol, QR y descripción; escribe dentro de una transacción | `repository`: `UsuarioJpaRepositorio`, `ComputadoraJpaRepositorio`, `IncidenciaJpaRepositorio` → Hibernate/JPA → PostgreSQL | `RegistroIncidenciaJpaTest` verifica guardado, lectura y rechazo de descripción vacía contra PostgreSQL de Testcontainers. La ejecución local se demuestra con `APP_EVENT accion=incidencia.registrar` en `logs/trazalab-app.log` y consulta SQL a `incidencia`. | Implementado; conservar evidencia de la ejecución que se presente |
| Consultas de incidencias, usuarios e historial | `console/ConsolaPersistencia` | `IncidenciaController` coordina la consulta | `RegistroIncidenciaPersistenteServicio` obtiene los resultados | Repositorios JPA → PostgreSQL | `APP_EVENT` registra `incidencia.listar`, `usuario.listar` y `computadora.historial`, con cantidad de resultados | Implementado; conservar evidencia de consola y archivo al presentar |
| Exportar incidencias a PDF, Excel y CSV | `console/ConsolaPersistencia` selecciona el formato | `ExportacionIncidenciasController` obtiene datos, delega al exportador y registra `incidencia.exportar` | `RegistroIncidenciaPersistenteServicio.consultarIncidencias()` obtiene los registros persistidos | Repositorios JPA → PostgreSQL; adaptadores `export.pdf`, `export.excel`, `export.csv` generan los archivos | Archivos fechados en `evidencias/`; PDF apaisado y paginado, Excel con filtros y CSV UTF-8 con BOM | Implementado; conservar los archivos generados como evidencia de presentación |

El test de integración invoca directamente al servicio, no al controlador de aplicación. La captura o demostración manual de consola cubre el trayecto por `IncidenciaController`. No se afirma cobertura automática de la consola ni del controlador hasta que exista una prueba específica.

## Evidencias visuales

![Flujo de registro RF-05 hasta PostgreSQL y logging](imgs/flujo_principal.png)

![Ciclo TDD y estado de la prueba de integración](imgs/ciclo_tdd.png)

## Consulta implementada y flujos pendientes

| Requisito / flujo | Módulos esperados | Evidencia necesaria | Estado |
|---|---|---|---|
| RF-01 / RF-02: ficha básica e historial de incidencias por QR | `console` → `HistorialComputadoraDto` → `IncidenciaController` → `service` → `repository` → PostgreSQL | Consulta por QR; incidencias ordenadas cronológicamente | Implementado para ficha e incidencias; historial de componentes pendiente |
| RF-03 / RF-04: mantenimiento y disponibilidad | `console`/futuro `web` → `controller` → `service` → `repository` | Pruebas de reglas y consulta posterior en PostgreSQL | Pendiente |
| RF-06: fallas recurrentes / reportes | `service` → `repository` → `export` | Los exportadores actuales incluyen el listado de incidencias; el análisis de fallas recurrentes y reportes agregados se contrastará con registros | Parcial: exportación de incidencias implementada; análisis agregado pendiente |
| Seguridad por rol con inicio de sesión | Seguridad de aplicación y credenciales persistidas | Pruebas de autenticación, permisos y usuario inactivo | Pendiente; hoy se valida el rol del usuario reportante de la incidencia |
| Aplicación recolectora de computadoras | Segundo programa C++ → adaptador `integration.collector` → casos de uso | Datos enviados y recibidos con trazabilidad y autenticación | Pendiente; contrato de intercambio por definir |
| Exportación PDF, Excel y CSV | `ExportacionIncidenciasController` → `ExportadorIncidenciasPdf`, `ExportadorIncidenciasExcel`, `ExportadorIncidenciasCsv` | Archivos generados desde PostgreSQL y evento `APP_EVENT accion=incidencia.exportar` | Implementada para el listado de incidencias; guardar los archivos de la demostración como evidencia |

## Cadena verificable de registro

```text
RF-05
  → console/ConsolaPersistencia
  → IncidenciaController
  → RegistroIncidenciaDto / Bean Validation
  → RegistroIncidenciaPersistenteServicio
  → repository (Spring Data JPA / DAO)
  → Hibernate
  → PostgreSQL
```

La evidencia automatizada del registro está en `src/test/java/com/utp/trazalab/integration/RegistroIncidenciaJpaTest.java`. Las consultas del menú todavía requieren evidencia de ejecución y pruebas específicas. Para evidenciar la corrida manual, iniciar la app con `database/run.ps1`, registrar la incidencia y conservar el log junto con la consulta SQL indicada en el README.

## Interpretación de la matriz S10

Las hojas de trazabilidad, transferencia, TDD y evidencias de APF03 son la guía para organizar el trabajo. Las pruebas diseñadas en el Excel no son resultados ejecutados. Los nombres de clases, pantallas y estados deben actualizarse según la implementación real de TrazaLab; las plantillas del docente y la palabra “CitaLab” no describen este sistema.
