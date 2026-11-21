from pathlib import Path
import os
import subprocess
import sys

source = Path('deploy/publicacion/trazalab-desplegar.sh').read_text(encoding='utf-8')
code = source.split("<<'PY'\n", 1)[1].split('\nPY\n', 1)[0]
cases = [
    (('v0.0.1', 'v0.0.2', 'a', 'b'), 0),
    (('v0.0.2', 'v0.0.1', 'a', 'b'), 1),
    (('v0.0.1', 'v0.0.1', 'a', 'b'), 1),
    (('v0.0.1', 'v0.0.1', 'a', 'a'), 0),
]
for arguments, expected in cases:
    result = subprocess.run([sys.executable, '-', *arguments], input=code, text=True, encoding='utf-8', capture_output=True, env=dict(os.environ, PYTHONIOENCODING='utf-8'))
    if (result.returncode == 0) != (expected == 0):
        raise SystemExit(f'Caso inesperado: {arguments}, código {result.returncode}: {result.stderr}')

workflow = Path('.github/workflows/release.yml').read_text(encoding='utf-8')
marcador = 'python3 - "$VERSION" "$ultima" <<\'PY\'\n'
if marcador not in workflow:
    raise SystemExit('No se encontró la validación de versiones del workflow de publicación.')

validacion = workflow.split(marcador, 1)[1].split('\n          PY\n', 1)[0]
validacion = '\n'.join(linea[10:] if linea.startswith('          ') else linea for linea in validacion.splitlines())
casos_release = [
    (('v0.0.1', ''), 0),
    (('v0.0.2', ''), 1),
    (('v0.0.2', 'v0.0.1'), 0),
    (('v0.1.0', 'v0.0.1'), 0),
    (('v1.0.0', 'v0.9.9'), 0),
    (('v0.0.3', 'v0.0.1'), 1),
    (('v0.1.1', 'v0.0.1'), 1),
    (('v0.2.0', 'v0.0.1'), 1),
    (('v0.0.1', 'v0.0.1'), 1),
]
for arguments, expected in casos_release:
    result = subprocess.run([sys.executable, '-', *arguments], input=validacion, text=True, encoding='utf-8', capture_output=True, env=dict(os.environ, PYTHONIOENCODING='utf-8'))
    if (result.returncode == 0) != (expected == 0):
        raise SystemExit(f'Caso de publicación inesperado: {arguments}, código {result.returncode}: {result.stderr}')

print(f'{len(cases)} casos del helper y {len(casos_release)} casos de publicación correctos.')
