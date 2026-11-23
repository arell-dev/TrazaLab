from pathlib import Path
import os
import subprocess
import sys
import tempfile

source = Path('deploy/publicacion/ec2/trazalab-desplegar.sh').read_text(encoding='utf-8')
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
marcador_publicacion = '  publicar_y_desplegar:\n    needs:\n      - validar\n      - verificar\n'
if marcador_publicacion not in workflow:
    raise SystemExit('La publicaciÃ³n no estÃ¡ bloqueada por validar y verificar.')
bloque_needs = workflow.split(marcador_publicacion, 1)[1].split('    steps:', 1)[0]
if 'if:' in bloque_needs or 'always()' in bloque_needs:
    raise SystemExit('El job de publicaciÃ³n puede eludir el resultado de CI.')
if '  verificar:\n    needs: validar\n    uses: ./.github/workflows/ci.yml' not in workflow:
    raise SystemExit('La publicaciÃ³n no invoca la CI reutilizable.')
if 'concurrency:\n  group: trazalab-publicacion\n  queue: max\n' not in workflow:
    raise SystemExit('No se encontrÃ³ la cola de publicaciones concurrentes.')
if 'git merge-base --is-ancestor "$etiquetado" refs/remotes/origin/main' not in workflow:
    raise SystemExit('No se comprueba que la etiqueta descienda de main.')
if '"$EVENT_NAME" == workflow_dispatch && "$REF" != refs/heads/main' not in workflow:
    raise SystemExit('No se restringe el reintento manual a main.')
if '"$EVENT_NAME" == push && "$etiquetado" != "$actual"' not in workflow:
    raise SystemExit('El push no verifica el commit exacto de la etiqueta.')
if 'if: success()' not in workflow:
    raise SystemExit('La Release no espera el Ã©xito del despliegue.')
if 'stopped)\n              echo \'EC2_ARRANQUE_SOLICITADO=true\'' not in workflow:
    raise SystemExit('No se reconoce la rama de instancia detenida.')
if 'aws ec2 start-instances --instance-ids "$INSTANCE_ID"' not in workflow:
    raise SystemExit('La rama detenida no solicita el encendido.')
if 'aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"' not in workflow:
    raise SystemExit('No se espera que EC2 pase a running.')
if 'aws ssm send-command' not in workflow or 'aws ssm get-command-invocation' not in workflow:
    raise SystemExit('No se valida el ciclo de envÃ­o y resultado de SSM.')

if 'InstanceId=$INSTANCE_ID' not in workflow:
    raise SystemExit('SSM does not receive the instance identifier.')

documento_ssm = Path('deploy/publicacion/ssm/documento-despliegue-ssm.yaml').read_text(encoding='utf-8')
if '  InstanceId:' not in documento_ssm or '$SSM_InstanceId' not in documento_ssm:
    raise SystemExit('The SSM document does not pass InstanceId to the helper.')

with tempfile.TemporaryDirectory(prefix='trazalab-etiqueta-') as temporal:
    repositorio = Path(temporal)

    def git(*argumentos, esperado=0):
        resultado = subprocess.run(
            ['git', *argumentos],
            cwd=repositorio,
            text=True,
            encoding='utf-8',
            capture_output=True,
        )
        if resultado.returncode != esperado:
            raise SystemExit(f'Git {argumentos} devolviÃ³ {resultado.returncode}: {resultado.stderr}')
        return resultado

    git('init', '--initial-branch=main')
    (repositorio / 'base.txt').write_text('base\n', encoding='utf-8')
    git('add', 'base.txt')
    git('-c', 'user.name=Prueba', '-c', 'user.email=prueba@example.invalid', '-c', 'commit.gpgsign=false', 'commit', '-m', 'base')
    git('switch', '-c', 'prueba-externa')
    (repositorio / 'externa.txt').write_text('fuera de main\n', encoding='utf-8')
    git('add', 'externa.txt')
    git('-c', 'user.name=Prueba', '-c', 'user.email=prueba@example.invalid', '-c', 'commit.gpgsign=false', 'commit', '-m', 'externa')
    git('tag', 'v9.9.9')
    git('switch', 'main')
    (repositorio / 'main.txt').write_text('main\n', encoding='utf-8')
    git('add', 'main.txt')
    git('-c', 'user.name=Prueba', '-c', 'user.email=prueba@example.invalid', '-c', 'commit.gpgsign=false', 'commit', '-m', 'main')
    git('merge-base', '--is-ancestor', 'refs/tags/v9.9.9', 'refs/heads/main', esperado=1)
    git('tag', 'v0.0.1')
    git('merge-base', '--is-ancestor', 'refs/tags/v0.0.1', 'refs/heads/main')

marcador = 'python3 - "$VERSION" "$ultima" <<\'PY\'\n'
if marcador not in workflow:
    raise SystemExit('No se encontró la validación de versiones del workflow de publicación.')

validacion = workflow.split(marcador, 1)[1].split('\n          PY\n', 1)[0]
validacion = '\n'.join(linea[10:] if linea.startswith('          ') else linea for linea in validacion.splitlines())
casos_release = [
    (('v0.0.1', ''), 0),
    (('v0.0.2', ''), 1),
    (('v0.0.2', 'v0.0.1'), 0),
    (('v0.0.11', 'v0.0.1'), 0),
    (('v0.0.12', 'v0.0.11'), 0),
    (('v0.1.0', 'v0.0.1'), 0),
    (('v1.0.0', 'v0.9.9'), 0),
    (('v0.2.0', 'v0.0.1'), 0),
    (('v0.0.1', 'v0.0.2'), 1),
    (('v0.1.1', 'v0.0.1'), 1),
    (('v1.1.0', 'v0.9.9'), 1),
    (('v0.0.1', 'v0.0.1'), 1),
]
for arguments, expected in casos_release:
    result = subprocess.run([sys.executable, '-', *arguments], input=validacion, text=True, encoding='utf-8', capture_output=True, env=dict(os.environ, PYTHONIOENCODING='utf-8'))
    if (result.returncode == 0) != (expected == 0):
        raise SystemExit(f'Caso de publicación inesperado: {arguments}, código {result.returncode}: {result.stderr}')

print(f'{len(cases)} casos del helper y {len(casos_release)} casos de publicación correctos.')
