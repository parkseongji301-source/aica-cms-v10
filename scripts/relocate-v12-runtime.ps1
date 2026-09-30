# Rebinds a normally stopped V12 runtime copy to its new folder (another PC or path).
# Like the V11 transfer tool: the DB must be byte-identical to the last normal-stop inspection,
# a fresh V12PromotionTool inspection at the new path must match it, and only then is the
# receipt re-issued for the new path. Never migrates or edits the DB; the old receipt is kept.
param(
    [Parameter(Mandatory=$true)][string]$Runtime,
    [string]$Java,
    [switch]$AuthorizeRelocation
)
$ErrorActionPreference='Stop'
if(-not $AuthorizeRelocation){throw 'Explicit -AuthorizeRelocation is required.'}
function Save-TaskJson($value,$path){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 50),[Text.UTF8Encoding]::new($false))}
function Get-TaskJson($path){Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json}
function Get-TaskSame($a,$b){($a|ConvertTo-Json -Depth 50 -Compress) -ceq ($b|ConvertTo-Json -Depth 50 -Compress)}
# Same string as Java Path.toRealPath() for plain folders: true on-disk casing, no junctions.
function Get-TaskRealPath($path){
    $full=[IO.Path]::GetFullPath($path);$root=[IO.Path]::GetPathRoot($full).ToUpperInvariant();$real=$root
    foreach($part in $full.Substring($root.Length).Split([IO.Path]::DirectorySeparatorChar)){
        if(-not $part){continue}
        $entry=([IO.DirectoryInfo]$real).GetFileSystemInfos($part)
        if($entry.Count -ne 1){throw ('Path not found: '+$full)}
        if($entry[0].Attributes -band [IO.FileAttributes]::ReparsePoint){throw ('Junctions/symlinks are not supported; use a plain folder: '+$entry[0].FullName)}
        $real=Join-Path $real $entry[0].Name
    }
    $real
}
$taskRuntime=(Resolve-Path -LiteralPath $Runtime).Path
$taskConfigPath=Join-Path $taskRuntime 'runtime.json'
$taskConfig=Get-TaskJson $taskConfigPath
$taskJar=(Resolve-Path -LiteralPath (Join-Path $taskRuntime 'server.jar')).Path
$taskAgent=(Resolve-Path -LiteralPath (Join-Path $taskRuntime 'graceful-stop.jar')).Path
$taskReceiptPath=(Resolve-Path -LiteralPath (Join-Path $taskRuntime 'migration-receipt.json')).Path
$taskDb=Get-TaskRealPath (Join-Path $taskRuntime 'db/aica-local.mv.db')
if((Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash -ne $taskConfig.jarSha256){throw 'JAR checksum mismatch.'}
if((Get-FileHash -LiteralPath $taskAgent -Algorithm SHA256).Hash -ne $taskConfig.agentSha256){throw 'Shutdown tool checksum mismatch.'}
if($Java){
    $taskJava=(Resolve-Path -LiteralPath $Java).Path
    $taskRelease=Join-Path (Split-Path -Parent (Split-Path -Parent $taskJava)) 'release'
    if(-not((Test-Path -LiteralPath $taskRelease) -and (Get-Content -LiteralPath $taskRelease -Raw) -match 'JAVA_VERSION="17\.')){throw 'JDK 17 required.'}
}else{
    $taskJava=$taskConfig.java
    if(-not(Test-Path -LiteralPath $taskJava)){throw ('Java not found at '+$taskJava+'; pass -Java <path to JDK 17 java.exe>.')}
}
$taskActivePath=Join-Path $taskRuntime 'active.json'
# A START refused by the receipt guard leaves active.json RUNNING without opening the DB;
# that is accepted only when the process is gone. The DB hash check below proves no writes.
if((Test-Path -LiteralPath $taskActivePath) -and ($taskActive=Get-TaskJson $taskActivePath).status -ne 'STOPPED'){
    $taskProcess=Get-CimInstance Win32_Process -Filter ('ProcessId = '+[int]$taskActive.pid) -ErrorAction SilentlyContinue
    if($taskProcess -and $taskProcess.CommandLine -and $taskProcess.CommandLine.Contains($taskJar)){throw 'This runtime is running. Stop it with STOP.cmd first.'}
    Write-Host 'active.json records a start that is no longer running (e.g. refused before relocation); continuing with DB hash verification.'
}
$taskHandle=[IO.File]::Open($taskDb,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$taskHandle.Dispose()
$taskReceipt=Get-TaskJson $taskReceiptPath
if($taskReceipt.status -ne 'MIGRATED_V12' -or $taskReceipt.jarSha256 -ne $taskConfig.jarSha256 -or $taskReceipt.workaround -ne 'AUTO_COMPACT_FILL_RATE=0'){throw 'Receipt is not a completed V12 receipt for this JAR.'}
$taskStamp=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
if($taskReceipt.databasePath -ceq $taskDb){
    Write-Host 'Receipt already matches this folder; database binding unchanged.'
}else{
    # Source evidence: the cold inspection written by the last normal STOP on the original PC.
    $taskSourceFile=Get-ChildItem -LiteralPath $taskRuntime -Filter 'stopped-*.json' | Sort-Object {[long]($_.BaseName.Substring(8))} | Select-Object -Last 1
    if(-not $taskSourceFile){throw 'No normal-stop inspection (stopped-*.json) found; stop the runtime with STOP.cmd before copying.'}
    $taskSource=Get-TaskJson $taskSourceFile.FullName
    $taskDbHash=(Get-FileHash -LiteralPath $taskDb -Algorithm SHA256).Hash.ToLowerInvariant()
    if($taskDbHash -ne $taskSource.sha256.ToLowerInvariant()){throw 'Database changed after the last normal stop (or copy is incomplete). Copy again after STOP.cmd.'}
    if(-not((Get-TaskSame $taskSource.history $taskReceipt.after.history) -and (Get-TaskSame $taskSource.columns $taskReceipt.after.columns))){throw 'Migration history/schema differs from the V12 receipt.'}
    $taskInspectPath=Join-Path $taskRuntime ('relocation-inspect-'+$taskStamp+'.json')
    & $taskJava '-Dloader.main=egovframework.backoffice.mvp.operations.V12PromotionTool' -cp $taskJar org.springframework.boot.loader.launch.PropertiesLauncher inspect $taskDb $taskInspectPath
    if($LASTEXITCODE -ne 0){throw 'Database inspection at the new path failed.'}
    $taskInspect=Get-TaskJson $taskInspectPath
    foreach($taskKey in 'history','columns','fingerprints'){if(-not(Get-TaskSame $taskInspect.$taskKey $taskSource.$taskKey)){throw ('Relocated database differs from the normal-stop inspection: '+$taskKey)}}
    if($taskInspect.sha256.ToLowerInvariant() -ne $taskDbHash -or (Get-FileHash -LiteralPath $taskDb -Algorithm SHA256).Hash.ToLowerInvariant() -ne $taskDbHash){throw 'Database bytes changed during inspection.'}
    $taskBackup=Join-Path $taskRuntime ('migration-receipt.before-relocation-'+$taskStamp+'.json')
    Copy-Item -LiteralPath $taskReceiptPath -Destination $taskBackup
    $taskRelocation=[ordered]@{relocatedAt=[DateTimeOffset]::UtcNow.ToString('o');from=$taskReceipt.databasePath;to=$taskDb;databaseSha256=$taskDbHash
        sourceInspection=$taskSourceFile.Name;sourceInspectionSha256=(Get-FileHash -LiteralPath $taskSourceFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        inspection=(Split-Path -Leaf $taskInspectPath);inspectionSha256=(Get-FileHash -LiteralPath $taskInspectPath -Algorithm SHA256).Hash.ToLowerInvariant()
        previousReceipt=(Split-Path -Leaf $taskBackup)}
    $taskReceipt.databasePath=$taskDb
    $taskReceipt|Add-Member -Force -NotePropertyName relocations -NotePropertyValue (@($taskReceipt.relocations|Where-Object{$_})+@($taskRelocation))
    Save-TaskJson $taskReceipt $taskReceiptPath
    Write-Host ('Receipt re-issued for '+$taskDb+' (previous kept as '+(Split-Path -Leaf $taskBackup)+').')
}
if($taskConfig.java -ne $taskJava){
    Copy-Item -LiteralPath $taskConfigPath -Destination (Join-Path $taskRuntime ('runtime.before-relocation-'+$taskStamp+'.json'))
    $taskConfig.java=$taskJava;Save-TaskJson $taskConfig $taskConfigPath
    Write-Host ('runtime.json java = '+$taskJava)
}
Write-Host 'V12 relocation verified. Start with START.cmd.'
