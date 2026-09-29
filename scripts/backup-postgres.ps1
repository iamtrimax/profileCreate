param([string]$Directory = (Join-Path $PSScriptRoot '..\backups'))
$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot '..\compose.yaml'
$container = (docker compose -f $compose ps -q postgres)
if ($LASTEXITCODE -ne 0 -or -not $container) { throw 'PostgreSQL container is not running.' }
$destination = [IO.Path]::GetFullPath($Directory)
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$name = 'linkhub-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N') + '.dump'
$remote = '/tmp/' + $name
$file = Join-Path $destination $name
try {
    docker compose -f $compose exec -T postgres pg_dump -U linkhub -d linkhub --format=custom --file=$remote
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed.' }
    docker cp "${container}:$remote" $file
    if ($LASTEXITCODE -ne 0) { throw 'Copying backup failed.' }
    Get-FileHash -LiteralPath $file -Algorithm SHA256
    Write-Output "Backup: $file"
} finally {
    docker compose -f $compose exec -T postgres rm -f -- $remote
}
