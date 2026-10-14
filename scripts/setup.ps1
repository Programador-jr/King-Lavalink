$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $root "Lavalink.jar"
$configPath = Join-Path $root "application.yml"
$templatePath = Join-Path $root "application.example.yml"

$version = "4.2.2"
$downloadUrl = "https://github.com/lavalink-devs/Lavalink/releases/download/$version/Lavalink.jar"

try {
    $javaVersionOutput = (& java -version 2>&1 | Out-String)
} catch {
    throw "Java não foi encontrado. Instale Java 17 ou superior e abra um novo terminal."
}

if ($javaVersionOutput -notmatch 'version "(d+)') {
    throw "Não foi possível identificar a versão do Java. Saída: $javaVersionOutput"
}
$javaMajor = [int]$Matches[1]
if ($javaMajor -lt 17) {
    throw "Java 17 ou superior é necessário. Versão detectada: $javaMajor"
}

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
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $password = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_')
    $config = $template.Replace('${LAVALINK_SERVER_PASSWORD:change-me-before-running}', $password)
    Set-Content -Path $configPath -Value $config -Encoding utf8
    Write-Host "Configuração local criada: application.yml"
    Write-Host "Senha local gerada automaticamente e guardada apenas em application.yml (ignorado pelo Git)."
} else {
    Write-Host "application.yml já existe; mantendo sua configuração."
}

Write-Host ""
Write-Host "Preparação concluída. Inicie o serviço com: .\scripts\start.ps1"
