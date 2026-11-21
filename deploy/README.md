# Despliegue y operación

Este directorio organiza la infraestructura y operación de TrazaLab en AWS. La aplicación Spring Boot y su documentación funcional permanecen en la raíz del repositorio.

## Componentes

- arranque/ contiene el panel que consulta el estado y solicita el encendido de EC2.
- k3s/ contiene los manifiestos de TrazaLab, PostgreSQL 16, Caddy y la renovación de acceso a ECR.
- publicacion/ explica la CI, publicación de versiones, despliegue y recuperación ante fallos.
- respaldo/ documenta las copias PostgreSQL en S3 y la restauración.
- laboratorio/ conserva prototipos que no forman parte de la operación.

Consulta la guía de [alojamiento del panel](arranque/Alojamiento.md), la [publicación](publicacion/README.md) y el [respaldo](respaldo/README.md).

## Acceso

- Panel para encender TrazaLab: https://d2ltkzmo83k169.cloudfront.net/
- Aplicación: https://d1uf8hl2efayr4.cloudfront.net/usuarios/login

El panel y la aplicación usan autenticaciones independientes. Espera que el panel confirme la disponibilidad antes de iniciar sesión. PostgreSQL no está expuesto públicamente y sus datos persisten en EBS.

## Estado de operación

La cuenta permanece en el plan Free y en us-east-2. TrazaLab, PostgreSQL 16 y Caddy corren en K3s sobre la instancia EC2 i-08eb755e51c5be97b. PostgreSQL persiste en el volumen cifrado vol-0edcf3f9660ba148c, montado en /var/lib/trazalab-data; el volumen se conserva al terminar EC2.

La versión v0.0.1 está desplegada. El run 44958085145 verificó la publicación ECR, el despliegue por SSM y los endpoints /health, /db-health y /api/health. La inspección posterior confirmó TrazaLab, PostgreSQL y Caddy listos, y la PVC trazalab-postgres Bound de 10 GiB. El respaldo de producción se restauró y verificó en PostgreSQL aislado.

La responsable reportó que los dos enlaces son accesibles desde la red universitaria. Queda verificar allí el encendido desde el panel, el login y la consulta de datos.

## Costos y parada

El panel puede solicitar que se encienda EC2 aunque la instancia esté detenida. El workflow de publicación también la inicia si hace falta. El apagado automático sigue deshabilitado porque sus prototipos no coordinan de forma segura con el panel, la publicación y las operaciones de PostgreSQL. Al terminar, detén EC2 manualmente cuando no haya operaciones activas.

El plan Free conserva límites y posibles cargos por EC2, EBS, direcciones IP, S3 y CloudFront. Verifica créditos y uso en AWS antes de operaciones prolongadas. No se necesita AWS Organizations para el despliegue actual.
