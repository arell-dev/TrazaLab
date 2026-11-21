#!/usr/bin/env bash
set -euo pipefail
umask 077
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# Este primer control no tiene una ruta de apagado y siempre termina con error.
boot_esperado=${1:-}
if [[ ! "$boot_esperado" =~ ^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$ ]]; then
  echo 'Identificador de arranque inválido.' >&2
  exit 1
fi
if [[ "$(id -u)" != 0 ]]; then
  echo 'El control requiere root.' >&2
  exit 1
fi
if [[ "$(cat /proc/sys/kernel/random/boot_id)" != "$boot_esperado" ]]; then
  echo 'El comando corresponde a otro arranque; no se ejecutará.' >&2
  exit 1
fi

directorio=/run/trazalab
if [[ ! -d "$directorio" || -L "$directorio" || "$(stat -c '%u:%a' "$directorio")" != 0:700 ]]; then
  echo 'El directorio de bloqueo no existe o no está protegido.' >&2
  exit 1
fi
bloqueo="$directorio/operacion.lock"
if [[ ! -f "$bloqueo" || -L "$bloqueo" || "$(stat -c '%u' "$bloqueo")" != 0 ]]; then
  echo 'El bloqueo compartido no existe o no está protegido.' >&2
  exit 1
fi
exec 9>"$bloqueo"
if ! flock -n 9; then
  echo 'Otra operación mantiene el bloqueo local.' >&2
  exit 1
fi
if [[ "$(cat /proc/sys/kernel/random/boot_id)" != "$boot_esperado" ]]; then
  echo 'El identificador de arranque cambió.' >&2
  exit 1
fi

echo 'Preparación incompleta: no existe verificación externa ni canal de drenaje. No se iniciará el apagado.' >&2
exit 1
