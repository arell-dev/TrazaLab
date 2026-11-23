#!/usr/bin/env python3

import os
import socket
import sys


RUTA = "/run/trazalab-control/control.sock"
COMANDOS = {"OBSERVAR", "INICIAR", "ESTADO", "CANCELAR"}


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in COMANDOS or not hasattr(socket, "AF_UNIX"):
        return 2

    if os.geteuid() != 0:
        return 2

    try:
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as conexion:
            conexion.settimeout(2)
            conexion.connect(RUTA)
            conexion.sendall((sys.argv[1] + "\n").encode("ascii"))
            respuesta = bytearray()
            while len(respuesta) <= 128 and not respuesta.endswith(b"\n"):
                bloque = conexion.recv(129 - len(respuesta))
                if not bloque:
                    return 1
                respuesta.extend(bloque)
            if len(respuesta) > 128 or not respuesta.endswith(b"\n"):
                return 1
    except (OSError, TimeoutError):
        return 1

    try:
        mensaje = respuesta.decode("ascii").strip()
    except UnicodeDecodeError:
        return 1

    print(mensaje)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
