# Despliegue académico en AWS

## Acceso

- Panel independiente para encender la instancia: [Abrir panel de TrazaLab](https://d2ltkzmo83k169.cloudfront.net/).
- Aplicación: [Iniciar sesión en TrazaLab](https://d1uf8hl2efayr4.cloudfront.net/usuarios/login).

Primero inicia sesión en el panel con una cuenta autorizada y solicita el encendido. Espera a que la aplicación esté disponible y abre el enlace de inicio de sesión. Usa una cuenta persistida de la aplicación; el acceso al panel y el acceso a TrazaLab son independientes. El inicio puede tardar varios minutos y, mientras tanto, la aplicación puede responder con un error temporal. Si la instancia está detenida, el panel sigue disponible, pero la aplicación no.

Se confirmó que el panel abre desde la red universitaria. Queda pendiente comprobar allí el encendido autorizado, el inicio de sesión en la aplicación y la consulta de datos. No compartas contraseñas, códigos ni tokens al comunicar resultados.

## Entorno actual

En la última comprobación del 20 de noviembre de 2026, el proyecto `trazalab` usaba la región `us-east-2`, la instancia EC2 `i-08eb755e51c5be97b` (`t3.small`) y K3s con despliegues separados para aplicación, PostgreSQL 16 y Caddy. La distribución de la aplicación es `E3FU9NHCVMQSTY` y la del panel `E1OUFMRUNUOAU5`. El volumen EBS de datos se monta por UUID; el volumen raíz y el volumen de datos son independientes.

La administración se realiza mediante Systems Manager, sin necesidad de abrir SSH o el API de Kubernetes al público. Verifica siempre la identidad del proyecto y la región antes de operar. La recuperación y las comprobaciones de la copia están documentadas en [RECUPERACION-COPIA-FASE1.md](./RECUPERACION-COPIA-FASE1.md); la organización y las limitaciones de los componentes operativos figuran en [deploy/README.md](../deploy/README.md).

El despliegue anterior en Fresh Canvas se retiró después de verificar la recuperación; sus antiguas direcciones, comandos SSH y rutas de servicio no corresponden al entorno actual. El 20 de noviembre, desde una ubicación distinta de la universidad, se comprobó el encendido desde el panel, el inicio de sesión, el registro de una incidencia y su consulta en la PC de prueba. La comprobación de esos flujos desde la red universitaria continúa pendiente.

## Operación y límites

El apagado automático todavía no está implementado. Al terminar las pruebas, detén la instancia manualmente cuando no haya operaciones activas. La instancia detenida no elimina los cargos posibles por almacenamiento, dirección IPv4 y otros recursos; revisa consumo y créditos en AWS Settings. Los prototipos de apagado y la automatización de publicación tienen comprobaciones pendientes y no deben considerarse operativos.

El CI con Java 21, Docker y PostgreSQL 16 pasó en el PR #24 y el cambio se integró en `main` mediante squash (`959d42d`). El rol OIDC está limitado a la publicación de TrazaLab en ECR, inicio de la EC2 prevista y ejecución del documento SSM autorizado. Las etiquetas `v*` tienen reglas de creación, actualización y eliminación restringidas a administradores. Aún no se ejecutó el workflow de publicación ni existe una versión `v0.0.1`.

La copia externa de migración es anterior a la incidencia registrada en la prueba. Después se creó y restauró una copia puntual con dos incidencias; sus datos y límites figuran en [guía de recuperación](./RECUPERACION-COPIA-FASE1.md). Antes de reemplazar la base o terminar la instancia, comprueba que exista una copia verificada que incluya los cambios posteriores. El respaldo automático verificado sigue pendiente.

AWS Organizations denegó al rol de la instancia el acceso S3 requerido para guardar y restaurar el respaldo automatizado. La publicación seguirá detenida hasta revisar la política organizativa y completar la restauración integral del respaldo.

Las pruebas de capacidad representativa, renovación real de acceso a ECR y validación de los flujos desde la universidad siguen pendientes. Se comprobó que el origen rechaza una distribución sin el encabezado compartido y admite la distribución autorizada.
