# Versiones y fuentes de TrazaLab

Inventario de versiones declarado por el proyecto, revisado el 2026-10-15. Las dependencias sin versión directa son administradas por el parent de Spring Boot indicado aquí; la versión del servidor PostgreSQL aún no está definida.

| Componente | Versión declarada | Fuente |
|---|---:|---|
| Java | 21 | [`pom.xml`](../pom.xml), propiedad `java.version` |
| Spring Boot parent y plugin | 4.1.1 | [`pom.xml`](../pom.xml) |
| Spring Data JPA, Security y Validation | Administradas por Spring Boot 4.1.1 | [`pom.xml`](../pom.xml); Security está reservado para autenticación futura |
| Hibernate ORM | Administrado transitivamente por `spring-boot-starter-data-jpa` | [`pom.xml`](../pom.xml); proveedor JPA usado para el mapeo objeto-relacional |
| SLF4J y Logback | Administrados por Spring Boot 4.1.1 | Eventos de aplicación detrás de `LoggingAplicacion`; configuración de salida en `logback-spring.xml` |
| Driver PostgreSQL | Administrado por Spring Boot 4.1.1 | [`pom.xml`](../pom.xml); versión del servidor aún no fijada |
| Apache PDFBox | 3.0.8 | [`pom.xml`](../pom.xml) |
| Apache POI | 5.5.1 | [`pom.xml`](../pom.xml) |
| Apache Commons CSV | 1.14.1 | [`pom.xml`](../pom.xml) |
| Maven Wrapper | Maven 3.10.0; wrapper 3.3.4 | [`maven-wrapper.properties`](../.mvn/wrapper/maven-wrapper.properties) |
| Pruebas de Spring Boot y Testcontainers | Administradas por Spring Boot 4.1.1 | [`pom.xml`](../pom.xml) |
| Flyway | No instalado | Opción futura para versionar migraciones de PostgreSQL cuando evolucione el esquema |

## Fuentes del proyecto

- Requisitos funcionales y restricciones: `APF01/Requerimientos.xlsx` y `APF01/Project Charter, WBS, Gantt.xlsx`.
- Entidades, atributos, relaciones, reglas físicas y seguridad: `APF02/DiseñoDB, Relaciones, Entidades.xlsx`.
- Script PostgreSQL base: `APF02/Scripts/PostgreSQL Script.sql`; copia de trabajo en `database/01_trazalab_schema.sql`.
- Arquitectura, trazabilidad y pruebas propuestas: `APF03/Arquitectura_Semana_9_Proyecto_Integrador_v0.7.xlsx`, `APF03/Matriz_Trazabilidad_y_Transferencia.xlsx` y la copia S10 adjunta en `docs/`.
- Arquitectura implementada y objetivo por capas: [`Arquitectura.md`](Arquitectura.md); `IncidenciaController` es un controlador de aplicación de consola, no MVC/HTTP.
- Estado de implementación del prototipo: código bajo `src/main/java`, pruebas bajo `src/test/java` y [`README.md`](../README.md).

## Referencias oficiales

- [Java SE 21](https://docs.oracle.com/en/java/javase/21/)
- [Spring Boot Reference Documentation](https://docs.spring.io/spring-boot/index.html)
- [PostgreSQL JDBC Driver](https://jdbc.postgresql.org/)
- [Apache PDFBox](https://pdfbox.apache.org/)
- [Apache POI](https://poi.apache.org/)
- [Apache Commons CSV](https://commons.apache.org/proper/commons-csv/)
- [Apache Maven Wrapper](https://maven.apache.org/wrapper/)
- [Testcontainers for Java](https://java.testcontainers.org/)

Las versiones registradas describen el `pom.xml` de este checkout. Las dependencias de exportación son para etapas futuras y no implican que esas funciones estén disponibles en el flujo de consola actual.
