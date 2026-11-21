# Publicación de versiones de TrazaLab

El flujo por etiquetas está preparado localmente. En la fase 3 se añadieron y validaron permisos OIDC de mínimo alcance, protección GitHub para etiquetas `v*` y acceso de respaldo EC2 a un prefijo S3 aislado. El flujo completo de GitHub, la restauración integrada en K3s y el despliegue todavía requieren evidencia real antes de crear `v0.0.1`.

## Flujo

Una etiqueta `vX.Y.Z` llama a la integración continua reutilizable. El flujo exige que el commit etiquetado pertenezca a `main`, recupera la imagen que superó las pruebas y comprueba el SHA-256 del artefacto y su identificador. Publica esa misma imagen en ECR, conserva el digest y solicita el despliegue mediante el documento SSM `TrazaLabDeploy`. Las ejecuciones se serializan, pero el orden de llegada a la cola no garantiza el orden de las versiones; el servidor rechaza una versión anterior.

La etiqueta en ECR es inmutable. Un reintento puede reutilizarla solo si el digest vuelve a coincidir. Si una reconstrucción del mismo commit produce un digest diferente, el flujo falla y se debe investigar; no se sobrescribe la imagen publicada. Las etiquetas `build-<run>-<attempt>` son intermedias y necesitan una política de retención revisada, que no borre versiones necesarias para recuperación.

## Preparación obligatoria

- El entorno `produccion` de GitHub está limitado a etiquetas `v*` y conserva las variables requeridas. Una regla del repositorio limita creación, actualización y eliminación de esas etiquetas a administradores con capacidad de omitir reglas.
- Configurar las variables del entorno: `AWS_ACCOUNT_ID`, `AWS_DEPLOY_ROLE_ARN`, `AWS_INSTANCE_ID`, `AWS_DEPLOY_DOCUMENT` y `AWS_DEPLOY_DOCUMENT_VERSION`. El flujo exige el rol `trazalab-github-deploy`, el documento `TrazaLabDeploy` y un número de versión explícito del documento. La región y el repositorio son `us-east-2` y `trazalab`.
- El proveedor OIDC y la confianza del rol están limitados al repositorio `arell-dev/TrazaLab`, al entorno `produccion` y a `sts.amazonaws.com`. El rol permite publicar en ECR `trazalab`, iniciar la EC2 prevista y ejecutar `TrazaLabDeploy` sobre esa instancia. La política pasó Access Analyzer y las simulaciones de recursos permitidos y denegados. No puede editar el documento, invocar `AWS-RunShellScript` ni modificar roles.
- Instalar `trazalab-desplegar.sh` como `/usr/local/sbin/trazalab-desplegar`, propiedad de root y sin escritura para otros usuarios. Crear y revisar el documento SSM a partir de `documento-despliegue-ssm.yaml`; registrar su versión numérica y verificar la versión del agente SSM compatible con `interpolationType: ENV_VAR`.
- Revisar `../respaldo/RESPALDO.md` y probar integralmente el ayudante de respaldo preparado antes de instalarlo como `/usr/local/sbin/trazalab-respaldo-verificado`, archivo regular propiedad de root, no enlazado y con modo 0700, 0750 o 0755. Debe generar un respaldo consistente cifrado fuera de la instancia, restaurarlo de forma aislada y comprobarlo antes de devolver código cero. Su salida no debe contener datos personales ni credenciales. Sin él, el despliegue falla antes de modificar la aplicación.
- El rol `trazalab-ec2-ssm` tiene acceso S3 limitado a lectura del versionado del bucket y escritura SSE-S3 y lectura de versiones dentro de `respaldos/trazalab/`; no tiene permiso de borrado. El prefijo tiene una regla propia de expiración a 365 días y eliminación de versiones no actuales a los 30 días. La regla histórica `fase1/` se conserva.
- Revisar y aplicar por separado los manifiestos de `deploy/k3s`. Calcular su huella desde el commit autorizado y guardarla como `/etc/trazalab/manifiestos.sha256`, propiedad de root y modo 0600. Si cambia cualquier archivo versionado bajo `deploy/k3s`, se rechazará la publicación hasta revisar y actualizar el entorno y su huella. El flujo solo cambia la imagen: no aplica automáticamente cambios de manifiestos.
- Verificar que `/usr/local/sbin/trazalab-verificar-datos` compruebe el montaje y UUID del volumen persistente, que la renovación ECR esté instalada y que el Deployment use el Secret `trazalab-ecr-pull` y la política de imagen adecuada.
- Hacer que el apagado automático, los respaldos y cualquier otra operación que pueda interrumpir el sistema utilicen el mismo bloqueo exclusivo `/run/trazalab/operacion.lock`. Hasta que eso esté implementado y probado, no está garantizado que una parada automática espere al despliegue. El procedimiento de respaldo invocado por el ayudante ya se ejecuta bajo ese bloqueo y no debe intentar adquirirlo una segunda vez.

Para calcular la huella en Linux desde la raíz del repositorio y el commit que se revisará:

```bash
git ls-files -z deploy/k3s | LC_ALL=C sort -z | xargs -0 sha256sum | sha256sum | cut -d ' ' -f 1
```

La carpeta aún no estaba versionada en la revisión del 20 de noviembre; por tanto, esa huella de commit todavía no se ha comprobado y no debe usarse una lista vacía como evidencia.

## Fallos y recuperación

El ayudante escribe una marca `pendiente` antes de cambiar la imagen. Si se interrumpe o falla, el siguiente intento se detiene para que un administrador revise el estado y el respaldo. La referencia anterior queda en `/var/lib/trazalab-data/despliegues/imagen-anterior`; restaurar solo la imagen no revierte las migraciones Flyway. No eliminar la marca ni forzar el despliegue hasta determinar la compatibilidad del esquema o restaurar el respaldo comprobado.

Un fallo después de iniciar EC2 puede dejarla encendida. Mientras no exista la coordinación probada con el apagado por inactividad, revisar su estado en AWS y detenerla cuando corresponda. La verificación mediante los endpoints internos no sustituye la prueba de HTTPS, login ni acceso desde la universidad.

Si ocurre un fallo o una cancelación después de obtener credenciales AWS, el resumen de GitHub consulta el estado de EC2 y, cuando se conoce, el del comando SSM. También indica si el flujo solicitó el arranque. Si el envío de SSM pudo haberse iniciado pero no devolvió un identificador, se advierte que debe revisarse Systems Manager. El flujo no apaga EC2 automáticamente: podría haber usuarios, un respaldo o una operación SSM activa, y todavía no existe un bloqueo compartido que garantice una parada segura. Una cancelación forzada o el vencimiento del tiempo máximo del job puede impedir incluso este resumen; en ese caso, comprobar manualmente AWS.

## Prueba aislada de bloqueos

Desde PowerShell, en la raíz del repositorio, se puede probar que el ayudante rechaza el despliegue si falta el procedimiento de respaldo o si la huella de manifiestos no coincide:

```powershell
$carpetaPruebas = (Resolve-Path -LiteralPath 'deploy\publicacion').Path
docker run --rm --network none --user 0 --mount "type=bind,source=$carpetaPruebas,target=/prueba,readonly" --entrypoint bash postgres:16 /prueba/pruebas/probar-bloqueos-despliegue.sh
```

Esta prueba usa comandos simulados dentro de un contenedor temporal. No ejecuta el workflow en GitHub, no consulta AWS y no demuestra que exista un procedimiento de respaldo real.
