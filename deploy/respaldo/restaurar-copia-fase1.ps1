param(
    [string]$Perfil = 'trazalab-proyecto',
    [ValidateSet('fase1', 'incidencia')]
    [string]$Copia = 'fase1'
)

$ErrorActionPreference = 'Stop'
$bucket = 'trazalab-recuperacion-970909363773-us-east-2'
if ($Copia -eq 'fase1') {
    $objeto = 'fase1/fresh-canvas-20261120.dump'
    $version = '2cBfd.P5UCLnWDMdpR1YzaePK1SFITh9'
    $hashEsperado = 'ba3f5521881e1e50e8cbb11b68fc36a0a6b10ff441f6f544537eca95bd998037'
    $recuentosEsperados = '2,1,1,0,0,7,0,16'
} else {
    $objeto = 'fase1/actual-incidencia-20261120-1950.dump'
    $version = 'emHANPxMjSeTe98dEMy3_Ng5HZ3Hrt.I'
    $hashEsperado = '574f7f18cca733835868fd45e94dd4ab5ab464fb621880043066312082a01415'
    $recuentosEsperados = '2,1,2,0,0,7,0,16'
}
$nombre = 'trazalab-recuperacion-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$directorio = Join-Path ([IO.Path]::GetTempPath()) $nombre
$archivo = Join-Path $directorio 'respaldo.dump'
$contenedorCreado = $false

try {
    $identidad = aws sts get-caller-identity --profile $Perfil --region us-east-2 --output json | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or $identidad.Account -ne '970909363773') {
        throw 'La identidad AWS no corresponde al proyecto de destino.'
    }

    $usuario = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $permisos = [Security.AccessControl.DirectorySecurity]::new()
    $permisos.SetAccessRuleProtection($true, $false)
    $regla = [Security.AccessControl.FileSystemAccessRule]::new(
        $usuario, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'
    )
    $permisos.AddAccessRule($regla)
    [IO.FileSystemAclExtensions]::CreateDirectory($permisos, $directorio) | Out-Null

    aws s3api get-object --bucket $bucket --key $objeto --version-id $version --region us-east-2 --profile $Perfil $archivo --output json | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'No se pudo descargar la versión indicada.'
    }
    if ((Get-FileHash -LiteralPath $archivo -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hashEsperado) {
        throw 'El SHA-256 no coincide; se cancela la restauración.'
    }

    docker run -d --name $nombre --network none --tmpfs /var/lib/postgresql/data:rw,noexec,nosuid,size=512m -e POSTGRES_HOST_AUTH_METHOD=trust postgres:16 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'No se pudo crear el contenedor aislado.'
    }
    $contenedorCreado = $true

    $listo = $false
    for ($intento = 0; $intento -lt 30; $intento++) {
        docker exec $nombre pg_isready -U postgres 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $listo = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $listo) {
        throw 'PostgreSQL no quedó listo dentro del plazo.'
    }

    docker exec $nombre createdb -U postgres restauracion
    if ($LASTEXITCODE -ne 0) {
        throw 'No se pudo crear la base aislada.'
    }
    $proceso = [Diagnostics.Process]::new()
    $proceso.StartInfo.FileName = (Get-Command docker.exe).Source
    $proceso.StartInfo.Arguments = "exec -i $nombre pg_restore -U postgres --no-owner --no-privileges --exit-on-error -d restauracion"
    $proceso.StartInfo.UseShellExecute = $false
    $proceso.StartInfo.RedirectStandardInput = $true
    if (-not $proceso.Start()) {
        throw 'No se pudo iniciar pg_restore.'
    }
    $lectura = [IO.File]::OpenRead($archivo)
    try {
        $lectura.CopyTo($proceso.StandardInput.BaseStream)
    } finally {
        $lectura.Dispose()
        $proceso.StandardInput.Close()
    }
    $proceso.WaitForExit()
    if ($proceso.ExitCode -ne 0) {
        throw 'La restauración falló.'
    }

    $consulta = "SELECT (SELECT count(*) FROM usuario WHERE estado='activo') || ',' || (SELECT count(*) FROM computadora) || ',' || (SELECT count(*) FROM incidencia) || ',' || (SELECT count(*) FROM mantenimiento) || ',' || (SELECT count(*) FROM componente) || ',' || (SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2','3','4','5','6','7')) || ',' || (SELECT count(*) FROM flyway_schema_history WHERE NOT success) || ',' || (SELECT count(*) FROM pg_tables WHERE schemaname='public');"
    $resultado = docker exec $nombre psql -U postgres -d restauracion -Atc $consulta
    if ($LASTEXITCODE -ne 0 -or $resultado.Trim() -ne $recuentosEsperados) {
        throw "Los recuentos no coinciden con la instantánea: $resultado"
    }
    $comandoEsquema = 'pg_dump -U postgres --schema-only --schema=public --no-owner --no-privileges restauracion | sed ''/^\\restrict /d; /^\\unrestrict /d; /^-- Dumped from database version /d; /^-- Dumped by pg_dump version /d'' | sha256sum'
    $huellaEsquema = docker exec $nombre bash -o pipefail -c $comandoEsquema
    if ($LASTEXITCODE -ne 0 -or $huellaEsquema -notmatch '^[0-9a-f]{64}\s+-\s*$') {
        throw 'No se pudo calcular la huella del esquema restaurado.'
    }
    Write-Output "SHA-256 del esquema público normalizado: $($huellaEsquema.Split(' ')[0])"
    Write-Output "Restauración verificada para '$Copia': hash y recuentos $resultado (usuarios activos, computadoras, incidencias, mantenimientos, componentes, migraciones exitosas, fallidas y tablas)."
} finally {
    if ($contenedorCreado) {
        docker rm -f $nombre | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "No se pudo eliminar el contenedor temporal $nombre."
        }
    }
    if (Test-Path -LiteralPath $archivo) {
        Remove-Item -LiteralPath $archivo -Force
    }
    if (Test-Path -LiteralPath $directorio) {
        Remove-Item -LiteralPath $directorio -Force
    }
}
