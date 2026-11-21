#!/usr/bin/env bash
set -euo pipefail

mkdir -p /etc/trazalab /usr/local/sbin /usr/local/bin /var/lib/trazalab-data
printf '#!/bin/sh\nexit 0\n' > /usr/local/sbin/trazalab-verificar-datos
chmod 0700 /usr/local/sbin/trazalab-verificar-datos
cat > /usr/local/bin/kubectl <<'SCRIPT'
#!/bin/sh
if [ "$1" = get ] && [ "$2" = deployment ]; then
  printf '{}\n'
  exit 0
fi
printf '%s\n' "$*" >> /tmp/acciones-kubectl
exit 1
SCRIPT
cat > /usr/local/bin/python3 <<'SCRIPT'
#!/bin/sh
cat >/dev/null
printf '%s\n' '970909363773.dkr.ecr.us-east-2.amazonaws.com/trazalab@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
SCRIPT
chmod 0755 /usr/local/bin/kubectl /usr/local/bin/python3
huella=$(printf 'a%.0s' {1..64})
digest="sha256:$(printf 'b%.0s' {1..64})"
printf '%s\n' "$huella" > /etc/trazalab/manifiestos.sha256
chmod 0600 /etc/trazalab/manifiestos.sha256

if bash /prueba/trazalab-desplegar.sh "$digest" v0.0.1 "$huella" > /tmp/resultado 2>&1; then
  echo 'ERROR: se permitió el despliegue sin el respaldo verificado.' >&2
  exit 1
fi
grep -q 'Falta el procedimiento root de respaldo externo' /tmp/resultado
test ! -e /tmp/acciones-kubectl
test ! -e /var/lib/trazalab-data/despliegues/pendiente
echo 'OK: sin respaldo, el despliegue se detuvo antes de cambiar Kubernetes.'

huella_incorrecta=$(printf 'c%.0s' {1..64})
if bash /prueba/trazalab-desplegar.sh "$digest" v0.0.1 "$huella_incorrecta" > /tmp/resultado 2>&1; then
  echo 'ERROR: se aceptó una huella de manifiestos no aprobada.' >&2
  exit 1
fi
grep -q 'Los manifiestos cambiaron' /tmp/resultado
test ! -e /tmp/acciones-kubectl
echo 'OK: se rechazaron manifiestos sin aprobar.'
