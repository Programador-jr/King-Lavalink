$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$stage = Join-Path $root "dist\shardcloud"
$archive = Join-Path $root "dist\king-lavalink-shardcloud.zip"
$youtubePlugin = Join-Path $root "dist\youtube-plugin-1.18.2-king-tv-ua.jar"
$previousPassword = ""
$previousConfig = Join-Path $stage "application.yml"
if (Test-Path -LiteralPath $previousConfig) {
    $previousYaml = Get-Content -LiteralPath $previousConfig -Raw
    $passwordMatch = [regex]::Match($previousYaml, '(?m)^\s{4}password:\s*["'']?([^"''\s#]+)')
    if ($passwordMatch.Success -and $passwordMatch.Groups[1].Value -ne "CHANGE-ME-TO-A-LONG-RANDOM-PASSWORD") {
        $previousPassword = $passwordMatch.Groups[1].Value
    }
}

if (-not (Test-Path -LiteralPath $youtubePlugin)) {
    $buildPlugin = Join-Path $PSScriptRoot "build-youtube-plugin-patched.ps1"
    & $buildPlugin
    if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar o plugin YouTube com o workaround TVHTML5." }
}
if (-not (Test-Path -LiteralPath $youtubePlugin)) {
    throw "JAR customizado do youtube-source não encontrado: $youtubePlugin"
}

if (Test-Path $stage) {
    Remove-Item -LiteralPath $stage -Recurse -Force
}
New-Item -ItemType Directory -Path $stage -Force | Out-Null

$javac = Get-Command javac -ErrorAction SilentlyContinue
$jarTool = Get-Command jar -ErrorAction SilentlyContinue
if (-not $javac -or -not $jarTool) {
    throw "JDK 17 ou superior é necessário para compilar o inicializador Java."
}

$classes = Join-Path $stage "classes"
New-Item -ItemType Directory -Path $classes -Force | Out-Null
$source = Join-Path $root "src\main\java\app\kinglavalink\Launcher.java"
& $javac.Source --release 17 -encoding UTF-8 -d $classes $source
if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar o inicializador Java." }
& $jarTool.Source --create --file (Join-Path $stage "king-lavalink-launcher.jar") `
    --main-class app.kinglavalink.Launcher -C $classes .
if ($LASTEXITCODE -ne 0) { throw "Falha ao empacotar o inicializador Java." }
Remove-Item -LiteralPath $classes -Recurse -Force

$config = Get-Content -LiteralPath (Join-Path $root "application.shardcloud.yml") -Raw
if ($previousPassword) {
    $password = $previousPassword
} else {
    $secretBytes = New-Object byte[] 48
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($secretBytes)
    $rng.Dispose()
    $password = [Convert]::ToBase64String($secretBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}
$config = $config.Replace("CHANGE-ME-TO-A-LONG-RANDOM-PASSWORD", $password)
Set-Content -LiteralPath (Join-Path $stage "application.yml") -Value $config -Encoding utf8

Copy-Item -LiteralPath (Join-Path $root ".shardcloud") -Destination (Join-Path $stage ".shardcloud")
New-Item -ItemType Directory -Path (Join-Path $stage "scripts") -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $root "scripts\panel.html") -Destination (Join-Path $stage "scripts")
$pluginStage = Join-Path $stage "plugins"
New-Item -ItemType Directory -Path $pluginStage -Force | Out-Null
Copy-Item -LiteralPath $youtubePlugin -Destination (Join-Path $pluginStage "youtube-plugin-1.18.2-king-tv-ua.jar")

if (Test-Path $archive) {
    Remove-Item -LiteralPath $archive -Force
}
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $archive -Force
Write-Host "ShardCloud package ready: $archive"
Write-Host "The generated Lavalink password is stored in application.yml inside the package."
Write-Host "The Java launcher downloads Lavalink.jar on first startup; the Lavalink JAR is not included in the upload."
Write-Host "The package includes the patched youtube-source JAR for the TVHTML5 User-Agent workaround."
