#!/usr/bin/env bash
set -euo pipefail

test "$(id -u)" -eq 0
test -f /.dockerenv
install -d -m 0700 -o root -g root /run/trazalab
touch /run/trazalab/operacion.lock
chmod 0600 /run/trazalab/operacion.lock
install -d -m 0755 -o root -g root /usr/local/lib/trazalab /usr/local/bin /usr/local/sbin
tr -d '\r' < /prueba/deploy/apagado/ec2/coordinador.sh > /usr/local/lib/trazalab/coordinador.sh
tr -d '\r' < /prueba/deploy/apagado/ec2/actividad_socket.py > /usr/local/lib/trazalab/actividad_socket.py
tr -d '\r' < /prueba/deploy/apagado/ec2/trazalab-apagar-inactivo.sh > /usr/local/sbin/trazalab-apagar-inactivo
tr -d '\r' < /prueba/deploy/apagado/ec2/trazalab-recuperar-arranque.sh > /usr/local/sbin/trazalab-recuperar-arranque
chmod 0644 /usr/local/lib/trazalab/coordinador.sh
chmod 0755 /usr/local/lib/trazalab/actividad_socket.py /usr/local/sbin/trazalab-apagar-inactivo /usr/local/sbin/trazalab-recuperar-arranque

cat > /usr/local/bin/python3 <<'SCRIPT'
#!/bin/sh
set -eu
if [ "$1" = /usr/local/lib/trazalab/actividad_socket.py ]; then
  printf '%s\n' "$2" >> /tmp/actividad
  case "$2" in
    OBSERVAR)
      if [ -f /tmp/drenaje ]; then echo 'ESTADO|true|1800000000000|0|true'
      else echo "${MOCK_OBSERVACION:-ESTADO|true|1800000000000|0|false}"; fi
      ;;
    INICIAR) touch /tmp/drenaje; echo DRENANDO ;;
    CANCELAR) rm -f /tmp/drenaje; echo CANCELADO ;;
    *) exit 2 ;;
  esac
  exit 0
fi
if [ "$1" = -c ]; then
  cat > /tmp/item
  if printf '%s' "$2" | grep -q 're.fullmatch'; then
    grep -Fq '"operacion":{"S":"'"$3"'"}' /tmp/item &&
      grep -Fq '"instancia":{"S":"'"$4"'"}' /tmp/item &&
      grep -Eq '"propietario":\{"S":"[a-f0-9-]{36}"\}' /tmp/item &&
      grep -Eq '"sesion":\{"S":"[a-f0-9-]{36}"\}' /tmp/item
    exit
  fi
  grep -Fq '"operacion":{"S":"'"$3"'"}' /tmp/item &&
    grep -Fq '"propietario":{"S":"'"$4"'"}' /tmp/item &&
    grep -Fq '"instancia":{"S":"'"$5"'"}' /tmp/item &&
    grep -Fq '"sesion":{"S":"'"$6"'"}' /tmp/item
  exit
fi
exit 2
SCRIPT

cat > /usr/local/bin/aws <<'SCRIPT'
#!/bin/sh
set -eu
printf '%s\n' "$*" >> /tmp/aws
case "$1 $2" in
  'dynamodb put-item')
    if [ "${MOCK_AWS:-ok}" = busy ]; then echo ConditionalCheckFailedException >&2; exit 254; fi
    if [ "${MOCK_AWS:-ok}" = error ]; then echo network-failure >&2; exit 254; fi
    item=
    while [ "$#" -gt 0 ]; do
      if [ "$1" = --item ]; then item=$2; break; fi
      shift
    done
    printf '%s' "$item" | sed -n 's/.*"propietario":{"S":"\([^"]*\)"}.*/\1/p' > /tmp/propietario
    printf '%s' "$item" | sed -n 's/.*"sesion":{"S":"\([^"]*\)"}.*/\1/p' > /tmp/sesion
    printf '%s' "$item" | sed -n 's/.*"operacion":{"S":"\([^"]*\)"}.*/\1/p' > /tmp/operacion
    ;;
  'dynamodb get-item')
    printf '{"Item":{"pk":{"S":"trazalab-operacion"},"operacion":{"S":"%s"},"propietario":{"S":"%s"},"instancia":{"S":"i-0123456789abcdef0"},"sesion":{"S":"%s"}}}\n' "$(cat /tmp/operacion)" "$(cat /tmp/propietario)" "$(cat /tmp/sesion)"
    ;;
  'dynamodb delete-item') ;;
  *) exit 2 ;;
esac
SCRIPT

cat > /usr/local/bin/kubectl <<'SCRIPT'
#!/bin/sh
set -eu
printf '%s\n' "$*" >> /tmp/kubectl
case "$1 $2" in
  'get deployment/trazalab') cat /tmp/app ;;
  'get deployment/trazalab-postgres') cat /tmp/db ;;
  'get pvc') echo Bound ;;
  'get pv')
    case "$*" in *persistentVolumeReclaimPolicy*) echo Retain ;; *hostPath.path*) echo /var/lib/trazalab-data/postgres ;; *) exit 2 ;; esac
    ;;
  'get pods')
    case "$*" in
      *app.kubernetes.io/name=trazalab-postgres*)
        if [ "${MOCK_PODS_DB:-gone}" = hold ]; then echo db-0; fi
        ;;
      *)
        if [ "${MOCK_PODS_APP:-gone}" = hold ]; then echo app-0; fi
        ;;
    esac
    ;;
  'scale deployment/trazalab')
    case "$*" in *--replicas=0*) echo -n 0 > /tmp/app ;; *--replicas=1*) echo -n 1 > /tmp/app ;; *) exit 2 ;; esac
    ;;
  'scale deployment/trazalab-postgres')
    case "$*" in *--replicas=0*) echo -n 0 > /tmp/db ;; *--replicas=1*) echo -n 1 > /tmp/db ;; *) exit 2 ;; esac
    ;;
  'rollout status')
    if [ "${MOCK_ROLLOUT_FAILURE:-false}" = true ] && [ "$3" = deployment/trazalab-postgres ]; then
      exit 1
    fi
    ;;
  *) exit 2 ;;
esac
SCRIPT

cat > /usr/local/bin/findmnt <<'SCRIPT'
#!/bin/sh
echo "${MOCK_MOUNT:-/var/lib/trazalab-data}"
SCRIPT
cat > /usr/local/bin/sleep <<'SCRIPT'
#!/bin/sh
exit 0
SCRIPT
cat > /usr/local/bin/systemd-run <<'SCRIPT'
#!/bin/sh
printf '%s\n' "$*" >> /tmp/systemd
SCRIPT
cat > /usr/local/bin/systemctl <<'SCRIPT'
#!/bin/sh
printf '%s\n' "$*" >> /tmp/systemctl
SCRIPT
chmod 0755 /usr/local/bin/python3 /usr/local/bin/aws /usr/local/bin/kubectl /usr/local/bin/findmnt /usr/local/bin/sleep /usr/local/bin/systemd-run /usr/local/bin/systemctl
export PATH=/usr/local/bin:/usr/local/sbin:/usr/sbin:/usr/bin:/sbin:/bin
export TRAZALAB_INSTANCIA_ID=i-0123456789abcdef0

preparar() {
  rm -f /tmp/drenaje
  echo -n 1 > /tmp/app
  echo -n 1 > /tmp/db
  echo 11111111-1111-4111-8111-111111111111 > /tmp/propietario
  echo 22222222-2222-4222-8222-222222222222 > /tmp/sesion
  echo apagado > /tmp/operacion
  : > /tmp/aws
  : > /tmp/kubectl
  : > /tmp/systemd
  : > /tmp/systemctl
  : > /tmp/actividad
}

preparar
MOCK_OBSERVACION='ESTADO|true|1799000000000|0|false' /usr/local/sbin/trazalab-apagar-inactivo
test ! -s /tmp/aws && test ! -s /tmp/kubectl && test ! -s /tmp/systemd
echo 'OK: 29:59 no adquiere permisos ni apaga.'

preparar
MOCK_OBSERVACION='ESTADO|true|1800000000000|0|false' MOCK_AWS=busy /usr/local/sbin/trazalab-apagar-inactivo
test ! -s /tmp/kubectl && test ! -s /tmp/systemd
! grep -Fxq INICIAR /tmp/actividad
echo 'OK: coordinador ocupado impide drenaje y apagado.'

preparar
if MOCK_OBSERVACION='ESTADO|true|1800000000000|0|false' MOCK_AWS=error /usr/local/sbin/trazalab-apagar-inactivo; then exit 1; fi
test ! -s /tmp/kubectl && test ! -s /tmp/systemd
! grep -Fxq INICIAR /tmp/actividad
echo 'OK: error de red impide drenaje y apagado.'

preparar
MOCK_OBSERVACION='ESTADO|true|1800000000000|0|false' /usr/local/sbin/trazalab-apagar-inactivo
test "$(cat /tmp/app)" = 0 && test "$(cat /tmp/db)" = 0
grep -Fq 'scale deployment/trazalab --replicas=0' /tmp/kubectl
grep -Fq 'scale deployment/trazalab-postgres --replicas=0' /tmp/kubectl
grep -Fq -- '--on-active=10s --collect /usr/bin/systemctl poweroff' /tmp/systemd
! grep -Fq 'dynamodb delete-item' /tmp/aws
echo 'OK: 30:00 apaga en orden y conserva el permiso hasta la confirmacion externa.'

preparar
if MOCK_OBSERVACION='ESTADO|true|1800000000000|0|false' MOCK_MOUNT=/mnt /usr/local/sbin/trazalab-apagar-inactivo; then exit 1; fi
test "$(cat /tmp/app)" = 1 && test "$(cat /tmp/db)" = 1
test ! -s /tmp/systemd
grep -Fxq CANCELAR /tmp/actividad
grep -Fq 'dynamodb delete-item' /tmp/aws
echo 'OK: montaje inesperado cancela el drenaje.'

preparar
if MOCK_OBSERVACION='ESTADO|true|1800000000000|0|false' MOCK_PODS_APP=hold /usr/local/sbin/trazalab-apagar-inactivo; then exit 1; fi
test "$(cat /tmp/app)" = 1 && test "$(cat /tmp/db)" = 1
test ! -s /tmp/systemd
grep -Fxq CANCELAR /tmp/actividad
grep -Fq 'dynamodb delete-item' /tmp/aws
echo 'OK: aplicacion que no termina se recupera y no apaga EC2.'

preparar
if MOCK_OBSERVACION='ESTADO|true|1800000000000|0|false' MOCK_PODS_DB=hold /usr/local/sbin/trazalab-apagar-inactivo; then exit 1; fi
test "$(cat /tmp/app)" = 1 && test "$(cat /tmp/db)" = 1
test ! -s /tmp/systemd
grep -Fxq CANCELAR /tmp/actividad
grep -Fq 'dynamodb delete-item' /tmp/aws
echo 'OK: PostgreSQL que no termina se recupera antes de la aplicacion.'

preparar
printf '%s' 0 > /tmp/app
printf '%s' 0 > /tmp/db
/usr/local/sbin/trazalab-recuperar-arranque
test "$(cat /tmp/app)" = 1 && test "$(cat /tmp/db)" = 1
postgres_linea=$(grep -n 'scale deployment/trazalab-postgres --replicas=1' /tmp/kubectl | cut -d : -f 1)
aplicacion_linea=$(grep -n 'scale deployment/trazalab --replicas=1' /tmp/kubectl | cut -d : -f 1)
test "$postgres_linea" -lt "$aplicacion_linea"
grep -Fq 'dynamodb delete-item' /tmp/aws
echo 'OK: la recuperacion de arranque inicia PostgreSQL antes de TrazaLab y libera el permiso.'

preparar
/usr/local/sbin/trazalab-recuperar-arranque
grep -Fq 'stop --no-block trazalab-recuperar-arranque.timer' /tmp/systemctl
! grep -Fq 'scale deployment/' /tmp/kubectl
echo 'OK: servicios ya activos detienen el temporizador y evitan reintentos innecesarios.'

preparar
printf '%s' 0 > /tmp/app
printf '%s' 0 > /tmp/db
MOCK_AWS=busy /usr/local/sbin/trazalab-recuperar-arranque
! grep -Fq 'scale deployment/' /tmp/kubectl
test ! -s /tmp/systemctl
echo 'OK: la recuperacion no modifica servicios si otro actor posee el coordinador.'

preparar
printf '%s' 0 > /tmp/app
printf '%s' 0 > /tmp/db
if MOCK_MOUNT=/mnt /usr/local/sbin/trazalab-recuperar-arranque; then exit 1; fi
! grep -Fq 'scale deployment/' /tmp/kubectl
grep -Fq 'dynamodb delete-item' /tmp/aws
echo 'OK: un volumen no verificado impide recuperar los servicios.'

preparar
printf '%s' 0 > /tmp/app
printf '%s' 0 > /tmp/db
if MOCK_ROLLOUT_FAILURE=true /usr/local/sbin/trazalab-recuperar-arranque; then exit 1; fi
test "$(cat /tmp/db)" = 1 && test "$(cat /tmp/app)" = 0
! grep -Fq 'dynamodb delete-item' /tmp/aws
grep -Fq 'stop --no-block trazalab-recuperar-arranque.timer' /tmp/systemctl
echo 'OK: una recuperación parcial incierta conserva el permiso y detiene el temporizador.'

preparar
printf '%s' 0 > /tmp/app
printf '%s' 0 > /tmp/db
printf '30\n' > /run/trazalab/recuperacion-arranque.intentos
chmod 0600 /run/trazalab/recuperacion-arranque.intentos
if MOCK_AWS=busy /usr/local/sbin/trazalab-recuperar-arranque; then exit 1; fi
grep -Fq 'stop --no-block trazalab-recuperar-arranque.timer' /tmp/systemctl
! grep -Fq 'scale deployment/' /tmp/kubectl
echo 'OK: se detienen los reintentos automáticos al agotar 30 intentos.'
