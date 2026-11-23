#!/usr/bin/env bash
set -euo pipefail

script=/tmp/trazalab-desplegar.sh
directorio_estado=/var/lib/trazalab-data/despliegues
huella=$(printf 'a%.0s' {1..64})
digest=$(printf 'b%.0s' {1..64})
digest_anterior=$(printf 'c%.0s' {1..64})
imagen_anterior="970909363773.dkr.ecr.us-east-2.amazonaws.com/trazalab@sha256:${digest_anterior}"
imagen_nueva="970909363773.dkr.ecr.us-east-2.amazonaws.com/trazalab@sha256:${digest}"
instancia=i-0123456789abcdef0
propietario=11111111-1111-4111-8111-111111111111
sesion=22222222-2222-4222-8222-222222222222

mkdir -p /etc/trazalab /usr/local/lib/trazalab /usr/local/sbin /usr/local/bin /var/lib/trazalab-data /run/trazalab /run/trazalab-control
tr -d '\r' < /prueba/deploy/apagado/ec2/coordinador.sh > /usr/local/lib/trazalab/coordinador.sh
chmod 0644 /usr/local/lib/trazalab/coordinador.sh
chown 10001:10001 /run/trazalab-control
chmod 0700 /run/trazalab-control
printf '#!/bin/sh\nexit 0\n' > /usr/local/sbin/trazalab-apagar-inactivo
chmod 0755 /usr/local/sbin/trazalab-apagar-inactivo
cat > /usr/local/bin/aws <<'SCRIPT'
#!/bin/sh
if [ "$1 $2" = 'dynamodb get-item' ]; then
  printf '{"Item":{"pk":{"S":"trazalab-operacion"},"operacion":{"S":"despliegue"},"propietario":{"S":"11111111-1111-4111-8111-111111111111"},"instancia":{"S":"i-0123456789abcdef0"},"sesion":{"S":"22222222-2222-4222-8222-222222222222"}}}\n'
  exit 0
fi
echo "Comando AWS inesperado: $*" >&2
exit 2
SCRIPT
chmod 0755 /usr/local/bin/aws
tr -d '\r' < /prueba/deploy/publicacion/ec2/trazalab-desplegar.sh > "$script"
printf '#!/bin/sh\nexit 0\n' > /usr/local/sbin/trazalab-verificar-datos
chmod 0700 /usr/local/sbin/trazalab-verificar-datos

cat > /usr/local/bin/kubectl <<'SCRIPT'
#!/bin/sh
set -eu
printf '%s\n' "$*" >> /tmp/acciones-kubectl
case "$1 $2" in
  'get deployment/trazalab') cat /tmp/replicas-aplicacion ;;
  'get deployment/trazalab-postgres') cat /tmp/replicas-postgres ;;
  'get pvc') printf '%s\n' Bound ;;
  'get pv')
    case "$*" in
      *persistentVolumeReclaimPolicy*) printf '%s\n' Retain ;;
      *hostPath.path*) printf '%s\n' /var/lib/trazalab-data/postgres ;;
      *) exit 2 ;;
    esac
    ;;
  'get deployment')
    imagen=$(cat /tmp/imagen-actual)
    printf '{"spec":{"template":{"spec":{"containers":[{"name":"trazalab","image":"%s"}]}}}}\n' "$imagen"
    ;;
  'get secret')
    printf 'secret/trazalab-ecr-pull\n'
    ;;
  'set image')
    if [ "${FALLO_SET_IMAGE:-false}" = true ]; then
      exit 1
    fi
    printf '%s\n' "${4#trazalab=}" > /tmp/imagen-actual
    touch /tmp/imagen-cambiada
    ;;
  'scale deployment/trazalab') printf '%s' 1 > /tmp/replicas-aplicacion ;;
  'scale deployment/trazalab-postgres') printf '%s' 1 > /tmp/replicas-postgres ;;
  'rollout status')
    if [ "${FALLO_ROLLOUT:-false}" = true ] && [ -f /tmp/imagen-cambiada ]; then
      echo 'rollout de prueba fallido' >&2
      exit 1
    fi
    ;;
  'port-forward service/trazalab')
    printf 'Forwarding from 127.0.0.1:18081 -> 8080\n'
    exec sleep 60
    ;;
  *)
    echo "Comando kubectl inesperado: $*" >&2
    exit 2
    ;;
esac
SCRIPT

cat > /usr/local/bin/python3 <<'SCRIPT'
#!/bin/sh
set -eu
cat > /tmp/python-entrada
if [ "${1:-}" = -c ]; then
  if printf '%s' "${2:-}" | grep -q 'sesion'; then
    if grep -Fq '"operacion":{"S":"despliegue"}' /tmp/python-entrada && grep -Fq '"instancia":{"S":"i-0123456789abcdef0"}' /tmp/python-entrada; then
      exit 0
    fi
    exit 1
  fi
  cat /tmp/imagen-actual
  exit 0
fi
if [ "${1:-}" = - ] && [ "$#" -eq 5 ]; then
  anterior=$2
  nueva=$3
  digest_anterior=$4
  digest_nuevo=$5
  if [ "$anterior" = v0.0.1 ] && [ "$nueva" = v0.0.2 ]; then
    exit 0
  fi
  if [ "$anterior" = "$nueva" ] && [ "$digest_anterior" = "$digest_nuevo" ]; then
    exit 0
  fi
  exit 1
fi
echo "Invocación python3 inesperada: $*" >&2
exit 2
SCRIPT

cat > /usr/local/bin/systemctl <<'SCRIPT'
#!/bin/sh
printf '%s\n' "$*" >> /tmp/acciones-systemctl
if [ "${FALLO_ECR:-false}" = true ]; then
  echo 'fallo de renovación ECR simulado' >&2
  exit 1
fi
SCRIPT

cat > /usr/local/bin/curl <<'SCRIPT'
#!/bin/sh
for argumento do
  url=$argumento
done
printf '%s\n' "$url" >> /tmp/acciones-curl
case "${FALLO_SALUD:-}" in
  all)
    echo 'fallo de salud simulado' >&2
    exit 22
    ;;
esac
if [ -n "${FALLO_SALUD:-}" ] && [ "${url##*18081}" = "$FALLO_SALUD" ]; then
  echo 'fallo de salud simulado' >&2
  exit 22
fi
exit 0
SCRIPT

chmod 0755 /usr/local/bin/kubectl /usr/local/bin/python3 /usr/local/bin/systemctl /usr/local/bin/curl
printf '#!/bin/sh\necho /var/lib/trazalab-data\n' > /usr/local/bin/findmnt
chmod 0755 /usr/local/bin/findmnt

preparar_caso() {
  rm -rf "$directorio_estado"
  mkdir -p "$directorio_estado"
  printf '%s\n' "$huella" > /etc/trazalab/manifiestos.sha256
  chmod 0600 /etc/trazalab/manifiestos.sha256
  printf '%s\n' "$imagen_anterior" > /tmp/imagen-actual
  printf '%s' 1 > /tmp/replicas-aplicacion
  printf '%s' 1 > /tmp/replicas-postgres
  : > /tmp/acciones-kubectl
  : > /tmp/acciones-systemctl
  : > /tmp/acciones-curl
  rm -f /tmp/imagen-cambiada
  cat > /usr/local/sbin/trazalab-respaldo-verificado <<'SCRIPT'
#!/bin/sh
printf 'respaldo\n' >> /tmp/acciones-respaldo
if [ "$(cat /tmp/replicas-aplicacion)" != 1 ] || [ "$(cat /tmp/replicas-postgres)" != 1 ]; then
  echo 'Los servicios no estaban disponibles antes del respaldo.' >&2
  exit 1
fi
if [ "${FALLO_RESPALDO:-false}" = true ]; then
  echo 'fallo de respaldo simulado' >&2
  exit 1
fi
SCRIPT
  chmod 0700 /usr/local/sbin/trazalab-respaldo-verificado
  : > /tmp/acciones-respaldo
}

ejecutar_fallo() {
  local texto_esperado=$1
  local version_caso=${VERSION_CASO:-v0.0.1}
  local huella_caso=${HUELLA_CASO:-$huella}
  shift
  if env "TRAZALAB_INSTANCIA_ID=$instancia" "$@" bash "$script" "sha256:$digest" "$version_caso" "$huella_caso" "$instancia" > /tmp/resultado 2>&1; then
    echo "ERROR: se esperaba el fallo: $texto_esperado" >&2
    exit 1
  fi
  if ! grep -Fq "$texto_esperado" /tmp/resultado; then
    cat /tmp/resultado >&2
    echo "ERROR: no se encontró el motivo esperado: $texto_esperado" >&2
    exit 1
  fi
}

preparar_caso
rm -f /usr/local/sbin/trazalab-respaldo-verificado
ejecutar_fallo 'Falta el procedimiento root de respaldo externo' \
  "PATH=$PATH"
! grep -Fq 'set image' /tmp/acciones-kubectl
test ! -e "$directorio_estado/pendiente"
echo 'OK: sin helper de respaldo no se modifica Kubernetes.'

preparar_caso
huella_incorrecta=$(printf 'd%.0s' {1..64})
HUELLA_CASO=$huella_incorrecta ejecutar_fallo 'Los manifiestos cambiaron' \
  "PATH=$PATH"
test ! -s /tmp/acciones-kubectl
test ! -s /tmp/acciones-respaldo
echo 'OK: una huella no aprobada se rechaza antes del respaldo y Kubernetes.'

preparar_caso
ejecutar_fallo 'fallo de respaldo simulado' \
  "PATH=$PATH" FALLO_RESPALDO=true
test ! -s /tmp/acciones-systemctl
! grep -Fq 'set image' /tmp/acciones-kubectl
test ! -e "$directorio_estado/pendiente"
echo 'OK: si falla respaldo/restauración, ECR y Kubernetes no se tocan.'

preparar_caso
ejecutar_fallo 'fallo de renovación ECR simulado' \
  "PATH=$PATH" FALLO_ECR=true
test -s /tmp/acciones-respaldo
test -s /tmp/acciones-systemctl
! grep -Fq 'set image' /tmp/acciones-kubectl
test ! -e "$directorio_estado/pendiente"
echo 'OK: un fallo al renovar ECR ocurre antes de aplicar Kubernetes.'

preparar_caso
exec 8>/run/trazalab/operacion.lock
flock -n 8
ejecutar_fallo 'Otra operación de despliegue, respaldo o parada mantiene el bloqueo' \
  "PATH=$PATH"
exec 8>&-
test ! -s /tmp/acciones-respaldo
test ! -s /tmp/acciones-kubectl
echo 'OK: el bloqueo exclusivo impide operaciones concurrentes.'

preparar_caso
printf 'v0.0.1 sha256:%s\n' "$digest" > "$directorio_estado/pendiente"
ejecutar_fallo 'Existe un despliegue interrumpido' \
  "PATH=$PATH"
test -f "$directorio_estado/pendiente"
test ! -s /tmp/acciones-respaldo
test ! -s /tmp/acciones-kubectl
echo 'OK: una interrupción pendiente bloquea el reintento automático.'

preparar_caso
ejecutar_fallo 'rollout de prueba fallido' \
  "PATH=$PATH" FALLO_ROLLOUT=true
test -f "$directorio_estado/pendiente"
test ! -f "$directorio_estado/version-actual"
grep -Fq 'set image deployment/trazalab' /tmp/acciones-kubectl
! grep -Fq 'port-forward' /tmp/acciones-kubectl
echo 'OK: un rollout fallido deja evidencia pendiente y no declara versión activa.'

preparar_caso
printf 'v0.0.1 sha256:%s\n' "$digest_anterior" > "$directorio_estado/version-actual"
VERSION_CASO=v0.0.2 ejecutar_fallo 'fallo de salud simulado' \
  "PATH=$PATH" FALLO_SALUD=/db-health
test -f "$directorio_estado/pendiente"
grep -Fqx "v0.0.1 sha256:$digest_anterior" "$directorio_estado/version-actual"
test "$(wc -l < /tmp/acciones-curl | tr -d ' ')" -eq 2
echo 'OK: una comprobación de salud fallida conserva la versión previa y marca pendiente.'

preparar_caso
TRAZALAB_INSTANCIA_ID=$instancia bash "$script" "sha256:$digest" v0.0.1 "$huella" "$instancia" > /tmp/resultado 2>&1
test ! -e "$directorio_estado/pendiente"
grep -Fqx "v0.0.1 sha256:$digest" "$directorio_estado/version-actual"
test "$(wc -l < /tmp/acciones-curl | tr -d ' ')" -eq 3
echo 'OK: el despliegue saludable registra digest y versión y retira la marca pendiente.'

bash "$script" "sha256:$digest" v0.0.1 "$huella" "$instancia" > /tmp/resultado 2>&1
test ! -e "$directorio_estado/pendiente"
grep -Fqx "v0.0.1 sha256:$digest" "$directorio_estado/version-actual"
echo 'OK: el reintento de la misma etiqueta y digest es idempotente.'

preparar_caso
printf '%s' 0 > /tmp/replicas-aplicacion
printf '%s' 0 > /tmp/replicas-postgres
TRAZALAB_INSTANCIA_ID=$instancia bash "$script" "sha256:$digest" v0.0.1 "$huella" "$instancia" > /tmp/resultado 2>&1
test "$(cat /tmp/replicas-aplicacion)" = 1
test "$(cat /tmp/replicas-postgres)" = 1
test -s /tmp/acciones-respaldo
echo 'OK: SSM reactiva PostgreSQL y TrazaLab antes del respaldo y el despliegue.'
