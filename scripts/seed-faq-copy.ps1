param([string]$Database = '.cache/react-phase3b2a-data/aica-phase3c2.mv.db')
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskAllowed = (Resolve-Path -LiteralPath (Join-Path $taskRoot '.cache/react-phase3b2a-data')).Path + [IO.Path]::DirectorySeparatorChar
$taskDb = (Resolve-Path -LiteralPath (Join-Path $taskRoot $Database)).Path
if (-not $taskDb.StartsWith($taskAllowed,[StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetFileName($taskDb) -notlike 'aica-phase3c2*.mv.db') { throw 'Only a stopped 3C-2 copy is allowed. Original and 3B fixture DBs are excluded.' }
$taskJava = Join-Path (Get-ChildItem -LiteralPath (Join-Path $taskRoot '.tools/jdk') -Directory | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/java.exe') } | Select-Object -First 1).FullName 'bin/java.exe'
$taskH2 = Join-Path $taskRoot '.cache/repository/com/h2database/h2/2.3.232/h2-2.3.232.jar'
$taskBase = $taskDb.Substring(0,$taskDb.Length-6).Replace('\','/')
& $taskJava '-Dfile.encoding=UTF-8' -cp $taskH2 org.h2.tools.RunScript -url "jdbc:h2:file:$taskBase;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0" -user sa -password '' -script (Join-Path $taskRoot 'workbench/faq-candidate/vocabulary.sql')
if ($LASTEXITCODE -ne 0) { throw 'Candidate vocabulary failed. Do not start the copy or change the original DB.' }
Write-Host "FAQ candidate vocabulary registered in COPY only: $taskDb"
