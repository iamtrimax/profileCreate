param(
    [Parameter(Mandatory=$true)][string]$Backup,
    [Parameter(Mandatory=$true)][ValidatePattern('^linkhub_restore_[a-z0-9_]{1,40}$')][string]$Database
)
$ErrorActionPreference = 'Stop'
$compose = Join-Path $PSScriptRoot '..\compose.yaml'
$file = (Resolve-Path -LiteralPath $Backup).Path
if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw 'Backup must be a file.' }
$container = (docker compose -f $compose ps -q postgres)
if ($LASTEXITCODE -ne 0 -or -not $container) { throw 'PostgreSQL container is not running.' }
$remote = '/tmp/restore-' + [guid]::NewGuid().ToString('N') + '.dump'
try {
    docker cp $file "${container}:$remote"
    if ($LASTEXITCODE -ne 0) { throw 'Copying backup failed.' }
    docker compose -f $compose exec -T postgres pg_restore --list $remote | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Backup archive is not valid.' }
    # createdb deliberately fails if the target already exists. Never use --clean.
    docker compose -f $compose exec -T postgres createdb -U linkhub $Database
    if ($LASTEXITCODE -ne 0) { throw 'Cannot create target database; existing databases are never overwritten.' }
    docker compose -f $compose exec -T postgres pg_restore -U linkhub --dbname=$Database --no-owner --no-acl --exit-on-error --single-transaction $remote
    if ($LASTEXITCODE -ne 0) { throw 'Restore failed; target database is retained for inspection.' }
    docker compose -f $compose exec -T postgres psql -U linkhub -d $Database -c 'select count(*) as accounts from accounts; select count(*) as bookings from bookings;'
    if ($LASTEXITCODE -ne 0) { throw 'Restore verification failed.' }
    Write-Output "Restored into $Database. Inspect it before changing the application DB_URL."
} finally {
    docker compose -f $compose exec -T postgres rm -f -- $remote
}
