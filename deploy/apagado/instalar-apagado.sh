#!/usr/bin/env bash
set -euo pipefail

if [[ "$(id -u)" != 0 ]]; then
  echo 'La instalación requiere root.' >&2
  exit 1
fi

if [[ ! -f /etc/trazalab/apagado.conf || -L /etc/trazalab/apagado.conf || "$(stat -c '%u:%a' /etc/trazalab/apagado.conf)" != 0:600 ]]; then
  echo 'Falta la configuración root 0600 de la instancia y el coordinador.' >&2
  exit 1
fi

raiz=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
install -d -o root -g root -m 0755 /usr/local/lib/trazalab
install -o root -g root -m 0644 "$raiz/ec2/coordinador.sh" /usr/local/lib/trazalab/coordinador.sh
install -o root -g root -m 0755 "$raiz/ec2/actividad_socket.py" /usr/local/lib/trazalab/actividad_socket.py
install -o root -g root -m 0755 "$raiz/ec2/trazalab-apagar-inactivo.sh" /usr/local/sbin/trazalab-apagar-inactivo
install -o root -g root -m 0755 "$raiz/ec2/trazalab-recuperar-arranque.sh" /usr/local/sbin/trazalab-recuperar-arranque
install -o root -g root -m 0644 "$raiz/systemd/trazalab-control.tmpfiles" /etc/tmpfiles.d/trazalab-control.conf
install -o root -g root -m 0644 "$raiz/systemd/trazalab-apagado.service" /etc/systemd/system/trazalab-apagado.service
install -o root -g root -m 0644 "$raiz/systemd/trazalab-apagado.timer" /etc/systemd/system/trazalab-apagado.timer
install -o root -g root -m 0644 "$raiz/systemd/trazalab-recuperar-arranque.service" /etc/systemd/system/trazalab-recuperar-arranque.service
install -o root -g root -m 0644 "$raiz/systemd/trazalab-recuperar-arranque.timer" /etc/systemd/system/trazalab-recuperar-arranque.timer
systemd-tmpfiles --create /etc/tmpfiles.d/trazalab-control.conf
systemctl daemon-reload

echo 'Archivos instalados. El temporizador quedó sin habilitar.'
