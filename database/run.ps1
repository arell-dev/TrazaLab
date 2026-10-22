$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$envPath = Join-Path $projectRoot ".env"

if (-not (Test-Path -LiteralPath $envPath)) {
    throw "No existe .env en la raíz del proyecto."
}

foreach ($line in [System.IO.File]::ReadAllLines($envPath)) {
    $match = [System.Text.RegularExpressions.Regex]::Match($line, '^\s*([^#=]+?)\s*=\s*(.*)\s*$')
    if ($match.Success) {
        $key = $match.Groups[1].Value.Trim()
        $value = $match.Groups[2].Value.Trim()
        Set-Item -Path "Env:$key" -Value $value
    }
}

Set-Location -LiteralPath $projectRoot
& (Join-Path $projectRoot "mvnw.cmd") spring-boot:run
exit $LASTEXITCODE
