#!/usr/bin/env bash
set -euo pipefail
umask 077
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

readonly aplicacion=deployment/trazalab
readonly postgres=deployment/trazalab-postgres
readonly reclamo=trazalab-postgres
readonly montaje_datos=/var/lib/trazalab-data
readonly ruta_bloqueo=/run/trazalab/operacion.lock
readonly ruta_intentos=/run/trazalab/recuperacion-arranque.intentos
readonly intentos_maximos=30
readonly libreria=/usr/local/lib/trazalab/coordinador.sh

if [[ "$(id -u)" != 0 || ! -r /proc/sys/kernel/random/boot_id ]]; then
  echo 'La recuperacion requiere root y un identificador de arranque legible.' >&2
  exit 1
fi

if [[ ! -d /run/trazalab || -L /run/trazalab || "$(stat -c '%u:%a' /run/trazalab)" != 0:700 ]]; then
  echo 'El directorio local de recuperación no tiene permisos seguros.' >&2
  systemctl stop --no-block trazalab-recuperar-arranque.timer >/dev/null 2>&1 || true
  exit 1
fi

if [[ -e "$ruta_intentos" || -L "$ruta_intentos" ]]; then
  if [[ ! -f "$ruta_intentos" || -L "$ruta_intentos" || "$(stat -c '%u:%a' "$ruta_intentos")" != 0:600 ]]; then
    echo 'El contador local de recuperación no tiene permisos seguros.' >&2
    systemctl stop --no-block trazalab-recuperar-arranque.timer >/dev/null 2>&1 || true
    exit 1
  fi
else
  install -o root -g root -m 0600 /dev/null "$ruta_intentos"
  printf '0\n' > "$ruta_intentos"
fi

intentos=$(cat "$ruta_intentos")
if [[ ! "$intentos" =~ ^(0|[1-9][0-9]*)$ ]]; then
  echo 'El contador local de recuperación no es válido.' >&2
  systemctl stop --no-block trazalab-recuperar-arranque.timer >/dev/null 2>&1 || true
  exit 1
fi
if (( 10#$intentos >= intentos_maximos )); then
  echo 'Se agotaron los intentos de recuperación automática; requiere conciliación manual.' >&2
  systemctl stop --no-block trazalab-recuperar-arranque.timer >/dev/null 2>&1 || true
  exit 1
fi
printf '%s\n' "$((intentos + 1))" > "$ruta_intentos"

if [[ ! -f "$libreria" || -L "$libreria" || "$(stat -c '%u:%a' "$libreria")" != 0:644 ]]; then
  echo 'Falta la biblioteca local protegida del coordinador.' >&2
  exit 1
fi

source "$libreria"
instancia=${TRAZALAB_INSTANCIA_ID:-}
arranque=$(cat /proc/sys/kernel/random/boot_id)
propietario=$(cat /proc/sys/kernel/random/uuid)

if [[ ! "$instancia" =~ ^i-[a-f0-9]{8,17}$ || ! "$arranque" =~ ^[a-f0-9-]{36}$ || ! "$propietario" =~ ^[a-f0-9-]{36}$ ]]; then
  echo 'Falta la identidad esperada de EC2.' >&2
  exit 1
fi

leer_replicas() {
  local recurso=$1
  kubectl get "$recurso" -o jsonpath='{.spec.replicas}'
}

validar_volumen() {
  [[ "$(kubectl get pvc "$reclamo" -o jsonpath='{.status.phase}')" == Bound ]] &&
    [[ "$(kubectl get pv "$reclamo" -o jsonpath='{.spec.persistentVolumeReclaimPolicy}')" == Retain ]] &&
    [[ "$(kubectl get pv "$reclamo" -o jsonpath='{.spec.hostPath.path}')" == /var/lib/trazalab-data/postgres ]] &&
    [[ "$(findmnt -n -o TARGET --target "$montaje_datos")" == "$montaje_datos" ]]
}

replicas_postgres=$(leer_replicas "$postgres")
replicas_aplicacion=$(leer_replicas "$aplicacion")
if [[ "$replicas_postgres" == 1 && "$replicas_aplicacion" == 1 ]]; then
  systemctl stop --no-block trazalab-recuperar-arranque.timer >/dev/null 2>&1 || true
  exit 0
fi

if [[ ( "$replicas_postgres" != 0 && "$replicas_postgres" != 1 ) || ( "$replicas_aplicacion" != 0 && "$replicas_aplicacion" != 1 ) ]]; then
  echo 'Las replicas existentes no coinciden con los estados esperados.' >&2
  exit 1
fi

if [[ ! -f "$ruta_bloqueo" || -L "$ruta_bloqueo" || "$(stat -c '%u:%a' "$ruta_bloqueo")" != 0:600 ]]; then
  echo 'El bloqueo local no tiene los permisos seguros esperados.' >&2
  exit 1
fi

if trazalab_coord_adquirir arranque "$propietario" "$instancia" "$arranque"; then
  permiso_adquirido=true
else
  estado=$?
  if [[ "$estado" == 2 ]]; then
    exit 0
  fi
  exit 1
fi

liberar_si_no_hubo_cambio() {
  if [[ "$permiso_adquirido" == true ]]; then
    trazalab_coord_liberar arranque "$propietario" "$instancia" "$arranque" || {
      echo 'No se pudo liberar el permiso sin cambios de servicios.' >&2
      return 1
    }
    permiso_adquirido=false
  fi
}

detener_reintentos_si_hubo_cambio_incierto() {
  local codigo=$?
  if [[ "$codigo" != 0 && "$servicios_cambiados" == true ]]; then
    echo 'La recuperación cambió servicios y no terminó; se detiene el temporizador para conciliación manual.' >&2
    systemctl stop --no-block trazalab-recuperar-arranque.timer >/dev/null 2>&1 || true
  fi
  return "$codigo"
}

servicios_cambiados=false
trap detener_reintentos_si_hubo_cambio_incierto EXIT

exec 9>"$ruta_bloqueo"
if ! flock -n 9; then
  liberar_si_no_hubo_cambio
  exit 0
fi

if ! trazalab_coord_verificar arranque "$propietario" "$instancia" "$arranque"; then
  echo 'El permiso de arranque no coincide; no se modificaran las replicas.' >&2
  exit 1
fi

replicas_postgres=$(leer_replicas "$postgres") || exit 1
replicas_aplicacion=$(leer_replicas "$aplicacion") || exit 1
if [[ ( "$replicas_postgres" != 0 && "$replicas_postgres" != 1 ) || ( "$replicas_aplicacion" != 0 && "$replicas_aplicacion" != 1 ) ]]; then
  echo 'Las replicas cambiaron a un estado no previsto.' >&2
  liberar_si_no_hubo_cambio
  exit 1
fi

if ! validar_volumen; then
  echo 'El volumen PostgreSQL no esta montado y retenido como se esperaba.' >&2
  liberar_si_no_hubo_cambio
  exit 1
fi

if [[ "$replicas_postgres" == 0 ]]; then
  servicios_cambiados=true
  kubectl scale "$postgres" --replicas=1 >/dev/null
fi
kubectl rollout status "$postgres" --timeout=180s >/dev/null

if [[ "$replicas_aplicacion" == 0 ]]; then
  if ! trazalab_coord_verificar arranque "$propietario" "$instancia" "$arranque"; then
    echo 'El permiso de arranque dejo de ser verificable antes de activar TrazaLab.' >&2
    exit 1
  fi
  servicios_cambiados=true
  kubectl scale "$aplicacion" --replicas=1 >/dev/null
fi
kubectl rollout status "$aplicacion" --timeout=360s >/dev/null

if ! validar_volumen || ! trazalab_coord_verificar arranque "$propietario" "$instancia" "$arranque"; then
  echo 'No se pudo confirmar la recuperacion y el permiso de arranque.' >&2
  exit 1
fi

trazalab_coord_liberar arranque "$propietario" "$instancia" "$arranque"
permiso_adquirido=false
systemctl stop --no-block trazalab-recuperar-arranque.timer >/dev/null 2>&1 || true
echo 'TrazaLab y PostgreSQL estan listos despues del arranque de EC2.'
