# Build a local youtube-source 1.18.2 plugin with the TV User-Agent workaround.
# Requires Git and JDK 17+. Does not modify the active Lavalink installation.
$ErrorActionPreference = "Stop"

$sourceTag = "1.18.2"
$sourceUrl = "https://github.com/lavalink-devs/youtube-source.git"
$repoRoot = Split-Path -Parent $PSScriptRoot
$patchFile = Join-Path $repoRoot "patches/youtube-source-1.18.2-tv-user-agent.patch"
$workRoot = Join-Path $env:TEMP ("king-youtube-source-" + [guid]::NewGuid().ToString("N"))
$sourceDir = Join-Path $workRoot "youtube-source"
$distDir = Join-Path $repoRoot "dist"

try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        throw "Git não foi encontrado no PATH."
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw "Java não foi encontrado no PATH. Instale um JDK 17 ou superior."
    }
    if (-not (Test-Path -LiteralPath $patchFile)) {
        throw "Arquivo de patch não encontrado: $patchFile"
    }

    $javaVersion = (& java -version 2>&1 | Out-String)
    if ($javaVersion -notmatch 'version "(?:1[7-9]|[2-9][0-9])\.') {
        throw "É necessário JDK 17 ou superior. Versão detectada: $javaVersion"
    }

    New-Item -ItemType Directory -Path $workRoot -Force | Out-Null
    git clone --depth 1 --branch $sourceTag $sourceUrl $sourceDir
    if ($LASTEXITCODE -ne 0) { throw "Falha ao clonar youtube-source tag $sourceTag." }

    Push-Location $sourceDir
    try {
        git apply --check $patchFile
        if ($LASTEXITCODE -ne 0) {
            throw "O patch não se aplica à tag $sourceTag. Nenhum JAR foi gerado."
        }
        git apply $patchFile
        if ($LASTEXITCODE -ne 0) { throw "Falha ao aplicar o patch." }

        & .\gradlew.bat :plugin:build
        if ($LASTEXITCODE -ne 0) { throw "Build/testes do plugin falharam." }
    }
    finally {
        Pop-Location
    }

    $jar = Join-Path $sourceDir "plugin/build/libs/youtube-plugin-$sourceTag.jar"
    if (-not (Test-Path -LiteralPath $jar)) {
        throw "Build concluído, mas o JAR esperado não foi encontrado: $jar"
    }

    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
    $output = Join-Path $distDir "youtube-plugin-$sourceTag-king-tv-ua.jar"
    Copy-Item -LiteralPath $jar -Destination $output -Force
    Write-Host ""
    Write-Host "Build e testes concluídos. JAR personalizado: $output"
    Write-Host "O Lavalink ativo não foi modificado. Leia docs/youtube-tv-user-agent-fix.md antes de instalar o JAR."
}
finally {
    if (Test-Path -LiteralPath $workRoot) {
        Remove-Item -LiteralPath $workRoot -Recurse -Force
    }
}
