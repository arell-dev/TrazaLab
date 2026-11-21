#!/usr/bin/env bash
set -euo pipefail

# Ejecutar solamente en un contenedor aislado: modifica /run/trazalab del contenedor.
test "$(id -u)" = 0
test -f /.dockerenv
script=/pruebas/trazalab-preparar-apagado.sh
mkdir -m 700 /run/trazalab
touch /run/trazalab/operacion.lock
boot=$(cat /proc/sys/kernel/random/boot_id)

comprobar_fallo() {
  local esperado=$1
  shift
  local salida
  if salida=$(bash "$script" "$@" 2>&1); then
    echo 'El control no debe permitir el apagado.' >&2
    exit 1
  fi
  [[ "$salida" == *"$esperado"* ]]
}

comprobar_fallo 'Identificador de arranque inválido' invalido
comprobar_fallo 'otro arranque' 00000000-0000-0000-0000-000000000000
chmod 755 /run/trazalab
comprobar_fallo 'directorio de bloqueo' "$boot"
chmod 700 /run/trazalab
exec 8>/run/trazalab/operacion.lock
flock -n 8
comprobar_fallo 'Otra operación mantiene el bloqueo' "$boot"
flock -u 8
exec 8>&-
comprobar_fallo 'Preparación incompleta' "$boot"
echo 'Controles de arranque y bloqueo: cinco casos correctos; no se invocó el apagado.'
