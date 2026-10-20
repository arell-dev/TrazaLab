# TrazaLab

TrazaLab registra y consulta la trazabilidad técnica de las computadoras de los laboratorios. La versión actual ofrece una interfaz de consola, persiste la información en PostgreSQL y permite generar archivos PDF, Excel y CSV con las incidencias registradas.

## Capacidades

| Capacidad | Descripción |
| :--- | :--- |
| Registro de incidencias | Asocia una computadora identificada por QR con un usuario reportante y almacena la descripción y fecha. |
| Consulta de incidencias | Lista los registros persistidos en PostgreSQL. |
| Historial por computadora | Busca un equipo por código QR y muestra sus datos básicos y sus incidencias en orden cronológico. |
| Exportación | Genera un archivo PDF, Excel (`.xlsx`) o CSV a partir de las incidencias consultadas. |
| Logging de aplicación | Registra acciones funcionales, resultado y datos operativos útiles para seguimiento. |

## Arquitectura

La consola es el adaptador de entrada actual. Los casos de uso se mantienen en los controladores y servicios, separados del acceso a datos y de los formatos de exportación.

```text
Consola
  ├─ Registro y consultas → DTO → IncidenciaController → RegistroIncidenciaPersistenteServicio
  │                                                           └─ Repositorios JPA → PostgreSQL
  └─ Exportación → ExportacionIncidenciasController → Exportadores PDF / Excel / CSV

IncidenciaController y ExportacionIncidenciasController → LoggingAplicacion → SLF4J / Logback
```

| Paquete | Responsabilidad |
| :--- | :--- |
| `console` | Presenta el menú, solicita datos y comunica resultados. |
| `controller` | Valida entradas de los casos de uso y coordina la operación. |
| `service` | Aplica reglas y coordina la consulta o escritura de incidencias. |
| `dto` | Define los datos que cruzan los límites entre capas. |
| `repository` | Implementa el acceso a datos mediante repositorios Spring Data JPA. |
| `model` | Contiene las entidades persistentes y estados del dominio. |
| `database` | Agrupa convertidores y detalles específicos de JPA. Hibernate valida las entidades frente al esquema SQL. |
| `export` | Define el contrato de exportación y genera archivos en PDF, Excel y CSV. |
| `logging` | Define el contrato de logging de aplicación y su adaptador actual. |
| `integration.collector` | Espacio previsto para la integración futura con la aplicación recolectora. |
| `web` | Espacio previsto para un adaptador web futuro. |

El controlador actual no expone rutas HTTP ni requiere una interfaz web. La aplicación recolectora de computadoras será un segundo sistema; el contrato de intercambio entre ambos aún está pendiente de definición.

## Estructura del proyecto

```text
.
├── database/
│   ├── 01_trazalab_schema.sql       # Esquema, restricciones y datos iniciales
│   ├── compose.yaml                # Servicio PostgreSQL para desarrollo
│   └── run-persistencia.ps1        # Carga variables y ejecuta el perfil persistente
├── docs/
│   ├── Arquitectura.md
│   ├── Matriz Trazabilidad.md
│   ├── Seguridad y Limites.md
│   ├── Versiones y Fuente.md
│   └── TrazaLab_Arquitectura_Trazabilidad.drawio
├── src/
│   ├── main/java/com/utp/trazalab/ # Capas de aplicación y adaptadores
│   ├── main/resources/             # Configuración Spring Boot y Logback
│   └── test/java/                  # Pruebas de integración JPA
├── .env.example                    # Variables de conexión de desarrollo
├── pom.xml
└── mvnw.cmd
```

## Tecnologías y dependencias

| Tecnología | Uso |
| :--- | :--- |
| Java 21 | Plataforma de ejecución. |
| Spring Boot 4.1.1 | Configuración y ciclo de vida de la aplicación. |
| Spring Data JPA e Hibernate | Repositorios, mapeo objeto-relacional y validación del esquema. |
| PostgreSQL 16 | Persistencia relacional. |
| Docker Compose | Ejecución local de PostgreSQL. |
| Apache PDFBox 3.0.8 | Generación del PDF. |
| Apache POI 5.5.1 | Generación del libro Excel. |
| Apache Commons CSV 1.14.1 | Escritura del archivo CSV. |
| JUnit y Testcontainers | Pruebas de integración con PostgreSQL temporal. |

El proyecto también incluye Spring Validation para validar los DTO y Spring Security como dependencia preparada para una etapa posterior. La consola actual no implementa autenticación. Las dependencias y versiones efectivas se declaran en [`pom.xml`](pom.xml).

## Requisitos

- JDK 21.
- Docker Desktop con Docker Compose.
- PowerShell en Windows.

## Configuración y ejecución

1. Crea la configuración local a partir de la plantilla:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Asigna una contraseña local a `SPRING_DATASOURCE_PASSWORD` en `.env`.
3. Desde la raíz del proyecto, inicia PostgreSQL:

   ```powershell
   docker compose --env-file .env -f database/compose.yaml up -d
   ```

4. Ejecuta TrazaLab:

   ```powershell
   .\database\run-persistencia.ps1
   ```

También puedes arrancar el perfil persistente directamente con Maven:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=persistencia"
```

Al iniciar, la consola mantiene abierto el menú hasta seleccionar `0. Salir`:

1. Consultar incidencias existentes.
2. Registrar una incidencia.
3. Consultar la ficha e historial de una computadora mediante su código QR.
4. Exportar incidencias a PDF, Excel o CSV.
0. Salir.

El esquema de [`database/01_trazalab_schema.sql`](database/01_trazalab_schema.sql) se aplica al crear el volumen por primera vez. Hibernate usa `spring.jpa.hibernate.ddl-auto=validate`: comprueba compatibilidad entre entidades y esquema, sin crear ni modificar tablas. Los registros iniciales de laboratorio se insertan en PostgreSQL de manera idempotente.

## Exportación

La opción 4 permite seleccionar PDF, Excel o CSV. Cada archivo contiene identificador, usuario reportante, código QR, descripción, fecha de registro y estado. Los archivos se guardan por defecto en `evidencias/`, con nombres como `incidencias_2026-10-15_15-36-28.csv`. La ruta se puede cambiar mediante `TRAZALAB_EXPORT_DIRECTORY`.

El PDF usa orientación apaisada, encabezados repetidos y paginación. El libro Excel presenta título, fecha de generación, total de registros, filtros, encabezados fijos y texto ajustado. El CSV usa UTF-8 con BOM para facilitar su apertura en Excel.

## Logging

Los eventos funcionales se escriben en `logs/trazalab-app.log`. `APP_EVENT` registra la acción, el recurso, el resultado y, cuando corresponde, una referencia, cantidad o detalle. Por ejemplo, `incidencia.registrar`, `incidencia.listar`, `computadora.historial` e `incidencia.exportar`. Los eventos exitosos usan `INFO`, los rechazos por validación `WARN` y los errores técnicos `ERROR`. El archivo rota por fecha y tamaño y conserva hasta 30 días.

## Pruebas

`RegistroIncidenciaJpaTest` verifica el registro persistente a través del servicio, las consultas y las validaciones con PostgreSQL temporal mediante Testcontainers. Docker debe estar disponible para ejecutar la prueba:

```powershell
.\mvnw.cmd test
```

Maven guarda los resultados de prueba en `target/surefire-reports`.

Otros comandos de desarrollo disponibles desde la raíz:

| Comando | Resultado |
| :--- | :--- |
| `.\mvnw.cmd -DskipTests compile` | Compila el código sin ejecutar pruebas. |
| `.\mvnw.cmd package` | Compila, ejecuta las pruebas y construye el JAR. |
| `docker compose --env-file .env -f database/compose.yaml down` | Detiene PostgreSQL y conserva el volumen de datos. |

## Alcance actual

La versión actual implementa registro, consulta e historial de incidencias, y exportación de esos registros. La aplicación funciona por consola y usa PostgreSQL. La interfaz web, autenticación de usuarios, gestión de mantenimientos y componentes, análisis de fallas recurrentes y comunicación con la aplicación recolectora C++ son etapas posteriores.

## Documentación técnica

- [Arquitectura](docs/Arquitectura.md)
- [Matriz de trazabilidad](docs/Matriz%20Trazabilidad.md)
- [Seguridad y límites](docs/Seguridad%20y%20Limites.md)
- [Versiones y fuentes](docs/Versiones%20y%20Fuente.md)
- [Diagrama de arquitectura y trazabilidad](docs/TrazaLab_Arquitectura_Trazabilidad.drawio)
- [Uso de PostgreSQL](database/README.md)
