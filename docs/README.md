# Documentación de TrazaLab

Esta carpeta separa la arquitectura, la trazabilidad de requisitos, la evidencia de pruebas y el historial de correcciones.

## Guías vigentes

- [Arquitectura](Arquitectura.md): estructura de paquetes, flujo MVC/JPA y límites actuales.
- [Matriz de trazabilidad](Matriz%20Trazabilidad.md): correspondencia entre requisitos, código, persistencia y pruebas.
- [Pruebas de aplicación](testing/README.md): estrategia tomada del laboratorio del docente y resultados de TrazaLab.
- [Seguridad y límites](Seguridad%20y%20Limites.md): modo demo, protección CSRF y datos personales.
- [Versiones y fuentes](Versiones%20y%20Fuente.md): dependencias, referencias y herramientas.
- [Guía PostgreSQL](../database/README.md): ejecución con el Compose de la raíz, migraciones y volúmenes existentes.
- [Diagrama](TrazaLab_Arquitectura_Trazabilidad.drawio): flujo editable de la arquitectura.

## Correcciones

- [`correcciones/OBS-01-consultas-paginadas.md`](correcciones/OBS-01-consultas-paginadas.md): carga paginada para exportaciones.
- [`correcciones/OBS-02-arquitectura-web.md`](correcciones/OBS-02-arquitectura-web.md): migración a Spring MVC y estructura JPA.
- [`correcciones/OBS-03-mantenimiento-reportes.md`](correcciones/OBS-03-mantenimiento-reportes.md): validaciones de rol técnico, asociación de fallas y fechas de recurrencia.
- [`correcciones/OBS-04-retiro-exportacion-xlsx.md`](correcciones/OBS-04-retiro-exportacion-xlsx.md): retiro del Excel duplicado y de la exportación XLSX.
- [`correcciones/OBS-05-diagnostico-arranque-spring-boot-4.md`](correcciones/OBS-05-diagnostico-arranque-spring-boot-4.md): ajuste del diagnóstico al paquete disponible en Spring Boot 4.
- [`correcciones/OBS-06-diagnosticos-editor-java.md`](correcciones/OBS-06-diagnosticos-editor-java.md): iterador del benchmark, ordenamiento tipado y recarga Maven del editor.

## Criterios de estado

- **Implementado:** existe código integrado en la rama publicada.
- **Probado:** hay un caso ejecutado y un resultado guardado en el informe de pruebas.
- **Diseñado:** el requisito tiene propuesta, pero todavía no código ejecutable.
- **Pendiente:** falta trabajo para cerrar el requisito.

El estado probado no acredita uso piloto ni información real: las pruebas de integración usan una base temporal de Testcontainers.
