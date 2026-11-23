# Publicación y despliegue

Los workflows están en .github/workflows/ci.yml y release.yml. La publicación se inicia al subir una etiqueta Git vX.Y.Z. El workflow vuelve a verificar el commit etiquetado, prepara la imagen comprobada y solicita el despliegue en EC2 mediante OIDC y Systems Manager (SSM).

## Publicar una versión

1. Integra los cambios en main y espera que la CI termine correctamente. Un push a main ejecuta CI, pero no despliega.
2. Usa una versión SemVer mayor que la última Release estable; la etiqueta debe apuntar a un commit ya integrado en main. Para esta publicación se autorizó `v0.0.11` después de `v0.0.1`.
3. Desde main, crea y publica la etiqueta Git. Para esta publicación:

    git switch main
    git pull --ff-only origin main
    git tag v0.0.11
    git push origin v0.0.11

No crees la GitHub Release manualmente. La etiqueta dispara release.yml; primero corre Maven/Testcontainers, construye la imagen Docker y ejecuta el smoke test con PostgreSQL 16. Si todo pasa, publica el digest en ECR, inicia EC2 si está detenida y solicita por SSM el despliegue en K3s. El documento de despliegue comprueba el respaldo y la restauración, el rollout y los endpoints /health, /db-health y /api/health. GitHub crea la Release solo después de confirmar el éxito en AWS.

Las versiones deben seguir vX.Y.Z, sin ceros iniciales. Se busca el primer componente distinto respecto de la última Release: debe ser mayor; los componentes superiores se conservan y los inferiores se reinician a cero. Se permiten saltos en el componente elegido, por ejemplo `v0.0.1` a `v0.0.11`, luego `v0.0.12`, o `v0.0.1` a `v0.1.0`. La primera publicación sigue siendo `v0.0.1`. El workflow valida la etiqueta contra la última GitHub Release estable y el commit debe pertenecer a main.

## Si ocurre un error

- Revisa el job, el paso, el resumen del run y, si se envió, el CommandId y la salida de SSM. Un run fallido de GitHub no demuestra por sí solo que el despliegue en AWS haya fallado: puede fallar un paso posterior.
- Si se confirma que el flujo falló antes de enviar SSM, no cambió el clúster por ese intento. Corrige la causa; para reintentar la misma etiqueta, ejecuta release.yml con workflow_dispatch desde main e indica la etiqueta existente.
- La etiqueta ECR de versión es inmutable. Si ya existe, el workflow corregido reutiliza y comprueba su digest; no reconstruye ni reemplaza esa versión. No muevas etiquetas Git ni borres o sobrescribas etiquetas ECR para ocultar un fallo.
- Si SSM recibió el comando, el estado es incierto o existe una marca de despliegue pendiente, no hagas otro reintento todavía. Comprueba SSM, EC2, el Deployment y el digest, PostgreSQL, su PVC y los datos respaldados; reconcilia el estado con el procedimiento de operación antes de decidir.
- Si hace falta cambiar el código después de etiquetar, no apuntes la etiqueta existente a otro commit. Resuelve primero el estado de AWS y la secuencia de versiones; documenta qué digest corresponde a cada artefacto.

## Validaciones automatizadas

La CI comprueba las reglas de versionado, que la etiqueta apunte a un commit integrado en `main`, que la publicación espere a los jobs `validar` y `verificar`, la cola de publicaciones, el encendido de EC2 y las respuestas de SSM. También valida la sintaxis de los scripts Bash y ejecuta pruebas aisladas de respaldo, huella, concurrencia, interrupción, ECR, rollout y salud. Estas pruebas no modifican AWS ni Kubernetes.

Las plantillas `deploy/arranque/cloudformation/infraestructura.yaml` y `deploy/arranque/cloudformation/alojamiento.yaml` pasaron `cfn-lint` 1.61.0 y `cloudformation.ValidateTemplate` para `us-east-2` el 21/11/2026. La primera requiere `CAPABILITY_IAM` para crear o actualizar el stack. La validación no desplegó recursos ni comprueba parámetros concretos de una futura ejecución.

Para repetir las barreras del despliegue en PowerShell:

```powershell
$repo = (Resolve-Path -LiteralPath '.').Path
docker run --rm --network none --user 0:0 `
  --mount "type=bind,source=$repo,target=/prueba,readonly" `
  --entrypoint bash postgres:16 `
  /prueba/deploy/publicacion/pruebas/probar-bloqueos-despliegue.sh
```

## Coordinación de operaciones

Los actores usan la tabla local trazalab-operacion, definida en arranque/cloudformation/infraestructura.yaml. La escritura condicional permite un solo permiso activo. No hay vencimiento automático: ante estado incierto, el permiso queda retenido para reconciliación manual. No se desplegó la tabla ni la regla EventBridge.

- El panel conserva arranque mientras EC2 está pending; la transición confirmada a running lo libera.
- GitHub Actions adquiere despliegue antes de iniciar EC2 y lo conserva hasta la confirmación satisfactoria de SSM. Si el inicio o SSM queda incierto, detén los reintentos y reconcilia el estado.
- El documento SSM verifica el permiso despliegue y pasa el InstanceId autorizado al helper. Antes del respaldo, este valida el PVC, el PV y el montaje; si las réplicas quedaron en cero tras un apagado previo, activa PostgreSQL y después TrazaLab mientras conserva el permiso despliegue.
- El respaldo se ejecuta dentro de ese mismo permiso y del bloqueo local. No hay un actor de respaldo independiente.
- El worker adquiere apagado y lo conserva hasta que EventBridge confirma stopped. Un evento solo se acepta después de comprobar cuenta, región, instancia y estado real.
- Al iniciar EC2, el temporizador local de recuperación intenta restaurar los servicios hasta 30 veces, con un minuto entre intentos. Se detiene al confirmar servicios activos o al requerir conciliación manual. Habilítalo y valida la recuperación antes de considerar habilitar el apagado automático.

La plantilla IAM del rol GitHub está en iam/politica-github-aws.json; la política prevista para el rol de EC2 está en ../apagado/iam/politica-instancia.json. Revísalas junto con la plantilla antes de aplicarlas. El despliegue, la activación del temporizador y una prueba real de encendido y apagado siguen pendientes.

## Historial operativo del panel

La mejora en esta rama configura el grupo de CloudWatch Logs de la Lambda del panel con retención de 30 días. `GET /historial` requiere una sesión Cognito del grupo `operadores`. Los operadores consultan hasta 1000 eventos recientes de solicitudes de encendido, transiciones EC2 y disponibilidad observada por HTTPS. El panel agrupa estos eventos por día.

Cuando SSM confirma el éxito del despliegue, el workflow guarda la versión, el commit, el digest, la fecha y el identificador SSM en el elemento `trazalab-ultimo-despliegue` de la tabla existente. También intenta registrar el evento en el grupo de CloudWatch. El elemento conserva el último despliegue registrado aunque los eventos caduquen a los 30 días. Es una confirmación histórica, sujeta a la fecha mostrada, y no una lectura directa del estado actual de Kubernetes.

Si alguno de estos registros falla, el resumen del workflow muestra una advertencia y la publicación continúa con el resultado ya confirmado por SSM. Revisa SSM, el digest y la instancia antes de reconciliar los metadatos; un registro anterior puede estar desactualizado. Si no hay un elemento corroborado, el panel debe mostrar la versión como desconocida.

Antes de dar la mejora por operativa, comprueba que API Gateway aplica la autorización a `GET /historial` y que el rol de la Lambda puede consultar el grupo y leer únicamente la clave de metadatos asignada. Comprueba que el rol de publicación puede escribir esa clave y el flujo `despliegues-verificados`. Esta documentación describe el cambio local, pendiente de publicación y comprobación en AWS.

## Entorno y estado publicado

TrazaLab corre en K3s con PostgreSQL 16 persistente y Caddy en EC2. El panel CloudFront permanece disponible para solicitar el encendido; no se habilitó el apagado automático. La publicación mantiene la cuenta en el plan Free y no requiere AWS Organizations.

La release v0.0.1 se publicó y desplegó correctamente en el run [44958085145](https://github.com/arell-dev/TrazaLab/actions/runs/44958085145), desde el commit 946145e. Reutilizó el digest ECR sha256:2e80cd6eadebe5a3bd2063354838be30b408d861c1c7eea95f5c7f1d88d7d5b4; el comando SSM 70417665-f7e0-4aba-903f-4ca7fa839c12 terminó en Success. El run comprobó respaldo y restauración PostgreSQL, rollout y los tres endpoints. La inspección posterior confirmó TrazaLab, PostgreSQL y Caddy listos, y la PVC Bound de 10 GiB.

La validación desde la red universitaria comprobó que el panel y la aplicación son accesibles, y que funcionan el inicio de sesión y la consulta de datos. El encendido real desde el panel sigue pendiente de comprobación.
