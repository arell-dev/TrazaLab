# Versiones y fuentes de TrazaLab

Inventario contrastado con el proyecto local el 06/11/2026. Las bibliotecas sin versión directa son administradas por Spring Boot; para conocer la versión resuelta se consulta el árbol de dependencias de Maven.

| Componente | Versión o estado declarado | Fuente y uso |
|---|---|---|
| Java | 21 | [`pom.xml`](../pom.xml), propiedad `java.version` |
| Spring Boot parent | 4.1.2 | [`pom.xml`](../pom.xml); aplicación Spring MVC |
| Spring MVC | Administrado por Spring Boot 4.1.2 | `spring-boot-starter-webmvc`; rutas HTTP y formularios |
| Thymeleaf | Administrado por Spring Boot 4.1.2 | `spring-boot-starter-thymeleaf`; vistas y fragmentos |
| Bootstrap | 5.3.8 | CSS y JS referenciados por `templates/fragments/head.html` y `scripts.html` |
| Spring Data JPA e Hibernate ORM | Administrados por Spring Boot 4.1.2 | Repositorios, entidades y validación del esquema |
| Spring Security | Administrado por Spring Boot 4.1.2 | CSRF habilitado; rutas permitidas sin login en etapa demo |
| Spring Validation | Administrado por Spring Boot 4.1.2 | Validación del formulario de registro de incidencias |
| SLF4J y Logback | Administrados por Spring Boot 4.1.2 | `LoggingAplicacion` y [`logback-spring.xml`](../src/main/resources/logback-spring.xml) |
| Driver JDBC PostgreSQL | Administrado por Spring Boot 4.1.2 | Persistencia JPA sobre PostgreSQL |
| PostgreSQL | Imagen `postgres:16` | [`compose.yaml`](../compose.yaml); instancia local de desarrollo |
| Apache PDFBox | 3.0.8 | PDF de fallas recurrentes |
| Apache Commons CSV | 1.14.1 | CSV de incidencias |
| Google Guava | No declarada | Pendiente respecto de la rúbrica; no se le atribuye uso |
| Maven Wrapper | Según [configuración del wrapper](../.mvn/wrapper/maven-wrapper.properties) | Ejecución reproducible de Maven |
| JUnit y Testcontainers | Administrados por Spring Boot 4.1.2 | 18 pruebas con PostgreSQL temporal en la ejecución del 06/11/2026 |
| Flyway | Administrado por Spring Boot 4.1.2, con módulo PostgreSQL | `src/main/resources/db/migration`; V1 define el esquema y V2 agrega el catálogo inicial de tipos de falla |

## Fuentes académicas

- Requisitos y alcance: `APF01/Requerimientos.xlsx` y `APF01/Project Charter, WBS, Gantt.xlsx`.
- Trazabilidad inicial y prototipo: `APF01/Trazabilidad, Prototipo inicial.xlsx`.
- Proceso, pantallas y modelo relacional: `APF02/BPM.xlsx`, `APF02/Pantallas UI UX.xlsx` y `APF02/DiseñoDB, Relaciones, Entidades.xlsx`.
- Esquema de referencia inicial: `APF02/Scripts/PostgreSQL Script.sql` y [`01_trazalab_schema.sql`](../database/01_trazalab_schema.sql). La fuente ejecutable son las migraciones inmutables de `src/main/resources/db/migration`.
- Estado de implementación, arquitectura, pruebas diseñadas y control de versiones: `APF03/Matriz de Transferencia y Trazabilidad.xlsx`, `APF03/Arquitectura.xlsx` y `APF03/Control de Versiones.xlsx`; estos archivos académicos se conservan fuera de este repositorio.
- La estructura de JPA y las plantillas web toma como referencia el proyecto ilustrativo Opalina; se mantiene independiente del código fuente de TrazaLab.

Las rutas `APF01/`, `APF02/` y `APF03/` son relativas a la carpeta `ProyectoFinal`, que contiene `Proyecto/trazalab`. El código verificable está bajo [`src/main/java`](../src/main/java) y las pruebas bajo [`src/test/java`](../src/test/java). Los controladores HTTP están en `controller`; `service` conserva reglas y transacciones.

## Evidencia observada

- El 06/11/2026, `./mvnw.cmd -q clean test` ejecutó 17 pruebas con PostgreSQL temporal: 17 aprobadas, 0 fallidas, 0 errores y 0 omitidas.
- La suite verificó render y envío de formularios MVC, registro y clasificación, paginación, mantenimiento correctivo y componentes, recurrencias, exportaciones CSV/XLSX/PDF y ausencia de descargas en incidencias.
- Las pruebas usan una base PostgreSQL desechable de Testcontainers; no certifican por sí solas un piloto real ni el inventario completo.
- La verificación local del esquema y la línea base Flyway se documentaron en la PR #3; la base de datos preexistente conserva sus filas.

## Referencias técnicas

- [Java SE 21](https://docs.oracle.com/en/java/javase/21/)
- [Spring Boot](https://docs.spring.io/spring-boot/index.html)
- [Bootstrap 5.3](https://getbootstrap.com/docs/5.3/)
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/)
- [Apache PDFBox](https://pdfbox.apache.org/)
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/)
- [Testcontainers for Java](https://java.testcontainers.org/)

Las exportaciones CSV, XLSX y PDF están disponibles únicamente en Reportes. Filtran hasta 30 días por equipo y tipo; el listado de incidencias no ofrece exportaciones.
