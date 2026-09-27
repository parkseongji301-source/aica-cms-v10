# Build both UIs into one Spring artifact. Does not start the server or touch the local DB.
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $taskRoot 'frontend')
try {
    & pnpm.cmd install --frozen-lockfile --store-dir ../.cache/pnpm-store --config.manage-package-manager-versions=false
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    & pnpm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }
Push-Location $taskRoot
try {
    & (Join-Path $PSScriptRoot 'mvn-local.ps1') -B -ntp -Pegov43-probe clean verify
    if ($LASTEXITCODE -ne 0) { throw 'Spring verification failed.' }
} finally { Pop-Location }
