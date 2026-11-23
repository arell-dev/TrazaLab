#!/usr/bin/env bash
set -euo pipefail
umask 077
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

readonly plazo_nanos=1800000000000
readonly aplicacion=deployment/trazalab
readonly postgres=deployment/trazalab-postgres
readonly reclamo=trazalab-postgres
readonly etiqueta_aplicacion='app.kubernetes.io/name=trazalab'
readonly etiqueta_postgres='app.kubernetes.io/name=trazalab-postgres'
readonly montaje_datos=/var/lib/trazalab-data
readonly ruta_bloqueo=/run/trazalab/operacion.lock
readonly libreria=/usr/local/lib/trazalab/coordinador.sh
readonly cliente_socket=/usr/local/lib/trazalab/actividad_socket.py

if [[ "$(id -u)" != 0 || ! -r /proc/sys/kernel/random/boot_id ]]; then
  echo 'El control requiere root y un identificador de arranque legible.' >&2
  exit 1
fi

if [[ ! -f "$libreria" || -L "$libreria" || "$(stat -c '%u:%a' "$libreria")" != 0:644 || ! -f "$cliente_socket" || -L "$cliente_socket" || "$(stat -c '%u:%a' "$cliente_socket")" != 0:755 ]]; then
  echo 'Faltan los componentes locales protegidos del apagado.' >&2
  exit 1
fi

source "$libreria"
arranque=$(cat /proc/sys/kernel/random/boot_id)
propietario=$(cat /proc/sys/kernel/random/uuid)
instancia=${TRAZALAB_INSTANCIA_ID:-}
if [[ ! "$instancia" =~ ^i-[a-f0-9]{8,17}$ || ! "$arranque" =~ ^[a-f0-9-]{36}$ ]]; then
  echo 'Falta la instancia o el identificador de arranque esperado.' >&2
  exit 1
fi

observar_actividad() {
  local respuesta
  respuesta=$(python3 "$cliente_socket" OBSERVAR) || return 1
  IFS='|' read -r tipo reloj_valido inactividad solicitudes drenando <<< "$respuesta"
  [[ "$tipo" == ESTADO && "$reloj_valido" == true && "$inactividad" =~ ^[0-9]+$ && "$solicitudes" =~ ^[0-9]+$ && ( "$drenando" == true || "$drenando" == false ) ]]
}

actividad_disponible() {
  observar_actividad && (( inactividad >= plazo_nanos )) && (( solicitudes == 0 )) && [[ "$drenando" == false ]]
}

if ! observar_actividad; then
  echo 'No se pudo obtener una observación válida de actividad; no se apagará.' >&2
  exit 1
fi

if (( inactividad < plazo_nanos || solicitudes != 0 )) || [[ "$drenando" == true ]]; then
  exit 0
fi

if [[ ! -d /run/trazalab || -L /run/trazalab || "$(stat -c '%u:%a' /run/trazalab)" != 0:700 ]]; then
  echo 'El directorio del bloqueo local no tiene permisos seguros.' >&2
  exit 1
fi

if [[ ! -f "$ruta_bloqueo" || -L "$ruta_bloqueo" || "$(stat -c '%u' "$ruta_bloqueo")" != 0 ]]; then
  echo 'El bloqueo local de operación no existe o no pertenece a root.' >&2
  exit 1
fi

if trazalab_coord_adquirir apagado "$propietario" "$instancia" "$arranque"; then
  permiso_adquirido=true
else
  estado=$?
  if [[ "$estado" == 2 ]]; then
    exit 0
  fi
  exit 1
fi

drenaje_iniciado=false
apagar_intentado=false
base_retorno_segura=true

restaurar_servicios() {
  local replicas

  replicas=$(kubectl get "$postgres" -o jsonpath='{.spec.replicas}') || return 1
  if [[ "$replicas" == 0 ]]; then
    kubectl scale "$postgres" --replicas=1 >/dev/null || return 1
    kubectl rollout status "$postgres" --timeout=180s >/dev/null || return 1
  elif [[ "$replicas" != 1 ]]; then
    return 1
  fi

  replicas=$(kubectl get "$aplicacion" -o jsonpath='{.spec.replicas}') || return 1
  if [[ "$replicas" == 0 ]]; then
    kubectl scale "$aplicacion" --replicas=1 >/dev/null || return 1
    kubectl rollout status "$aplicacion" --timeout=360s >/dev/null || return 1
  elif [[ "$replicas" != 1 ]]; then
    return 1
  fi
}

revertir_si_es_seguro() {
  local codigo=$?

  if [[ "$permiso_adquirido" != true || "$apagar_intentado" == true ]]; then
    return "$codigo"
  fi

  if [[ "$base_retorno_segura" == true ]] && ! restaurar_servicios; then
    base_retorno_segura=false
  fi

  if [[ "$base_retorno_segura" == true && "$drenaje_iniciado" == true ]]; then
    if [[ "$(python3 "$cliente_socket" CANCELAR 2>/dev/null || true)" == CANCELADO ]]; then
      drenaje_iniciado=false
    else
      base_retorno_segura=false
    fi
  fi

  if [[ "$base_retorno_segura" == true ]]; then
    if trazalab_coord_liberar apagado "$propietario" "$instancia" "$arranque"; then
      permiso_adquirido=false
    else
      base_retorno_segura=false
    fi
  fi

  if [[ "$base_retorno_segura" != true ]]; then
    echo 'Estado de recuperación incierto; se conserva el bloqueo externo para conciliación.' >&2
    return 1
  fi

  return "$codigo"
}
trap revertir_si_es_seguro EXIT

exec 9>"$ruta_bloqueo"
if ! flock -n 9; then
  if trazalab_coord_liberar apagado "$propietario" "$instancia" "$arranque"; then
    permiso_adquirido=false
  else
    echo 'No se pudo liberar el coordinador tras encontrar el bloqueo local ocupado.' >&2
    exit 1
  fi
  exit 0
fi

if ! trazalab_coord_verificar apagado "$propietario" "$instancia" "$arranque"; then
  echo 'El permiso externo no coincide; no se iniciará el drenaje.' >&2
  exit 1
fi

if ! actividad_disponible; then
  exit 0
fi

if [[ "$(python3 "$cliente_socket" INICIAR)" != DRENANDO ]]; then
  exit 0
fi
drenaje_iniciado=true

if ! observar_actividad || [[ "$drenando" != true ]] || (( inactividad < plazo_nanos || solicitudes != 0 )); then
  echo 'La aplicación no confirmó el drenaje y la inactividad completa.' >&2
  exit 1
fi

if [[ "$(kubectl get "$aplicacion" -o jsonpath='{.spec.replicas}')" != 1 || "$(kubectl get "$postgres" -o jsonpath='{.spec.replicas}')" != 1 ]]; then
  echo 'Las réplicas actuales no coinciden con el estado operativo esperado.' >&2
  exit 1
fi

if [[ "$(kubectl get pvc "$reclamo" -o jsonpath='{.status.phase}')" != Bound || "$(kubectl get pv "$reclamo" -o jsonpath='{.spec.persistentVolumeReclaimPolicy}')" != Retain || "$(kubectl get pv "$reclamo" -o jsonpath='{.spec.hostPath.path}')" != /var/lib/trazalab-data/postgres || "$(findmnt -n -o TARGET --target "$montaje_datos")" != "$montaje_datos" ]]; then
  echo 'El volumen persistente no está montado y retenido como se esperaba.' >&2
  exit 1
fi

if ! trazalab_coord_verificar apagado "$propietario" "$instancia" "$arranque"; then
  echo 'El permiso externo cambió antes del cierre; se cancelará la parada.' >&2
  exit 1
fi

kubectl scale "$aplicacion" --replicas=0 >/dev/null
for intento in {1..60}; do
  pods=$(kubectl get pods -l "$etiqueta_aplicacion" -o jsonpath='{.items[*].metadata.name}')
  if [[ -z "$pods" ]]; then
    break
  fi
  sleep 2
done
if [[ -n "${pods:-}" ]]; then
  echo 'La aplicación no terminó dentro del plazo de cierre.' >&2
  exit 1
fi

if ! trazalab_coord_verificar apagado "$propietario" "$instancia" "$arranque"; then
  echo 'El permiso externo dejó de ser verificable antes de detener PostgreSQL.' >&2
  exit 1
fi

kubectl scale "$postgres" --replicas=0 >/dev/null
for intento in {1..60}; do
  pods=$(kubectl get pods -l "$etiqueta_postgres" -o jsonpath='{.items[*].metadata.name}')
  if [[ -z "$pods" ]]; then
    break
  fi
  sleep 2
done
if [[ -n "$pods" ]]; then
  echo 'PostgreSQL no terminó dentro del plazo de cierre.' >&2
  exit 1
fi

if [[ "$(kubectl get pvc "$reclamo" -o jsonpath='{.status.phase}')" != Bound || "$(kubectl get pv "$reclamo" -o jsonpath='{.spec.persistentVolumeReclaimPolicy}')" != Retain || "$(findmnt -n -o TARGET --target "$montaje_datos")" != "$montaje_datos" ]] || ! trazalab_coord_verificar apagado "$propietario" "$instancia" "$arranque"; then
  echo 'No se confirmó la persistencia de PostgreSQL y la propiedad del permiso antes del apagado.' >&2
  exit 1
fi

apagar_intentado=true
if ! systemd-run --unit=trazalab-apagado-programado --on-active=10s --collect /usr/bin/systemctl poweroff; then
  echo 'No se pudo confirmar la programación de la parada; el bloqueo se conserva.' >&2
  exit 1
fi

echo 'Parada ordenada programada; el permiso se conserva hasta confirmar EC2 en stopped.'
