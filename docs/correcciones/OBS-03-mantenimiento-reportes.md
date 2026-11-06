# OBS-03 · Reglas de mantenimiento y ventana de recurrencia

**Fecha:** 05/11/2026
**Rama de implementación:** `feat/mantenimientos-reportes`
**Estado:** corregido y verificado con 16 pruebas aprobadas en PostgreSQL Testcontainers.

## Hallazgos durante la ejecución

| Causa observada | Impacto | Corrección aplicada |
|---|---|---|
| El trigger `fn_validar_mantenimiento()` exige que el responsable esté activo y tenga rol de Técnico de Soporte o Técnico de Laboratorio. El usuario demo reportante tenía rol Docente. | PostgreSQL rechazó el primer guardado de mantenimiento; no se debilitó la regla de base de datos. | Se separó la propiedad de identidad técnica demo (`TRAZALAB_DEMO_TECH_EMAIL`), se valida el rol en el servicio y el sembrado opcional crea un técnico demo si no existe. El usuario se sigue fijando en servidor. |
| El trigger exige asociar una falla válida a todo mantenimiento correctivo y que esa falla pertenezca al mismo equipo. | El primer intento de probar un mantenimiento correctivo sin asociación fue rechazado correctamente por PostgreSQL. | El formulario ofrece fallas clasificadas recientes y el servicio valida la coincidencia de equipo antes de guardar. |
| El reloj usaba UTC para construir `TIMESTAMP` locales mientras el periodo del reporte se tomaba en Lima. | Cerca de medianoche una falla podía quedar en el día siguiente y excluirse del reporte por fecha. | El bean `Clock` usa `America/Lima`, el huso definido para fechas operativas y JPA. La fecha de incidencia continúa como `Instant`. |

## Trazabilidad

- Servicio de mantenimiento: [`RegistroMantenimientoServicio`](../../src/main/java/com/utp/trazalab/service/RegistroMantenimientoServicio.java).
- Servicios web: [`MantenimientoWebController`](../../src/main/java/com/utp/trazalab/controller/MantenimientoWebController.java) y [`ReporteWebController`](../../src/main/java/com/utp/trazalab/controller/ReporteWebController.java).
- Migración de catálogo: [`V2__catalogo_tipos_falla.sql`](../../src/main/resources/db/migration/V2__catalogo_tipos_falla.sql).
- Casos y ejecución: [`testing/README.md`](../testing/README.md) y [`RegistroIncidenciaJpaTest`](../../src/test/java/com/utp/trazalab/integration/RegistroIncidenciaJpaTest.java).
