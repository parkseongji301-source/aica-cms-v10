param([int]$Port = 8082, [string]$Database = '.cache/react-phase3b2a-data/aica-phase3b2b.mv.db')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskAllowed = [IO.Path]::GetFullPath((Join-Path $taskRoot '.cache/react-phase3b2a-data')) + [IO.Path]::DirectorySeparatorChar
$taskDatabase = (Resolve-Path -LiteralPath (Join-Path $taskRoot $Database)).Path
if (-not $taskDatabase.StartsWith($taskAllowed, [StringComparison]::OrdinalIgnoreCase) -or -not $taskDatabase.EndsWith('.mv.db')) {
    throw 'Use an existing cold copy inside .cache/react-phase3b2a-data. Original DB paths are not allowed.'
}
$taskJar = Join-Path $taskRoot 'target/backoffice-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { throw 'Run .\scripts\mvn-local.ps1 -B -ntp -Pegov43-probe clean verify first.' }
$taskJavaRoot = $env:JAVA_HOME
if (-not $taskJavaRoot) {
    $taskJavaRoot = (Get-ChildItem -LiteralPath (Join-Path $taskRoot '.tools/jdk') -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1).FullName
}
if (-not $taskJavaRoot) { throw 'JDK 17 is required.' }
$taskBase = $taskDatabase.Substring(0,$taskDatabase.Length-6).Replace('\','/')
Write-Host "Classification verification COPY: $taskDatabase"
Write-Host "React: http://127.0.0.1:$Port/admin-next/posts?view=manage"
Push-Location $taskRoot
try {
    & (Join-Path $taskJavaRoot 'bin/java.exe') '-Dfile.encoding=UTF-8' -jar $taskJar '--spring.profiles.active=dev' `
        '--server.address=127.0.0.1' "--server.port=$Port" "--spring.datasource.url=jdbc:h2:file:$taskBase;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0" `
        '--backoffice.classification-migration.copy-validation=true' '--backoffice.bootstrap.enabled=false' '--backoffice.preview=true'
} finally { Pop-Location }
