# Persistent local backoffice. Initial login: 1234 / 1234.
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJavaRoot = $env:JAVA_HOME
if (-not $taskJavaRoot) {
    $taskJdk = Get-ChildItem -LiteralPath (Join-Path $taskRoot '.tools/jdk') -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1
    if ($taskJdk) { $taskJavaRoot = $taskJdk.FullName }
}
if (-not $taskJavaRoot) { throw 'Install JDK 17 and set JAVA_HOME.' }
# During copy-only 3B-2A validation the original database stays on the preserved 3A binary.
$taskCheckpointPointer = Join-Path $taskRoot '.cache/react-phase3b2a-checkpoint.txt'
if (-not (Test-Path -LiteralPath $taskCheckpointPointer)) { throw '3B-2A validates a copy only. Use scripts/run-classification-copy.ps1; the original requires its preserved 3A runtime.' }
$taskCheckpoint = (Get-Content -LiteralPath $taskCheckpointPointer -Raw).Trim()
$taskCheckpointRoot = [IO.Path]::GetFullPath((Join-Path $taskRoot '.cache/checkpoints')) + [IO.Path]::DirectorySeparatorChar
if (-not [IO.Path]::GetFullPath($taskCheckpoint).StartsWith($taskCheckpointRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Checkpoint must be inside .cache/checkpoints.' }
$taskJar = Join-Path $taskCheckpoint 'phase3a-runtime.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { throw 'Preserved 3A runtime is missing. Do not start the new build against the original database.' }
Write-Host 'Original V3 database: using the preserved 3A runtime. New backend validation: scripts/run-classification-copy.ps1.'
Write-Host 'Backoffice: http://127.0.0.1:8081/admin'
Write-Host 'React admin: http://127.0.0.1:8081/admin-next'
Write-Host 'Data persists in .local-data/aica-local.mv.db.'
Push-Location $taskRoot
try { & (Join-Path $taskJavaRoot 'bin/java.exe') -jar $taskJar '--spring.profiles.active=local' }
finally { Pop-Location }

