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

    # java -version writes to stderr. Redirect it inside cmd.exe so PowerShell
    # does not convert the expected version output into a NativeCommandError.
    $javaVersion = (& $env:ComSpec /d /c "java -version 2>&1" | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw "Não foi possível executar java -version. Verifique a instalação do Java e o PATH."
    }
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

    # The upstream build derives its version from Git state. Applying our patch
    # makes the checkout dirty, so the resulting JAR name may not be 1.18.2.
    # Discover the actual plugin artifact rather than assuming a fixed filename.
    $libsDir = Join-Path $sourceDir "plugin/build/libs"
    $jars = @(
        Get-ChildItem -LiteralPath $libsDir -Filter "youtube-plugin-*.jar" -File |
            Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' }
    )
    if ($jars.Count -eq 0) {
        throw "Build executado, mas nenhum JAR youtube-plugin-*.jar foi encontrado em: $libsDir"
    }
    if ($jars.Count -gt 1) {
        $jarList = ($jars | ForEach-Object { $_.Name }) -join ", "
        throw "Mais de um JAR de plugin encontrado; não é seguro escolher automaticamente: $jarList"
    }
    $jar = $jars[0].FullName

    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
    $output = Join-Path $distDir "youtube-plugin-$sourceTag-king-tv-ua.jar"
    Copy-Item -LiteralPath $jar -Destination $output -Force
    Write-Host ""
    Write-Host "Build e testes concluídos. Artefato de origem: $jar"
    Write-Host "JAR personalizado: $output"
    Write-Host "O Lavalink ativo não foi modificado. Leia docs/youtube-tv-user-agent-fix.md antes de instalar o JAR."
}
finally {
    if (Test-Path -LiteralPath $workRoot) {
        Remove-Item -LiteralPath $workRoot -Recurse -Force
    }
}
