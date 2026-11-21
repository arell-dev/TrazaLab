# Despliegue y operación

`deploy/` contiene infraestructura y operación de AWS. La aplicación Spring Boot y su documentación funcional permanecen en la raíz del repositorio.

## Componentes

- `arranque/` publica el panel independiente que consulta el estado y solicita el encendido de EC2.
- `k3s/` contiene los manifiestos de TrazaLab, PostgreSQL 16, Caddy y la renovación de acceso a ECR.
- `publicacion/` prepara la imagen comprobada y solicita su despliegue por Systems Manager.
- `respaldo/` verifica copias PostgreSQL en S3 y documenta la recuperación de las copias históricas.
- `laboratorio/` conserva prototipos y ensayos que todavía no forman parte de la operación.

La guía de alojamiento del [panel para encender TrazaLab](arranque/Alojamiento.md) describe su distribución y los límites de sus pruebas. Los requisitos y evidencias del flujo de publicación están en [`publicacion/README.md`](publicacion/README.md), y las copias y restauraciones en [`respaldo/README.md`](respaldo/README.md).

## Acceso

- Panel para encender TrazaLab: [Abrir panel](https://d2ltkzmo83k169.cloudfront.net/).
- Aplicación: [Iniciar sesión](https://d1uf8hl2efayr4.cloudfront.net/usuarios/login).

Inicia sesión en el panel con una cuenta autorizada y solicita el encendido. Espera la confirmación de disponibilidad antes de abrir la aplicación. El panel y TrazaLab usan autenticaciones independientes. La base PostgreSQL permanece en el EBS de datos y no está expuesta públicamente.

## Entorno y evidencia actual

El proyecto AWS permanece en el plan Free, en `us-east-2`. La instancia K3s es `i-08eb755e51c5be97b`; TrazaLab, PostgreSQL 16 y Caddy corren en despliegues separados. PostgreSQL persiste en `vol-0edcf3f9660ba148c`, montado en `/var/lib/trazalab-data` y comprobado por UUID antes de operar. El volumen raíz es independiente.

El panel y la distribución de la aplicación están habilitados. El panel se sirve aunque EC2 esté detenida. Los endpoints de salud internos se prueban desde el despliegue. La aplicación también respondió por HTTPS durante la comprobación operativa del 21 de noviembre de 2026. La validación completa desde la red universitaria sigue pendiente.

El respaldo de producción se instaló y ejecutó desde EC2 con el rol `trazalab-ec2-ssm`. El 21 de noviembre de 2026, S3 confirmó una versión cifrada y el helper la descargó, restauró en un pod PostgreSQL aislado y verificó la base. Los datos de recuperación están en [`respaldo/README.md`](respaldo/README.md).

El documento SSM `TrazaLabDeploy` está activo en la versión 1. Los helpers de respaldo y despliegue están instalados en EC2 con permisos de root. La huella `0283e3c34b6982949afbb85a467e996e644b9d6291f2066732b1a0da00276e20` está registrada en `/etc/trazalab/manifiestos.sha256` y corresponde a los 13 archivos de `deploy/k3s` revisados y aplicados. La publicación automática todavía no se ha ejecutado: faltan la comprobación de las variables del entorno GitHub `produccion` y una ejecución satisfactoria de Actions. No crear `v0.0.1` antes de esas comprobaciones.

## Operación y costos

El apagado automático después de 30 minutos sigue pendiente. Los prototipos de `laboratorio/apagado-experimental/` no se deben habilitar: su flujo de parada todavía no coordina de forma segura con el panel, SSM y los despliegues. Detén EC2 manualmente al acabar cuando no haya operaciones activas. La distribución CloudFront del panel queda disponible para volver a encenderla.

El plan Free sigue sujeto a créditos y límites de uso. EC2, EBS, direcciones IP, almacenamiento S3, solicitudes y CloudFront pueden consumir créditos aunque la aplicación esté detenida. Comprueba el estado y los créditos en AWS Settings antes de operaciones prolongadas. No se requiere AWS Organizations para el procedimiento de respaldo que acaba de pasar.

Las rutas locales no actualizan los manifiestos del clúster ni el documento SSM instalado. Aplica cambios operativos solo después de revisar sus huellas, su efecto sobre PostgreSQL y la posibilidad de recuperar la instancia.
