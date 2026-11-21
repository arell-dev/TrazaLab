#!/usr/bin/env bash
set -euo pipefail
umask 077

configuracion=/etc/trazalab/ecr-pull.conf
namespace=default
secreto=trazalab-ecr-pull
region=
registro=

if [[ ! -f "$configuracion" || -L "$configuracion" ]]; then
  echo "No existe el archivo de configuración requerido." >&2
  exit 1
fi

if [[ "$(stat -c '%u:%a' "$configuracion")" != 0:600 ]]; then
  echo "La configuración debe pertenecer a root y tener permisos 0600." >&2
  exit 1
fi

while IFS='=' read -r clave valor || [[ -n "$clave" ]]; do
  case "$clave" in
    ''|\#*) ;;
    AWS_REGION) region=$valor ;;
    ECR_REGISTRY) registro=$valor ;;
    *) echo "Clave de configuración no reconocida." >&2; exit 1 ;;
  esac
done < "$configuracion"

if [[ ! "$region" =~ ^[a-z0-9-]+$ ]] || [[ ! "$registro" =~ ^[0-9]{12}\.dkr\.ecr\.${region}\.amazonaws\.com$ ]]; then
  echo "La región o el registro ECR no son válidos." >&2
  exit 1
fi

directorio=$(mktemp -d /run/trazalab-ecr-pull.XXXXXXXX)
trap 'rm -rf -- "$directorio"' EXIT
archivo="$directorio/config.json"
export AWS_REGION="$region"
export AWS_DEFAULT_REGION="$region"
export AWS_PAGER=""
export AWS_SHARED_CREDENTIALS_FILE=/dev/null
export AWS_CONFIG_FILE=/dev/null
unset AWS_PROFILE AWS_DEFAULT_PROFILE AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN
unset AWS_WEB_IDENTITY_TOKEN_FILE AWS_ROLE_ARN AWS_CONTAINER_CREDENTIALS_RELATIVE_URI AWS_CONTAINER_CREDENTIALS_FULL_URI
unset AWS_EC2_METADATA_DISABLED AWS_ENDPOINT_URL AWS_ENDPOINT_URL_ECR

if ! k3s secrets-encrypt status | grep -q '^Encryption Status: Enabled$'; then
  echo "El cifrado de secretos de K3s no está habilitado." >&2
  exit 1
fi

aws ecr get-login-password --region "$region" | python3 -c '
import base64
import json
import sys

password = sys.stdin.read().rstrip("\r\n")
if not password:
    raise SystemExit("ECR devolvió una credencial vacía.")
authorization = base64.b64encode(("AWS:" + password).encode()).decode()
with open(sys.argv[1], "w", encoding="utf-8") as output:
    json.dump({"auths": {sys.argv[2]: {"auth": authorization}}}, output)
' "$archivo" "$registro"

kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml -n "$namespace" create secret generic "$secreto" \
  --type=kubernetes.io/dockerconfigjson \
  --from-file=".dockerconfigjson=$archivo" \
  --dry-run=client -o yaml | kubectl --kubeconfig /etc/rancher/k3s/k3s.yaml apply -f - >/dev/null

echo "Credencial ECR actualizada."
