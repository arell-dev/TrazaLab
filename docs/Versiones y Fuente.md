# Versiones y fuentes de TrazaLab

Inventario contrastado con el proyecto local el 27/10/2026. Las bibliotecas sin versión directa son administradas por el parent de Spring Boot; para conocer la versión resuelta se consulta el árbol de dependencias de Maven.

| Componente | Versión o estado declarado | Fuente y uso |
|---|---|---|
| Java | 21 | [`pom.xml`](../pom.xml), propiedad `java.version` |
| Spring Boot parent | 4.1.2 | [`pom.xml`](../pom.xml); arranque de consola |
| Spring Data JPA e Hibernate ORM | Administrados por Spring Boot 4.1.2 | Repositorios, mapeo de entidades y validación del esquema |
| Spring Security | Dependencia declarada; autenticación no configurada | [`pom.xml`](../pom.xml); acceso de usuario final pendiente |
| Spring Validation | Administrado por Spring Boot 4.1.2 | Validación del DTO de registro |
| SLF4J y Logback | Administrados por Spring Boot 4.1.2 | `LoggingAplicacion` y [`logback-spring.xml`](../src/main/resources/logback-spring.xml) |
| Driver JDBC PostgreSQL | Administrado por Spring Boot 4.1.2 | Persistencia JPA sobre PostgreSQL |
| PostgreSQL | Imagen `postgres:16` | [`compose.yaml`](../database/compose.yaml); instancia local de desarrollo |
| Apache PDFBox | 3.0.8 | PDF de incidencias |
| Apache POI | 5.5.1 | XLSX de incidencias |
| Apache Commons CSV | 1.14.1 | CSV de incidencias |
| Google Guava | No declarada | Pendiente respecto de la rúbrica; no se le atribuye uso |
| Maven Wrapper | Según [configuración del wrapper](../.mvn/wrapper/maven-wrapper.properties) | Ejecución reproducible de Maven |
| JUnit y Testcontainers | Administrados por Spring Boot 4.1.2 | Dos pruebas de integración con PostgreSQL temporal |
| Flyway | No instalado | Las migraciones versionadas quedan para una etapa posterior |

## Fuentes académicas

- Requisitos y alcance: `APF01/Requerimientos.xlsx` y `APF01/Project Charter, WBS, Gantt.xlsx`.
- Trazabilidad inicial y prototipo: `APF01/Trazabilidad, Prototipo inicial.xlsx`.
- Proceso, pantallas y modelo relacional: `APF02/BPM.xlsx`, `APF02/Pantallas UI UX.xlsx` y `APF02/DiseñoDB, Relaciones, Entidades.xlsx`.
- Script SQL sincronizado con el esquema de desarrollo: `APF02/Scripts/PostgreSQL Script.sql` y [`01_trazalab_schema.sql`](../database/01_trazalab_schema.sql).
- Estado de implementación, arquitectura, pruebas diseñadas y control de versiones: `APF03/Matriz de Transferencia y Trazabilidad.xlsx`, `APF03/Arquitectura.xlsx` y `APF03/Control de Versiones.xlsx`. La [copia S10](S10_Matriz_Trazabilidad_y_Transferencia.xlsx) conserva la matriz de APF03 dentro del proyecto.

Las rutas `APF01/`, `APF02/` y `APF03/` son relativas a la carpeta `ProyectoFinal`, que contiene `Proyecto/trazalab`. El código verificable está bajo [`src/main/java`](../src/main/java) y las pruebas bajo [`src/test/java`](../src/test/java). `IncidenciaController` coordina la aplicación de consola; todavía no es un controlador HTTP.

## Evidencia observada

- El 27/10/2026, `.\mvnw.cmd -q -DskipTests compile` concluyó correctamente.
- El 27/10/2026, `.\mvnw.cmd -q test` ejecutó las dos pruebas de `RegistroIncidenciaJpaTest` con PostgreSQL temporal: 2 ejecutadas, 0 fallidas, 0 errores y 0 omitidas. Los resultados de esa ejecución se guardan en `target/surefire-reports`.
- Una consulta de solo lectura al PostgreSQL local el 27/10/2026 mostró 14 tablas, 4 vistas, 6 incidencias y 1 computadora. Es una observación puntual del entorno local, no el resultado del piloto.

## Referencias técnicas

- [Java SE 21](https://docs.oracle.com/en/java/javase/21/)
- [Spring Boot](https://docs.spring.io/spring-boot/index.html)
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/)
- [Apache PDFBox](https://pdfbox.apache.org/)
- [Apache POI](https://poi.apache.org/)
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/)
- [Testcontainers for Java](https://java.testcontainers.org/)

La exportación PDF, XLSX y CSV ya está implementada para el listado de incidencias. El análisis de fallas recurrentes y sus reportes siguen pendientes.
