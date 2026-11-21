# Publicación y despliegue

Los workflows están en .github/workflows/ci.yml y release.yml. La publicación se inicia al subir una etiqueta Git vX.Y.Z. El workflow vuelve a verificar el commit etiquetado, prepara la imagen comprobada y solicita el despliegue en EC2 mediante OIDC y Systems Manager (SSM).

## Publicar una versión

1. Integra los cambios en main y espera que la CI termine correctamente. Un push a main ejecuta CI, pero no despliega.
2. Usa la siguiente versión válida. Después de v0.0.1 corresponde v0.0.2; la etiqueta debe apuntar a un commit ya integrado en main.
3. Desde main, crea y publica la etiqueta Git. Por ejemplo, para v0.0.2:

    git switch main
    git pull --ff-only origin main
    git tag v0.0.2
    git push origin v0.0.2

No crees la GitHub Release manualmente. La etiqueta dispara release.yml; primero corre Maven/Testcontainers, construye la imagen Docker y ejecuta el smoke test con PostgreSQL 16. Si todo pasa, publica el digest en ECR, inicia EC2 si está detenida y solicita por SSM el despliegue en K3s. El documento de despliegue comprueba el respaldo y la restauración, el rollout y los endpoints /health, /db-health y /api/health. GitHub crea la Release solo después de confirmar el éxito en AWS.

Las versiones deben seguir vX.Y.Z. Se incrementa un solo componente exactamente en uno y se reinician los componentes inferiores. El workflow valida la etiqueta contra la última GitHub Release estable y el commit debe pertenecer a main.

## Si ocurre un error

- Revisa el job, el paso, el resumen del run y, si se envió, el CommandId y la salida de SSM. Un run fallido de GitHub no demuestra por sí solo que el despliegue en AWS haya fallado: puede fallar un paso posterior.
- Si se confirma que el flujo falló antes de enviar SSM, no cambió el clúster por ese intento. Corrige la causa; para reintentar la misma etiqueta, ejecuta release.yml con workflow_dispatch desde main e indica la etiqueta existente.
- La etiqueta ECR de versión es inmutable. Si ya existe, el workflow corregido reutiliza y comprueba su digest; no reconstruye ni reemplaza esa versión. No muevas etiquetas Git ni borres o sobrescribas etiquetas ECR para ocultar un fallo.
- Si SSM recibió el comando, el estado es incierto o existe una marca de despliegue pendiente, no hagas otro reintento todavía. Comprueba SSM, EC2, el Deployment y el digest, PostgreSQL, su PVC y los datos respaldados; reconcilia el estado con el procedimiento de operación antes de decidir.
- Si hace falta cambiar el código después de etiquetar, no apuntes la etiqueta existente a otro commit. Resuelve primero el estado de AWS y la secuencia de versiones; documenta qué digest corresponde a cada artefacto.

## Entorno y estado publicado

TrazaLab corre en K3s con PostgreSQL 16 persistente y Caddy en EC2. El panel CloudFront permanece disponible para solicitar el encendido; no se habilitó el apagado automático. La publicación mantiene la cuenta en el plan Free y no requiere AWS Organizations.

La release v0.0.1 se publicó y desplegó correctamente en el run [44958085145](https://github.com/arell-dev/TrazaLab/actions/runs/44958085145), desde el commit 946145e. Reutilizó el digest ECR sha256:2e80cd6eadebe5a3bd2063354838be30b408d861c1c7eea95f5c7f1d88d7d5b4; el comando SSM 70417665-f7e0-4aba-903f-4ca7fa839c12 terminó en Success. El run comprobó respaldo y restauración PostgreSQL, rollout y los tres endpoints. La inspección posterior confirmó TrazaLab, PostgreSQL y Caddy listos, y la PVC Bound de 10 GiB.

La responsable confirmó que el panel y la aplicación son accesibles desde la red universitaria. Falta que verifique el login y la consulta de datos desde esa red.
