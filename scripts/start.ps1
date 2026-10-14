$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "Lavalink.jar"
$configPath = Join-Path $root "application.yml"

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java não foi encontrado. Instale Java 17 ou superior."
}
if (-not (Test-Path $jarPath)) {
    throw "Lavalink.jar não encontrado. Execute primeiro: .\scripts\setup.ps1"
}
if (-not (Test-Path $configPath)) {
    throw "application.yml não encontrado. Execute primeiro: .\scripts\setup.ps1"
}

Set-Location $root
java -jar $jarPath
