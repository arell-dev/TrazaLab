param(
    [string]$BackupDirectory = (Join-Path $PSScriptRoot '..\..\_backups')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'No se encontró .env en la raíz del proyecto.'
}

$dbUserLine = Get-Content -LiteralPath $envFile |
    Where-Object { $_ -match '^SPRING_DATASOURCE_USERNAME=' } |
    Select-Object -First 1

if (-not $dbUserLine) {
    throw 'Define SPRING_DATASOURCE_USERNAME en .env.'
}

$dbUser = ($dbUserLine -split '=', 2)[1].Trim()
$health = docker inspect --format '{{.State.Health.Status}}' trazalab-postgres

if ($LASTEXITCODE -ne 0 -or $health.Trim() -ne 'healthy') {
    throw 'El contenedor trazalab-postgres debe estar activo y saludable.'
}

$counts = docker exec trazalab-postgres psql -U $dbUser -d trazalab -Atc @'
SELECT
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE') || '|' ||
    (SELECT count(*) FROM information_schema.views WHERE table_schema = 'public') || '|' ||
    (SELECT count(*) FROM information_schema.triggers WHERE trigger_schema = 'public') || '|' ||
    (SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'flyway_schema_history') || '|' ||
    (SELECT count(*) FROM incidencia);
'@

$countParts = $counts.Trim() -split '\|'
if ($LASTEXITCODE -ne 0 -or $countParts.Count -ne 5 -or ($countParts[0..3] -join '|') -ne '14|4|6|0') {
    throw "La base no coincide con la línea base esperada (14 tablas, 4 vistas, 6 triggers y sin historial Flyway). Resultado: $($counts.Trim())"
}

$incidentCount = [int]$countParts[4]

$resolvedBackupDirectory = [IO.Path]::GetFullPath($BackupDirectory)
New-Item -ItemType Directory -Force -Path $resolvedBackupDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupFile = Join-Path $resolvedBackupDirectory "trazalab-antes-flyway-$stamp.dump"
$containerBackup = '/tmp/trazalab-antes-flyway.dump'

docker exec trazalab-postgres pg_dump -Fc -U $dbUser -d trazalab -f $containerBackup
if ($LASTEXITCODE -ne 0) {
    throw 'No se pudo crear el respaldo PostgreSQL.'
}

docker cp "trazalab-postgres:$containerBackup" $backupFile
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $backupFile)) {
    throw 'No se pudo copiar el respaldo al equipo.'
}

docker exec trazalab-postgres pg_restore --list $containerBackup | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw 'El respaldo no pasó la comprobación pg_restore --list.'
}

$environmentNames = @(
    'SPRING_FLYWAY_BASELINE_ON_MIGRATE',
    'SPRING_FLYWAY_BASELINE_VERSION',
    'TRAZALAB_CONSOLE_ENABLED',
    'TRAZALAB_SEED_ENABLED'
)
$previousValues = @{}

foreach ($name in $environmentNames) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = 'true'
    $env:SPRING_FLYWAY_BASELINE_VERSION = '1'
    $env:TRAZALAB_CONSOLE_ENABLED = 'false'
    $env:TRAZALAB_SEED_ENABLED = 'false'

    Push-Location $projectRoot
    try {
        & .\mvnw.cmd -q spring-boot:run
        if ($LASTEXITCODE -ne 0) {
            throw 'La aplicación no pudo registrar la línea base Flyway.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    foreach ($name in $environmentNames) {
        $previous = $previousValues[$name]
        if ($null -eq $previous) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        }
        else {
            Set-Item "Env:$name" $previous
        }
    }
}

$verification = docker exec trazalab-postgres psql -U $dbUser -d trazalab -Atc @'
SELECT
    (SELECT version || ':' || type || ':' || success FROM flyway_schema_history WHERE type = 'BASELINE' ORDER BY installed_rank DESC LIMIT 1) || '|' ||
    (SELECT count(*) FROM incidencia);
'@

if ($LASTEXITCODE -ne 0 -or $verification.Trim() -ne "1:BASELINE:true|$incidentCount") {
    throw "No se pudo confirmar la línea base 1. Revisa el respaldo: $backupFile"
}

Write-Output "Línea base Flyway 1 registrada. Respaldo: $backupFile"
