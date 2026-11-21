# Despliegue y operación

- `arranque/`: panel independiente, funciones y plantillas CloudFormation; incluye sus pruebas.
- `k3s/`: manifiestos y renovación de acceso a ECR. La huella del flujo de publicación cubre todos los archivos versionados de esta carpeta; no se ha cambiado su contenido ni su ruta.
- `publicacion/`: ayudante de despliegue, documento SSM, política candidata y pruebas. El flujo aún requiere las verificaciones indicadas en `publicacion/README.md`.
- `respaldo/`: recuperación de las copias de migración y ayudante de respaldo propuesto, configuración de ejemplo y pruebas simuladas. Consultar `respaldo/RESPALDO.md` antes de instalarlo.
- `apagado-experimental/`: diseños y prototipos de coordinación y apagado con pruebas locales. No están habilitados; la definición ASL es insegura y no debe desplegarse.
- `pruebas-carga/`: ensayo sintético en contenedores locales aislados; no mide la capacidad de EC2 ni valida el piloto.

Cada carpeta de operación contiene sus pruebas en `pruebas/`. Los archivos generados por Python no forman parte del despliegue. Las rutas locales no modifican la versión del documento SSM ni los ayudantes ya instalados en EC2: deben revisarse y actualizarse por separado antes de habilitar la publicación. No se han aplicado cambios remotos por esta reorganización.
