from pathlib import Path
import json
import os
import subprocess
import sys
import tempfile


contenido = Path('deploy/respaldo/trazalab-respaldo-verificado.sh').read_text(encoding='utf-8')
fragmentos = []
entorno = dict(os.environ, PYTHONIOENCODING='utf-8')
for parte in contenido.split("<<'PY'\n")[1:]:
    fragmentos.append(parte.split('\nPY\n', 1)[0])
if len(fragmentos) != 2:
    raise SystemExit('No se encontraron los dos fragmentos Python esperados.')

with tempfile.TemporaryDirectory() as temporal:
    respuesta = Path(temporal) / 'respuesta.json'
    respuesta.write_text(json.dumps({'ServerSideEncryption': 'AES256', 'VersionId': 'v1', 'Metadata': {'sha256': 'digest'}}), encoding='utf-8')
    argumentos = [sys.executable, '-', str(respuesta), 'digest', 'v1']
    correcto = subprocess.run(argumentos, input=fragmentos[0], text=True, capture_output=True, encoding='utf-8', env=entorno)
    if correcto.returncode != 0:
        raise SystemExit('Se rechazó una respuesta S3 válida.')
    respuesta.write_text(json.dumps({'ServerSideEncryption': 'AES256', 'VersionId': 'v2', 'Metadata': {'sha256': 'digest'}}), encoding='utf-8')
    incorrecto = subprocess.run(argumentos, input=fragmentos[0], text=True, capture_output=True, encoding='utf-8', env=entorno)
    if incorrecto.returncode == 0:
        raise SystemExit('Se aceptó una versión S3 incorrecta.')

resultado = subprocess.run([sys.executable, '-', 'restauracion-prueba', 'postgres:prueba'], input=fragmentos[1], text=True, capture_output=True, encoding='utf-8', check=True, env=entorno)
pod = json.loads(resultado.stdout)
contenedor = pod['spec']['containers'][0]
assert pod['spec']['automountServiceAccountToken'] is False
assert pod['spec']['restartPolicy'] == 'Never'
assert 'listen_addresses=' in contenedor['args']
assert pod['spec']['volumes'][0]['emptyDir']['sizeLimit'] == '1Gi'
assert contenedor['securityContext']['allowPrivilegeEscalation'] is False
print('Metadatos S3 y estructura del pod de restauración validados.')
