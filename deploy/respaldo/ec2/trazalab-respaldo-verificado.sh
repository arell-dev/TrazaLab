#!/usr/bin/env bash
set -euo pipefail
umask 077
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
coordinador=/usr/local/lib/trazalab/coordinador.sh

if [[ ! -f "$coordinador" || -L "$coordinador" || "$(stat -c '%u:%a' "$coordinador")" != 0:644 || ! "${TRAZALAB_INSTANCIA_ID:-}" =~ ^i-[a-f0-9]{8,17}$ ]]; then
  echo 'Falta la identidad o la biblioteca protegida del coordinador externo.' >&2
  exit 1
fi
source "$coordinador"
if ! trazalab_coord_verificar_operacion despliegue "$TRAZALAB_INSTANCIA_ID"; then
  echo 'El respaldo requiere el permiso externo de despliegue que lo contiene.' >&2
  exit 1
fi
export AWS_PAGER=""
export AWS_SHARED_CREDENTIALS_FILE=/dev/null
export AWS_CONFIG_FILE=/dev/null
unset AWS_PROFILE AWS_DEFAULT_PROFILE AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN
unset AWS_WEB_IDENTITY_TOKEN_FILE AWS_ROLE_ARN AWS_CONTAINER_CREDENTIALS_RELATIVE_URI AWS_CONTAINER_CREDENTIALS_FULL_URI
unset AWS_EC2_METADATA_DISABLED AWS_ENDPOINT_URL AWS_ENDPOINT_URL_S3 AWS_ENDPOINT_URL_STS

# El llamador debe mantener /run/trazalab/operacion.lock durante toda la operación.
configuracion=/etc/trazalab/respaldo.conf
region=
cuenta=
bucket=
prefijo=
imagen='postgres@sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d'

if [[ ! -f "$configuracion" || -L "$configuracion" || "$(stat -c '%u:%a' "$configuracion")" != 0:600 ]]; then
  echo 'Falta la configuración root 0600 del respaldo.' >&2
  exit 1
fi
while IFS='=' read -r clave valor || [[ -n "$clave" ]]; do
  case "$clave" in
    ''|\#*) ;;
    AWS_REGION) region=$valor ;;
    AWS_ACCOUNT_ID) cuenta=$valor ;;
    S3_BUCKET) bucket=$valor ;;
    S3_PREFIX) prefijo=$valor ;;
    *) echo 'Clave de configuración de respaldo no reconocida.' >&2; exit 1 ;;
  esac
done < "$configuracion"
[[ "$region" == us-east-2 ]]
[[ "$cuenta" =~ ^[0-9]{12}$ ]]
[[ "$bucket" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]]
[[ "$prefijo" =~ ^[a-zA-Z0-9][a-zA-Z0-9/_-]{0,127}$ ]]
[[ "$prefijo" != *..* && "$prefijo" != */ ]]
test "$(id -u)" -eq 0
test -e /proc/$$/fd/9
test -f /run/trazalab/operacion.lock
test "$(stat -Lc '%d:%i' /proc/$$/fd/9)" = "$(stat -Lc '%d:%i' /run/trazalab/operacion.lock)"
exec 8</run/trazalab/operacion.lock
if flock -n 8; then
  echo 'El respaldo debe ejecutarse con el bloqueo exclusivo del llamador.' >&2
  exit 1
fi
test "$(findmnt -n -o FSTYPE --target /run)" = tmpfs
/usr/local/sbin/trazalab-verificar-datos
test "$(aws sts get-caller-identity --query Account --output text --region "$region")" = "$cuenta"
test "$(aws s3api get-bucket-versioning --bucket "$bucket" --expected-bucket-owner "$cuenta" --region "$region" --query Status --output text)" = Enabled

directorio=$(mktemp -d /run/trazalab-respaldo.XXXXXXXX)
pod="trazalab-restauracion-$(tr -d '-' < /proc/sys/kernel/random/uuid | cut -c 1-20)"
pod_creado=false
limpiar() {
  local codigo=$?
  trap - EXIT
  if [[ "$pod_creado" == true ]]; then
    if ! kubectl delete pod "$pod" --namespace default --wait=true --ignore-not-found=true --timeout=120s >/dev/null; then
      echo "No se confirmó la eliminación del pod temporal $pod." >&2
      codigo=1
    fi
  fi
  rm -f -- "$directorio/respaldado.dump" "$directorio/descargado.dump" "$directorio/pod.json" "$directorio/subida.json" "$directorio/descarga.json"
  rmdir -- "$directorio" || codigo=1
  exit "$codigo"
}
trap limpiar EXIT

# Evita agotar la RAM del nodo cuando /run comparte memoria con K3s.
ulimit -f 65536
timeout 180s kubectl exec deployment/trazalab-postgres -- sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --serializable-deferrable' > "$directorio/respaldado.dump"
test -s "$directorio/respaldado.dump"
hash=$(sha256sum "$directorio/respaldado.dump" | cut -d ' ' -f 1)
[[ "$hash" =~ ^[a-f0-9]{64}$ ]]
clave="${prefijo}/$(date -u +%Y/%m/%dT%H%M%SZ)-$(tr -d '-' < /proc/sys/kernel/random/uuid).dump"
aws s3api put-object --bucket "$bucket" --key "$clave" --body "$directorio/respaldado.dump" --server-side-encryption AES256 --checksum-algorithm SHA256 --metadata "sha256=$hash" --expected-bucket-owner "$cuenta" --region "$region" --output json > "$directorio/subida.json"
version=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1], encoding="utf-8")).get("VersionId", ""))' "$directorio/subida.json")
[[ -n "$version" && "$version" != null && "$version" != None ]]
aws s3api get-object --bucket "$bucket" --key "$clave" --version-id "$version" --expected-bucket-owner "$cuenta" --region "$region" "$directorio/descargado.dump" --output json > "$directorio/descarga.json"
python3 - "$directorio/descarga.json" "$hash" "$version" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as archivo:
    respuesta = json.load(archivo)
if respuesta.get("ServerSideEncryption") != "AES256":
    raise SystemExit("La copia externa no declara cifrado SSE-S3.")
if respuesta.get("VersionId") != sys.argv[3] or respuesta.get("Metadata", {}).get("sha256") != sys.argv[2]:
    raise SystemExit("La versión o metadatos de la copia externa no coinciden.")
PY
test "$(sha256sum "$directorio/descargado.dump" | cut -d ' ' -f 1)" = "$hash"

python3 - "$pod" "$imagen" > "$directorio/pod.json" <<'PY'
import json
import sys

nombre, imagen = sys.argv[1:]
pod = {
    "apiVersion": "v1", "kind": "Pod",
    "metadata": {"name": nombre, "namespace": "default", "labels": {"app.kubernetes.io/name": "trazalab-restauracion"}},
    "spec": {
        "restartPolicy": "Never", "automountServiceAccountToken": False, "activeDeadlineSeconds": 600,
        "securityContext": {"runAsNonRoot": True, "runAsUser": 999, "runAsGroup": 999, "fsGroup": 999, "seccompProfile": {"type": "RuntimeDefault"}},
        "containers": [{
            "name": "postgres", "image": imagen,
            "args": ["postgres", "-c", "listen_addresses="],
            "env": [{"name": "POSTGRES_HOST_AUTH_METHOD", "value": "trust"}, {"name": "PGDATA", "value": "/var/lib/postgresql/data/pgdata"}],
            "resources": {"requests": {"cpu": "50m", "memory": "128Mi"}, "limits": {"cpu": "1", "memory": "256Mi", "ephemeral-storage": "1Gi"}},
            "securityContext": {"allowPrivilegeEscalation": False, "capabilities": {"drop": ["ALL"]}},
            "readinessProbe": {"exec": {"command": ["pg_isready", "-h", "/var/run/postgresql", "-U", "postgres"]}, "periodSeconds": 5},
            "volumeMounts": [{"name": "datos", "mountPath": "/var/lib/postgresql/data"}]
        }],
        "volumes": [{"name": "datos", "emptyDir": {"sizeLimit": "1Gi"}}]
    }
}
json.dump(pod, sys.stdout)
PY
pod_creado=true
kubectl create -f "$directorio/pod.json" >/dev/null
kubectl wait --for=condition=Ready "pod/$pod" --namespace default --timeout=240s >/dev/null
kubectl exec "$pod" --namespace default -- sh -c '
  test "$(head -n 1 "$PGDATA/postmaster.pid")" = 1
  pg_isready -h /var/run/postgresql -U postgres
' >/dev/null
timeout 300s kubectl exec -i "$pod" --namespace default -- pg_restore --exit-on-error --no-owner --no-privileges -h /var/run/postgresql -U postgres -d postgres < "$directorio/descargado.dump"
validacion=$(kubectl exec "$pod" --namespace default -- psql -h /var/run/postgresql -U postgres -d postgres -X -A -t -v ON_ERROR_STOP=1 -c "SELECT (SELECT COUNT(*) FROM usuario WHERE estado = 'activo') > 0 AND (SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success) = 0 AND (SELECT COUNT(*) FROM flyway_schema_history WHERE success) > 0;")
test "$validacion" = t
echo "Respaldo cifrado restaurado y verificado: s3://$bucket/$clave (versión $version, SHA-256 $hash)."
