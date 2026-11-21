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
print(f'{len(cases)} casos de orden y repetición de versiones correctos.')
