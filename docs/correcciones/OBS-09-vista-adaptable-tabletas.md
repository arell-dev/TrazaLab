# OBS-09: vista comprimida en tabletas

## Observación

En una ventana de 768 px, la navegación lateral permanecía fija porque el breakpoint cubría solo pantallas menores a 768 px. El espacio restante comprimía los campos de Mantenimientos y las columnas de Incidencias.

## Corrección

Se extendió el breakpoint de navegación adaptable hasta 991.98 px. En ese rango, la navegación pasa a la barra inferior y el contenido utiliza el ancho disponible.

## Verificación

- Se revisaron Incidencias, Mantenimientos y Reportes en el navegador local con una ventana de 768 px.
- La consulta QR de `PC-001` mostró los datos del equipo y sus cinco incidencias recientes.
- El menú de Reportes conserva CSV, Excel y PDF; Incidencias no muestra controles de exportación.
- `mvnw.cmd -q test` finalizó correctamente.
