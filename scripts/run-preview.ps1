# Disposable design preview with labelled sample traffic and fictional content.
# Demo login: 1234 / 1234
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskJavaRoot = $env:JAVA_HOME
if (-not $taskJavaRoot) {
    $taskJdk = Get-ChildItem -LiteralPath (Join-Path $taskRoot '.tools/jdk') -Directory |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } |
        Select-Object -First 1
    if ($taskJdk) { $taskJavaRoot = $taskJdk.FullName }
}
if (-not $taskJavaRoot) { throw 'Install JDK 17 and set JAVA_HOME.' }
$taskJar = Join-Path $taskRoot 'target/backoffice-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $taskJar)) { throw 'Build first: .\scripts\mvn-local.ps1 -B -ntp -Pegov43-probe verify' }
Write-Host 'Preview: http://127.0.0.1:8081'
Write-Host 'Demo login: 1234 / 1234'
Write-Host 'Sample data is held in memory and resets on restart.'
Push-Location $taskRoot
try {
    & (Join-Path $taskJavaRoot 'bin/java.exe') -jar $taskJar '--spring.profiles.active=design-preview'
} finally { Pop-Location }
