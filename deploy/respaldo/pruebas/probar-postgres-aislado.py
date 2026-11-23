"""Comprueba el arranque y una restauración sintética del pod propuesto usando Docker."""

import json
import subprocess
import sys
import time
import uuid
from pathlib import Path


def ejecutar(*argumentos, entrada=None):
    return subprocess.run(argumentos, input=entrada, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)


ayudante = Path(__file__).resolve().parents[1] / "ec2" / "trazalab-respaldo-verificado.sh"
contenido = ayudante.read_text(encoding="utf-8")
inicio = contenido.index("python3 - \"$pod\" \"$imagen\" > \"$directorio/pod.json\" <<'PY'")
generador = contenido[inicio:].split("\n", 1)[1].split("\nPY\n", 1)[0]
imagen = "postgres@sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d"
manifiesto = json.loads(ejecutar(sys.executable, "-c", generador, "restauracion", imagen).stdout)
contenedor = manifiesto["spec"]["containers"][0]
nombre = "trazalab-ensayo-respaldo-" + uuid.uuid4().hex[:12]
creado = False

try:
    parametros = ["docker", "run", "-d", "--name", nombre, "--network", "none", "--user", "999:999", "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--memory", "256m", "--tmpfs", "/var/lib/postgresql/data:rw,noexec,nosuid,size=128m,mode=1777"]
    for variable in contenedor["env"]:
        parametros.extend(["-e", f"{variable['name']}={variable['value']}"])
    parametros.extend([imagen, *contenedor["args"]])
    ejecutar(*parametros)
    creado = True

    for intento in range(60):
        listo = subprocess.run(["docker", "exec", nombre, *contenedor["readinessProbe"]["exec"]["command"]], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if listo.returncode == 0:
            break
        if ejecutar("docker", "inspect", "--format", "{{.State.Running}}", nombre).stdout.strip() != b"true":
            raise RuntimeError("PostgreSQL terminó antes de quedar listo.")
        time.sleep(1)
    else:
        raise RuntimeError("PostgreSQL no quedó listo dentro del plazo.")

    socket = "/var/run/postgresql"
    escucha = ejecutar("docker", "exec", nombre, "psql", "-h", socket, "-U", "postgres", "-d", "postgres", "-Atc", "SHOW listen_addresses;").stdout.strip()
    if escucha:
        raise RuntimeError("PostgreSQL no debe escuchar conexiones TCP.")
    ejecutar("docker", "exec", nombre, "psql", "-h", socket, "-U", "postgres", "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-c", "CREATE TABLE prueba_respaldo (valor INTEGER); INSERT INTO prueba_respaldo VALUES (42);")
    copia = ejecutar("docker", "exec", nombre, "pg_dump", "-h", socket, "-U", "postgres", "-d", "postgres", "--format=custom").stdout
    ejecutar("docker", "exec", nombre, "createdb", "-h", socket, "-U", "postgres", "restauracion")
    ejecutar("docker", "exec", "-i", nombre, "pg_restore", "--exit-on-error", "--no-owner", "--no-privileges", "-h", socket, "-U", "postgres", "-d", "restauracion", entrada=copia)
    resultado = ejecutar("docker", "exec", nombre, "psql", "-h", socket, "-U", "postgres", "-d", "restauracion", "-Atc", "SELECT valor FROM prueba_respaldo;").stdout.strip()
    if resultado != b"42":
        raise RuntimeError("El registro sintético restaurado no coincide.")
    print("OK: PostgreSQL aislado arrancó y restauró el dato sintético por socket Unix.")
finally:
    if creado:
        ejecutar("docker", "rm", "-f", nombre)
