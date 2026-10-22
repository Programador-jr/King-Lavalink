$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) {
    $python = Get-Command py -ErrorAction SilentlyContinue
}
if (-not $python) {
    throw "Python 3 não foi encontrado. Instale Python 3 para abrir o painel."
}

Set-Location $root
if ($python.Name -eq "py.exe") {
    & $python.Source -3 (Join-Path $PSScriptRoot "panel.py")
} else {
    & $python.Source (Join-Path $PSScriptRoot "panel.py")
}
