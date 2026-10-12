[CmdletBinding()]
param([switch]$KeepRunning, [switch]$Offline)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectDirectory
try {
    $seedArguments = '--server.port=0 --linkhub.seed.exit=true'
    if ($KeepRunning) { $seedArguments = '--linkhub.seed.exit=false' }
    $mavenArguments = @('spring-boot:run', '-Dspring-boot.run.profiles=seed-recruitment', "-Dspring-boot.run.arguments=$seedArguments")
    if ($Offline) { $mavenArguments = @('-o') + $mavenArguments }
    & '.\mvnw.cmd' @mavenArguments
    if ($LASTEXITCODE -ne 0) { throw 'Seed không hoàn tất. Xem lỗi Maven/Spring Boot phía trên.' }
    Write-Host 'Seed tuyển dụng đã hoàn tất. Mật khẩu demo: LinkHubDemo!2026'
    Write-Host 'Nhà tuyển dụng: demo_recruiter@linkhub.test'
    Write-Host 'Ứng viên chờ lời mời: demo_bao@linkhub.test'
    Write-Host 'Ứng viên đã match: demo_an@linkhub.test'
    Write-Host 'Danh sách và kịch bản: docs/recruitment-seed.md'
} finally {
    Pop-Location
}
