# Documentación de TrazaLab

La documentación de esta carpeta describe la aplicación, su arquitectura, pruebas y trazabilidad. La infraestructura de AWS y sus procedimientos operativos están en [`../deploy/README.md`](../deploy/README.md).

## Guías vigentes

- [Arquitectura](arquitectura/Arquitectura.md): módulos, capas, persistencia y flujos implementados.
- [Matriz de trazabilidad](Matriz%20Trazabilidad.md): relación entre requisitos, código, datos y pruebas.
- [Pruebas](testing/README.md): estrategia, casos ejecutados y pendientes.
- [Seguridad y límites](Seguridad%20y%20Limites.md): autenticación, roles, CSRF y límites del modo demo.
- [Versiones y fuentes](Versiones%20y%20Fuente.md): dependencias, herramientas y referencias.
- [Base de datos](../database/README.md): Compose local, migraciones y volúmenes existentes.
- [Diagrama de arquitectura](arquitectura/Diagrama%20Arquitectura%20y%20Trazabilidad.drawio): flujo editable y límites del agente propuesto.

## Historial de cambios y correcciones

- [Historial de arquitectura y herramientas](Historial%20Arquitectura%20y%20Herramientas.md): migración a Spring MVC, estructura REST, diagnóstico de arranque y herramientas.
- [Historial de flujos y rendimiento](Historial%20Flujos%20y%20Rendimiento.md): historial paginado, mantenimientos, reportes, diseño adaptable y mediciones locales.

## Estados de evidencia

- **Implementado:** existe código integrado en la rama publicada.
- **Probado:** hay un caso ejecutado y un resultado guardado en el informe de pruebas.
- **Diseñado:** el requisito tiene propuesta, pero todavía no código ejecutable.
- **Pendiente:** falta trabajo para cerrar el requisito.

Las pruebas de integración usan Testcontainers. Sus resultados no acreditan el uso piloto ni la validez de datos de un laboratorio real.
