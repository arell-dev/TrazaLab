"""Crea un entorno Docker de prueba; no acepta URL ni credenciales externas."""

import json
import hashlib
import os
import secrets
import subprocess
import tempfile
import threading
import time
import uuid
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
PRUEBAS = Path(__file__).resolve().parent
NOMBRE = "trazalab-ensayo-" + uuid.uuid4().hex[:12]
RED, BASE, APP, CLIENTE = (NOMBRE + sufijo for sufijo in ("-red", "-db", "-app", "-cliente"))
IMAGEN = NOMBRE + ":local"
RECURSOS = []
CLAVE = secrets.token_urlsafe(24)
CLAVE_DB = secrets.token_urlsafe(24)


def docker(*args, entrada=None):
    proceso = subprocess.run(["docker", *args], input=entrada, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if proceso.returncode:
        raise RuntimeError("Docker falló en " + args[0] + ".")
    return proceso.stdout.strip()


def esperar(comprobar, segundos):
    limite = time.monotonic() + segundos
    while time.monotonic() < limite:
        if comprobar():
            return
        time.sleep(2)
    raise RuntimeError("Servicio temporal no quedó listo dentro del plazo.")


def disponible(*args, entrada=None):
    try:
        docker(*args, entrada=entrada)
        return True
    except RuntimeError:
        return False


def limpiar():
    pendientes = []
    for tipo, nombre in reversed(RECURSOS):
        try:
            if tipo == "container":
                docker("rm", "-f", nombre)
            elif tipo == "network":
                docker("network", "rm", nombre)
            else:
                docker("image", "rm", nombre)
        except RuntimeError:
            pendientes.append(nombre)
    if pendientes:
        raise RuntimeError("Recursos temporales pendientes: " + ", ".join(pendientes))


def principal():
    if not (RAIZ / "target" / "trazalab-0.0.1-SNAPSHOT.jar").is_file():
        raise RuntimeError("Compila primero el JAR con Maven.")
    if os.environ.get("DOCKER_HOST") or os.environ.get("DOCKER_CONTEXT"):
        raise RuntimeError("Quita DOCKER_HOST y DOCKER_CONTEXT para usar únicamente el daemon local.")
    endpoint = docker("context", "inspect", "--format", "{{.Endpoints.docker.Host}}")
    if endpoint not in ("npipe:////./pipe/dockerDesktopLinuxEngine", "npipe:////./pipe/docker_engine", "unix:///var/run/docker.sock"):
        raise RuntimeError("El contexto Docker no apunta a un daemon local admitido.")
    docker("build", "--tag", IMAGEN, str(RAIZ))
    RECURSOS.append(("image", IMAGEN))
    revision = subprocess.run(["git", "-C", str(RAIZ), "rev-parse", "HEAD"], text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    cambios = subprocess.run(["git", "-C", str(RAIZ), "status", "--porcelain"], text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    jar = RAIZ / "target" / "trazalab-0.0.1-SNAPSHOT.jar"
    print(json.dumps({"revision_git": revision.stdout.strip() if revision.returncode == 0 else None, "cambios_locales": bool(cambios.stdout) if cambios.returncode == 0 else None, "sha256_jar": hashlib.sha256(jar.read_bytes()).hexdigest(), "imagen_app_id": docker("image", "inspect", "--format", "{{.Id}}", IMAGEN)}))
    docker("network", "create", "--internal", RED)
    RECURSOS.append(("network", RED))
    with tempfile.TemporaryDirectory(prefix="trazalab-carga-") as temporal:
        env_db, env_app, env_cliente = (Path(temporal) / (nombre + ".env") for nombre in ("db", "app", "cliente"))
        env_db.write_text(f"POSTGRES_USER=ensayo\nPOSTGRES_DB=ensayo\nPOSTGRES_PASSWORD={CLAVE_DB}\n", encoding="utf-8")
        env_app.write_text(f"SPRING_DATASOURCE_URL=jdbc:postgresql://{BASE}:5432/ensayo\nSPRING_DATASOURCE_USERNAME=ensayo\nSPRING_DATASOURCE_PASSWORD={CLAVE_DB}\n", encoding="utf-8")
        env_cliente.write_text(f"ENSAYO_APP={APP}\nENSAYO_CLAVE={CLAVE}\n", encoding="utf-8")
        base_id = docker("create", "--name", BASE, "--network", RED, "--tmpfs", "/var/lib/postgresql/data:rw,nosuid,size=512m", "--env-file", str(env_db), "postgres:16")
        RECURSOS.append(("container", base_id))
        docker("start", base_id)
        esperar(lambda: disponible("exec", BASE, "pg_isready", "-U", "ensayo"), 60)
        app_id = docker("create", "--name", APP, "--network", RED, "--env-file", str(env_app), IMAGEN)
        RECURSOS.append(("container", app_id))
        docker("start", app_id)
        cliente_id = docker("create", "--name", CLIENTE, "--network", RED, "--env-file", str(env_cliente), "--mount", f"type=bind,source={PRUEBAS / 'cliente.py'},target=/cliente.py,readonly", "python:3.12-alpine", "sleep", "600")
        RECURSOS.append(("container", cliente_id))
        docker("start", cliente_id)
        print(json.dumps({"imagen_postgresql_id": docker("inspect", "--format", "{{.Image}}", BASE), "imagen_cliente_id": docker("inspect", "--format", "{{.Image}}", CLIENTE)}))
        red = json.loads(docker("network", "inspect", RED))[0]
        if not red["Internal"] or set(red["Containers"]) != {docker("inspect", "--format", "{{.Id}}", nombre) for nombre in (BASE, APP, CLIENTE)}:
            raise RuntimeError("La red temporal no está aislada o tiene participantes inesperados.")
        for nombre in (BASE, APP, CLIENTE):
            datos = json.loads(docker("inspect", nombre))[0]
            if set(datos["NetworkSettings"]["Networks"]) != {RED}:
                raise RuntimeError("Un contenedor temporal tiene una red adicional.")
            if datos["NetworkSettings"]["Ports"] and any(datos["NetworkSettings"]["Ports"].values()):
                raise RuntimeError("Un contenedor temporal expone puertos.")
            if nombre in (BASE, APP) and datos["Mounts"]:
                raise RuntimeError("La aplicación o base temporal tiene un volumen persistente o montaje del host.")
        consulta = "SELECT count(*) FROM flyway_schema_history WHERE success AND version='7';"
        def migrado():
            try:
                return docker("exec", "-i", BASE, "psql", "-U", "ensayo", "-d", "ensayo", "-At", entrada=consulta) == "1"
            except RuntimeError:
                return False
        esperar(migrado, 180)
        sql = "CREATE EXTENSION IF NOT EXISTS pgcrypto;\n"
        sql += "INSERT INTO usuario (rol_id,nombres,apellidos,correo,estado,password_hash) SELECT rol_id,'Ensayo','Local','ensayo' || n || '@prueba.local','activo',crypt('" + CLAVE + "',gen_salt('bf')) FROM rol CROSS JOIN generate_series(1,20) n WHERE nombre='Administrador';\n"
        sql += "INSERT INTO computadora (codigo_qr,numero_serie,marca,modelo,ubicacion_id,estado) SELECT 'QR-TEST-' || lpad(n::text,3,'0'),'SERIE-TEST-' || n,'Ensayo','Local',ubicacion_id,'operativa' FROM ubicacion CROSS JOIN generate_series(1,20) n WHERE nombre='A0301' LIMIT 20;\n"
        docker("exec", "-i", BASE, "psql", "-U", "ensayo", "-d", "ensayo", "-v", "ON_ERROR_STOP=1", entrada=sql)
        conteo = docker("exec", "-i", BASE, "psql", "-U", "ensayo", "-d", "ensayo", "-At", "-v", "ON_ERROR_STOP=1", entrada="SELECT count(*) FROM computadora WHERE codigo_qr LIKE 'QR-TEST-%';")
        if conteo != "20":
            raise RuntimeError("No se crearon exactamente 20 equipos sintéticos.")
        print(json.dumps({"equipos_sinteticos": int(conteo)}))
        historial = (PRUEBAS / "datos-sinteticos.sql").read_text(encoding="utf-8")
        docker("exec", "-i", BASE, "psql", "-U", "ensayo", "-d", "ensayo", "-v", "ON_ERROR_STOP=1", entrada=historial)
        consulta_historial = "SELECT (SELECT count(*) FROM incidencia WHERE descripcion LIKE 'Incidencia sintetica %') || ',' || (SELECT count(*) FROM falla WHERE descripcion_tecnica = 'Falla sintetica') || ',' || (SELECT count(*) FROM mantenimiento WHERE descripcion = 'Mantenimiento sintetico') || ',' || (SELECT count(*) FROM componente WHERE numero_serie LIKE 'COMP-TEST-%') || ',' || (SELECT count(*) FROM computadora_componente JOIN componente USING (componente_id) WHERE componente.numero_serie LIKE 'COMP-TEST-%');"
        recuentos = docker("exec", "-i", BASE, "psql", "-U", "ensayo", "-d", "ensayo", "-At", "-v", "ON_ERROR_STOP=1", entrada=consulta_historial)
        if recuentos != "60,60,60,20,20":
            raise RuntimeError("El historial sintético no tiene los recuentos esperados.")
        print(json.dumps({"incidencias": 60, "fallas": 60, "mantenimientos": 60, "componentes": 20, "asignaciones": 20}))
        sondeo = "import urllib.request; urllib.request.urlopen('http://'+__import__('os').environ['ENSAYO_APP']+':8080/api/health',timeout=3).read()"
        esperar(lambda: disponible("exec", CLIENTE, "python", "-c", sondeo), 180)
        finalizado = threading.Event()
        muestras = []
        def observar():
            while not finalizado.is_set():
                try:
                    muestras.append(docker("stats", "--no-stream", "--format", "{{.Name}} {{.CPUPerc}} {{.MemUsage}}", APP, BASE))
                except RuntimeError:
                    muestras.append("No se pudo consultar Docker stats.")
                finalizado.wait(1)
        observador = threading.Thread(target=observar, daemon=True)
        observador.start()
        try:
            proceso = subprocess.run(["docker", "exec", CLIENTE, "python", "/cliente.py"], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        finally:
            finalizado.set()
            observador.join()
        print(proceso.stdout)
        print("Muestras Docker durante el ensayo:")
        for muestra in muestras:
            print(muestra)
        print("Disco temporal PostgreSQL:", docker("exec", BASE, "df", "-h", "/var/lib/postgresql/data"))
        if proceso.returncode:
            raise RuntimeError("La carga o alguno de sus accesos falló; revisar la salida de métricas.")
        print("Ensayo local sintético; no determina capacidad de EC2 ni valida el piloto.")


if __name__ == "__main__":
    try:
        principal()
    finally:
        limpiar()
