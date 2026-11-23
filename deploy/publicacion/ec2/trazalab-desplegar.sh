#!/usr/bin/env bash
set -euo pipefail
umask 077
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

digest=${1:-}
version=${2:-}
huella_manifiestos=${3:-}
instancia=${4:-}
repositorio='970909363773.dkr.ecr.us-east-2.amazonaws.com/trazalab'
estado=/var/lib/trazalab-data/despliegues
respaldo=/usr/local/sbin/trazalab-respaldo-verificado
huella_aprobada=/etc/trazalab/manifiestos.sha256
coordinador=/usr/local/lib/trazalab/coordinador.sh

[[ "$digest" =~ ^sha256:[a-f0-9]{64}$ ]]
[[ "$version" =~ ^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$ ]]
[[ "$huella_manifiestos" =~ ^[a-f0-9]{64}$ ]]
[[ "$instancia" =~ ^i-[a-f0-9]{8,17}$ ]]
test "$(id -u)" -eq 0

if [[ ! -f "$coordinador" || -L "$coordinador" || "$(stat -c '%u:%a' "$coordinador")" != 0:644 ]]; then
  echo 'Falta la biblioteca protegida del coordinador externo.' >&2
  exit 1
fi
source "$coordinador"
if ! trazalab_coord_verificar_operacion despliegue "$instancia"; then
  echo 'El flujo SSM no posee el permiso externo de despliegue.' >&2
  exit 1
fi
export TRAZALAB_INSTANCIA_ID="$instancia"

install -d -m 0700 -o root -g root /run/trazalab
exec 9>/run/trazalab/operacion.lock
if ! flock -n 9; then
  echo 'Otra operación de despliegue, respaldo o parada mantiene el bloqueo.' >&2
  exit 1
fi

/usr/local/sbin/trazalab-verificar-datos
if [[ ! -x /usr/local/sbin/trazalab-apagar-inactivo || ! -d /run/trazalab-control || -L /run/trazalab-control || "$(stat -c '%u:%a' /run/trazalab-control)" != 10001:700 ]]; then
  echo 'Falta la preparación protegida del canal de apagado seguro.' >&2
  exit 1
fi
if [[ ! -f "$huella_aprobada" || -L "$huella_aprobada" ]] || [[ "$(stat -c '%u:%a' "$huella_aprobada")" != 0:600 ]]; then
  echo 'Falta la huella de manifiestos aprobada por el administrador.' >&2
  exit 1
fi
if [[ "$(tr -d '\r\n' < "$huella_aprobada")" != "$huella_manifiestos" ]]; then
  echo 'Los manifiestos cambiaron: requieren revisión y aplicación antes del despliegue.' >&2
  exit 1
fi

install -d -m 0700 -o root -g root "$estado"
if [[ -e "$estado/pendiente" ]]; then
  echo 'Existe un despliegue interrumpido; requiere revisión y recuperación manual.' >&2
  exit 1
fi

imagen="${repositorio}@${digest}"
anterior=$(kubectl get deployment trazalab -o json | python3 -c '
import json
import sys

deployment = json.load(sys.stdin)
images = [container["image"] for container in deployment["spec"]["template"]["spec"]["containers"] if container["name"] == "trazalab"]
if len(images) != 1:
    raise SystemExit("No se encontró exactamente un contenedor trazalab.")
print(images[0])
')

if [[ -f "$estado/version-actual" ]]; then
  read -r version_anterior digest_anterior < "$estado/version-actual"
  python3 - "$version_anterior" "$version" "$digest_anterior" "$digest" <<'PY'
import re
import sys

anterior, nueva, digest_anterior, digest_nuevo = sys.argv[1:]
patron = r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"
versiones = [re.fullmatch(patron, value) for value in (anterior, nueva)]
if not all(versiones):
    raise SystemExit("El estado de versión existente no es válido.")
valores = [tuple(map(int, match.groups())) for match in versiones]
if valores[1] < valores[0]:
    raise SystemExit("Se rechaza el despliegue de una versión anterior.")
if valores[1] == valores[0] and digest_anterior != digest_nuevo:
    raise SystemExit("La versión ya desplegada corresponde a otro digest.")
PY
fi

if [[ ! -f "$respaldo" || -L "$respaldo" || ! -x "$respaldo" || "$(stat -c '%u' "$respaldo")" != 0 ]]; then
  echo 'Falta el procedimiento root de respaldo externo y restauración verificada.' >&2
  exit 1
fi
permisos=$(stat -c '%a' "$respaldo")
if [[ "$permisos" != 700 && "$permisos" != 750 && "$permisos" != 755 ]]; then
  echo 'Los permisos del procedimiento de respaldo no son seguros.' >&2
  exit 1
fi

echo 'Comprobando respaldo externo y restauración antes de actualizar.'
if [[ "$(kubectl get pvc trazalab-postgres -o jsonpath='{.status.phase}')" != Bound || "$(kubectl get pv trazalab-postgres -o jsonpath='{.spec.persistentVolumeReclaimPolicy}')" != Retain || "$(kubectl get pv trazalab-postgres -o jsonpath='{.spec.hostPath.path}')" != /var/lib/trazalab-data/postgres || "$(findmnt -n -o TARGET --target /var/lib/trazalab-data)" != /var/lib/trazalab-data ]]; then
  echo 'El volumen PostgreSQL no esta montado y retenido como se esperaba.' >&2
  exit 1
fi

replicas_postgres=$(kubectl get deployment/trazalab-postgres -o jsonpath='{.spec.replicas}')
replicas_aplicacion=$(kubectl get deployment/trazalab -o jsonpath='{.spec.replicas}')
if [[ ( "$replicas_postgres" != 0 && "$replicas_postgres" != 1 ) || ( "$replicas_aplicacion" != 0 && "$replicas_aplicacion" != 1 ) ]]; then
  echo 'Las replicas actuales no coinciden con los estados esperados de PostgreSQL y TrazaLab.' >&2
  exit 1
fi

if ! trazalab_coord_verificar_operacion despliegue "$instancia"; then
  echo 'El permiso de despliegue dejo de ser verificable antes de iniciar los servicios.' >&2
  exit 1
fi
if [[ "$replicas_postgres" == 0 ]]; then
  kubectl scale deployment/trazalab-postgres --replicas=1 >/dev/null
fi
kubectl rollout status deployment/trazalab-postgres --timeout=180s

if [[ "$replicas_aplicacion" == 0 ]]; then
  if ! trazalab_coord_verificar_operacion despliegue "$instancia"; then
    echo 'El permiso de despliegue dejo de ser verificable antes de iniciar TrazaLab.' >&2
    exit 1
  fi
  kubectl scale deployment/trazalab --replicas=1 >/dev/null
fi
kubectl rollout status deployment/trazalab --timeout=360s

"$respaldo"
systemctl start trazalab-renovar-ecr-pull.service
kubectl get secret trazalab-ecr-pull -o name >/dev/null
printf '%s %s\n' "$version" "$digest" > "$estado/pendiente"
printf '%s\n' "$anterior" > "$estado/imagen-anterior"

echo "Desplegando $version por digest."
kubectl set image deployment/trazalab "trazalab=$imagen"
kubectl rollout status deployment/trazalab --timeout=360s
actual=$(kubectl get deployment trazalab -o json | python3 -c '
import json
import sys

deployment = json.load(sys.stdin)
images = [container["image"] for container in deployment["spec"]["template"]["spec"]["containers"] if container["name"] == "trazalab"]
if len(images) != 1:
    raise SystemExit("No se encontró exactamente un contenedor trazalab.")
print(images[0])
')
test "$actual" = "$imagen"

temporal=$(mktemp -d /run/trazalab-salud.XXXXXXXX)
puerto=18081
kubectl port-forward service/trazalab "$puerto:8080" --address 127.0.0.1 > "$temporal/port-forward.log" 2>&1 &
proceso=$!
trap 'kill "$proceso" 2>/dev/null || true; rm -f "$temporal/port-forward.log"; rmdir "$temporal"' EXIT
listo=false
for intento in {1..30}; do
  if ! kill -0 "$proceso" 2>/dev/null; then
    echo 'El port-forward terminó antes de iniciar.' >&2
    exit 1
  fi
  if grep -q "Forwarding from 127.0.0.1:${puerto} ->" "$temporal/port-forward.log"; then
    listo=true
    break
  fi
  sleep 1
done
if [[ "$listo" != true ]]; then
  echo 'No se pudo abrir el port-forward para verificar la aplicación.' >&2
  exit 1
fi
for ruta in /health /db-health /api/health; do
  curl --fail --silent --show-error --max-time 10 "http://127.0.0.1:${puerto}${ruta}" >/dev/null
done

printf '%s %s\n' "$version" "$digest" > "$estado/version-actual.nueva"
mv -f "$estado/version-actual.nueva" "$estado/version-actual"
rm -f "$estado/pendiente"
echo "Despliegue de $version verificado con los tres endpoints."
