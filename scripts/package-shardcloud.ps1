$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$stage = Join-Path $root "dist\shardcloud"
$archive = Join-Path $root "dist\king-lavalink-shardcloud.zip"

if (Test-Path $stage) {
    Remove-Item -LiteralPath $stage -Recurse -Force
}
New-Item -ItemType Directory -Path $stage -Force | Out-Null

$config = Get-Content -LiteralPath (Join-Path $root "application.shardcloud.yml") -Raw
$secretBytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($secretBytes)
$rng.Dispose()
$password = [Convert]::ToBase64String($secretBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$config = $config.Replace("CHANGE-ME-TO-A-LONG-RANDOM-PASSWORD", $password)
Set-Content -LiteralPath (Join-Path $stage "application.yml") -Value $config -Encoding utf8

Copy-Item -LiteralPath (Join-Path $root ".shardcloud") -Destination (Join-Path $stage ".shardcloud")
Copy-Item -LiteralPath (Join-Path $root "Dockerfile") -Destination $stage
New-Item -ItemType Directory -Path (Join-Path $stage "scripts") -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $root "scripts\start-shardcloud.py") -Destination (Join-Path $stage "scripts")
Copy-Item -LiteralPath (Join-Path $root "scripts\panel.py") -Destination (Join-Path $stage "scripts")
Copy-Item -LiteralPath (Join-Path $root "scripts\panel.html") -Destination (Join-Path $stage "scripts")

if (Test-Path $archive) {
    Remove-Item -LiteralPath $archive -Force
}
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $archive -Force
Write-Host "ShardCloud package ready: $archive"
Write-Host "The generated Lavalink password is stored in application.yml inside the package."
Write-Host "Lavalink.jar is downloaded during the Docker image build; it is not included in the upload."
