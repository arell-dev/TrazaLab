# Despliegue y operación

Este directorio organiza la infraestructura y la operación de TrazaLab en AWS. La aplicación Spring Boot y su documentación funcional permanecen en la raíz del repositorio.

## Componentes

- arranque/ contiene el panel que consulta el estado y solicita el encendido de EC2; sus plantillas CloudFormation están en arranque/cloudformation/.
- apagado/ contiene una sola implementación local del apagado seguro, la recuperación al iniciar EC2 y sus unidades de systemd. Ningún temporizador está habilitado y el módulo no se ha desplegado.
- k3s/ contiene los manifiestos de TrazaLab, PostgreSQL 16, Caddy y la renovación de acceso a ECR.
- publicacion/ organiza el helper de EC2, el documento SSM, la política IAM, las pruebas y la guía de publicación.
- respaldo/ separa el helper y configuración de EC2, las pruebas y la restauración histórica.
- laboratorio/ conserva los ensayos de carga y capacidad; no contiene una segunda implementación del apagado.

Consulta la guía de [alojamiento del panel](arranque/Alojamiento.md), el [apagado seguro](apagado/README.md), la [publicación](publicacion/README.md) y el [respaldo](respaldo/README.md).

## Acceso

- Panel para encender TrazaLab: https://d2ltkzmo83k169.cloudfront.net/
- Aplicación: https://d1uf8hl2efayr4.cloudfront.net/usuarios/login

El panel y la aplicación usan autenticaciones independientes. Espera que el panel confirme la disponibilidad antes de iniciar sesión. PostgreSQL no está expuesto públicamente y sus datos persisten en EBS.

## Estado de operación

La cuenta permanece en el plan Free y en us-east-2. TrazaLab, PostgreSQL 16 y Caddy corren en K3s sobre la instancia EC2 i-08eb755e51c5be97b. PostgreSQL persiste en el volumen cifrado vol-0edcf3f9660ba148c, montado en /var/lib/trazalab-data; AWS informa DeleteOnTermination=false para ese volumen y true para la raíz. El apagado del sistema operativo está configurado como stop. Detener EC2 conserva ambos volúmenes; terminar la instancia elimina el volumen raíz y conserva el volumen de datos.

La versión v0.0.1 está desplegada. El run 44958085145 verificó la publicación ECR, el despliegue por SSM y los endpoints /health, /db-health y /api/health. La inspección posterior confirmó TrazaLab, PostgreSQL y Caddy listos, y la PVC trazalab-postgres Bound de 10 GiB. El respaldo de producción se restauró y verificó en PostgreSQL aislado.

La validación desde la red universitaria comprobó el acceso a ambos enlaces, el inicio de sesión y la consulta de datos. Sigue pendiente comprobar allí el encendido real desde el panel.

## Costos y parada

El panel puede solicitar que se encienda EC2 aunque la instancia esté detenida. El workflow de publicación también la inicia si hace falta. El módulo local coordina esos actores y recupera PostgreSQL antes de TrazaLab al iniciar EC2, pero no está desplegado ni habilitado; la parada sigue siendo manual. Antes de activarlo, verifica el ciclo real de encendido y recuperación, la regla de EventBridge, los permisos, el respaldo y la persistencia de PostgreSQL. El apagado desde el sistema operativo está configurado como stop. Al terminar, detén EC2 manualmente cuando no haya operaciones activas.

El plan Free conserva límites y posibles cargos por EC2, EBS, direcciones IP, S3 y CloudFront. Verifica créditos y uso en AWS antes de operaciones prolongadas. No se necesita AWS Organizations para el despliegue actual.
