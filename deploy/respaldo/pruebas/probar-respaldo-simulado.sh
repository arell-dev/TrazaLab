#!/usr/bin/env bash
set -euo pipefail

install -d -m 0700 /etc/trazalab /run/trazalab /usr/local/lib/trazalab /usr/local/sbin /usr/local/bin /tmp/respaldo-prueba
tr -d '\r' < /prueba/respaldo/ec2/trazalab-respaldo-verificado.sh > /tmp/respaldo-prueba/trazalab-respaldo-verificado.sh
tr -d '\r' < /prueba/apagado/ec2/coordinador.sh > /usr/local/lib/trazalab/coordinador.sh
chmod 0644 /usr/local/lib/trazalab/coordinador.sh
chmod 0755 /tmp/respaldo-prueba/trazalab-respaldo-verificado.sh
cat > /etc/trazalab/respaldo.conf <<'CONF'
AWS_REGION=us-east-2
AWS_ACCOUNT_ID=123456789012
S3_BUCKET=trazalab-prueba
S3_PREFIX=respaldos/trazalab
CONF
chmod 0600 /etc/trazalab/respaldo.conf
printf '#!/bin/sh\nexit 0\n' > /usr/local/sbin/trazalab-verificar-datos
printf '#!/bin/sh\necho tmpfs\n' > /usr/local/bin/findmnt
cat > /usr/local/bin/python3 <<'MOCK'
#!/bin/sh
if [ "$1" = -c ]; then echo version-prueba; else cat >/dev/null; fi
MOCK
cat > /usr/local/bin/aws <<'MOCK'
#!/bin/bash
set -e
if [ "$1 $2" = 'dynamodb get-item' ]; then
  echo '{"Item":{"pk":{"S":"trazalab-operacion"},"operacion":{"S":"despliegue"},"propietario":{"S":"11111111-1111-4111-8111-111111111111"},"instancia":{"S":"i-0123456789abcdef0"},"sesion":{"S":"22222222-2222-4222-8222-222222222222"}}}'
  exit
fi
if [ "$1 $2" = 'sts get-caller-identity' ]; then echo 123456789012; exit; fi
if [ "$1 $2" = 's3api get-bucket-versioning' ]; then echo Enabled; exit; fi
if [ "$1 $2" = 's3api put-object' ]; then
  [ "${MOCK_FALLO:-}" != subida ] || exit 1
  while [ "$#" -gt 0 ]; do
    if [ "$1" = --body ]; then cp "$2" /tmp/respaldo-prueba/objeto; fi
    shift
  done
  echo '{"VersionId":"version-prueba"}'
  exit
fi
if [ "$1 $2" = 's3api get-object' ]; then
  for argumento in "$@"; do
    case "$argumento" in */descargado.dump) cp /tmp/respaldo-prueba/objeto "$argumento"; destino="$argumento" ;; esac
  done
  if [ "${MOCK_FALLO:-}" = integridad ]; then printf x >> "$destino"; fi
  echo '{}'
  exit
fi
exit 1
MOCK
cat > /usr/local/bin/kubectl <<'MOCK'
#!/bin/bash
set -e
echo "$1" >> /tmp/respaldo-prueba/acciones
case "$1" in
  create|wait|delete) exit 0 ;;
  exec)
    case " $* " in
      *postmaster.pid*) echo 1 ;;
      *' pg_dump '*) printf 'PGDMP-contenido-simulado' ;;
      *' pg_restore '*) [ "${MOCK_FALLO:-}" != restauracion ]; cat >/dev/null ;;
      *' psql '*) echo t ;;
      *) exit 1 ;;
    esac
    ;;
  *) exit 1 ;;
esac
MOCK
chmod 0755 /usr/local/sbin/trazalab-verificar-datos /usr/local/bin/findmnt /usr/local/bin/python3 /usr/local/bin/aws /usr/local/bin/kubectl
export TRAZALAB_INSTANCIA_ID=i-0123456789abcdef0
if bash /tmp/respaldo-prueba/trazalab-respaldo-verificado.sh > /tmp/respaldo-prueba/salida 2>&1; then
  echo 'ERROR: se permitió ejecutar el respaldo sin el bloqueo del llamador.' >&2
  exit 1
fi
test ! -e /tmp/respaldo-prueba/acciones
echo 'OK: sin bloqueo'
exec 9>/run/trazalab/operacion.lock
flock -n 9

for caso in subida integridad restauracion exito; do
  : > /tmp/respaldo-prueba/acciones
  if MOCK_FALLO="$caso" bash /tmp/respaldo-prueba/trazalab-respaldo-verificado.sh > /tmp/respaldo-prueba/salida 2>&1; then
    test "$caso" = exito
  else
    test "$caso" != exito
  fi
  case "$caso" in
    subida|integridad) ! grep -qx create /tmp/respaldo-prueba/acciones ;;
    restauracion|exito) grep -qx delete /tmp/respaldo-prueba/acciones ;;
  esac
  echo "OK: $caso"
done
