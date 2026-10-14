$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "Lavalink.jar"
$configPath = Join-Path $root "application.yml"
$templatePath = Join-Path $root "application.example.yml"

$version = "4.2.2"
$downloadUrl = "https://github.com/lavalink-devs/Lavalink/releases/download/$version/Lavalink.jar"

# Capture both stdout and stderr without relying on the PowerShell native-command error handling.
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCommand) {
    throw "Java não foi encontrado no PATH. Verifique a instalação do Java 17 ou superior."
}

$javaVersionOutput = (& java -version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0) {
    throw "O comando java -version falhou. Saída: $javaVersionOutput"
}

# Supports both legacy output (java version "17...") and modern output (openjdk 25...).
if ($javaVersionOutput -match '(?:version\s+")?(\d+)(?:\.\d+)*') {
    $javaMajor = [int]$Matches[1]
} else {
    throw "Não foi possível identificar a versão do Java. Saída: $javaVersionOutput"
}

if ($javaMajor -lt 17) {
    throw "Java 17 ou superior é necessário. Versão detectada: $javaMajor"
}

Write-Host "Java detectado: versão $javaMajor"

if (-not (Test-Path $jarPath)) {
    Write-Host "Baixando Lavalink $version..."
    Invoke-WebRequest -Uri $downloadUrl -OutFile $jarPath
    if (-not (Test-Path $jarPath) -or (Get-Item $jarPath).Length -lt 1MB) {
        Remove-Item $jarPath -ErrorAction SilentlyContinue
        throw "O download do Lavalink falhou ou o arquivo parece inválido."
    }
} else {
    Write-Host "Lavalink.jar já existe; mantendo o arquivo atual."
}

if (-not (Test-Path $configPath)) {
    if (-not (Test-Path $templatePath)) {
        throw "Arquivo application.example.yml não encontrado."
    }
    $template = Get-Content $templatePath -Raw
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $password = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
    $config = $template.Replace('${LAVALINK_SERVER_PASSWORD:change-me-before-running}', $password)
    Set-Content -Path $configPath -Value $config -Encoding utf8
    Write-Host "Configuração local criada: application.yml"
    Write-Host "Senha local aleatória guardada apenas em application.yml (ignorado pelo Git)."
} else {
    Write-Host "application.yml já existe; mantendo sua configuração."
}

Write-Host ""
Write-Host "Preparação concluída. Inicie o serviço com: .\scripts\start.ps1"
