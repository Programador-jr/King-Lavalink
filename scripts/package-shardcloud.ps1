$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$version = "4.2.2"
$url = "https://github.com/lavalink-devs/Lavalink/releases/download/$version/Lavalink.jar"
$stage = Join-Path $root "dist\shardcloud-java"
$archive = Join-Path $root "dist\king-lavalink-shardcloud.zip"

if (Test-Path $stage) {
    Remove-Item -LiteralPath $stage -Recurse -Force
}
New-Item -ItemType Directory -Path $stage -Force | Out-Null

$jar = Join-Path $stage "Lavalink.jar"
$localJar = Join-Path $root "Lavalink.jar"
if (Test-Path $localJar) {
    Copy-Item -LiteralPath $localJar -Destination $jar
} else {
    Write-Host "Downloading Lavalink $version..."
    Invoke-WebRequest -Uri $url -OutFile $jar
}
if (-not (Test-Path $jar) -or (Get-Item $jar).Length -lt 1MB) {
    throw "Lavalink download failed or the file appears invalid."
}

Copy-Item -LiteralPath (Join-Path $root "application.shardcloud.yml") -Destination (Join-Path $stage "application.yml")
Copy-Item -LiteralPath (Join-Path $root ".shardcloud") -Destination (Join-Path $stage ".shardcloud")

if (Test-Path $archive) {
    Remove-Item -LiteralPath $archive -Force
}
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $archive -Force
Write-Host "ShardCloud package ready: $archive"
