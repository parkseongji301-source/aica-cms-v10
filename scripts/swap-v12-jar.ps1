# Issues a V12 receipt for a replacement JAR (for example RC2) that uses the database of a
# normally stopped V12 runtime (for example RC1). Nothing is migrated or written to the DB and
# the source receipt is kept, so the source runtime stays valid for rollback. Required:
# - neither runtime is running and no Java process names the database (never run both);
# - the DB is byte-identical to the latest normal-stop inspection of any runtime using it;
# - both JARs carry identical db/migration entries (same V1..V12 files and checksums);
# - the replacement JAR's own read-only inspection (Flyway validate, V12 current, no pending)
#   matches that stop inspection in history, columns and row fingerprints.
param(
    [Parameter(Mandatory=$true)][string]$Source,
    [Parameter(Mandatory=$true)][string]$Target,
    [switch]$AuthorizeJarSwap
)
$ErrorActionPreference='Stop'
if(-not $AuthorizeJarSwap){throw 'Explicit -AuthorizeJarSwap is required.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Save-TaskJson($value,$path){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 50),[Text.UTF8Encoding]::new($false))}
function Get-TaskJson($path){Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json}
# Inspections print row fingerprints from Java Map.of, whose key order changes between JVM runs;
# compare values with object keys sorted (array order is kept: migration history order matters).
function ConvertTo-TaskCanonical($value){
    if($null -eq $value){return $null}
    if($value -is [Management.Automation.PSCustomObject]){$out=[ordered]@{};foreach($name in @($value.PSObject.Properties.Name|Sort-Object -CaseSensitive)){$out[$name]=ConvertTo-TaskCanonical $value.$name};return $out}
    if($value -is [Collections.IDictionary]){$out=[ordered]@{};foreach($name in @($value.Keys|Sort-Object -CaseSensitive)){$out[$name]=ConvertTo-TaskCanonical $value[$name]};return $out}
    if(($value -is [Collections.IEnumerable]) -and -not ($value -is [string])){return ,@(foreach($item in $value){ConvertTo-TaskCanonical $item})}
    $value
}
function Get-TaskSame($a,$b){(ConvertTo-Json -InputObject (ConvertTo-TaskCanonical $a) -Depth 50 -Compress) -ceq (ConvertTo-Json -InputObject (ConvertTo-TaskCanonical $b) -Depth 50 -Compress)}
function Get-TaskHash($path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
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
function Get-TaskDatabase($runtime,$config){
    $setting='db/aica-local.mv.db'
    if(($config.PSObject.Properties.Name -contains 'database') -and $config.database){$setting=$config.database}
    if(-not [IO.Path]::IsPathRooted($setting)){$setting=Join-Path $runtime $setting}
    Get-TaskRealPath $setting
}
function Get-TaskEntries($jar,$prefix){
    $zip=[IO.Compression.ZipFile]::OpenRead($jar);$out=[ordered]@{}
    try{
        foreach($entry in ($zip.Entries|Where-Object{$_.FullName.StartsWith($prefix) -and -not $_.FullName.EndsWith('/')}|Sort-Object FullName)){
            $stream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
            try{$out[$entry.FullName.Substring($prefix.Length)]=([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','').ToLowerInvariant()}finally{$stream.Dispose();$sha.Dispose()}
        }
    }finally{$zip.Dispose()}
    $out
}
function Get-TaskEntryBytes($jar,$name){
    $zip=[IO.Compression.ZipFile]::OpenRead($jar)
    try{$entry=$zip.GetEntry($name);if(-not $entry){throw ('JAR entry missing: '+$name)};$stream=$entry.Open();$memory=New-Object IO.MemoryStream;try{$stream.CopyTo($memory);,$memory.ToArray()}finally{$stream.Dispose();$memory.Dispose()}}finally{$zip.Dispose()}
}
function Assert-TaskStopped($runtime,$jar,$label){
    $activePath=Join-Path $runtime 'active.json'
    if((Test-Path -LiteralPath $activePath) -and ($active=Get-TaskJson $activePath).status -ne 'STOPPED'){
        $process=Get-CimInstance Win32_Process -Filter ('ProcessId = '+[int]$active.pid) -ErrorAction SilentlyContinue
        if($process -and $process.CommandLine -and $process.CommandLine.Contains($jar)){throw ($label+' runtime is running. Stop it with STOP.cmd (or -Stop) first.')}
        throw ($label+' runtime did not record a normal stop (active.json '+$active.status+'). Start and stop it normally first.')
    }
}

$sourceRuntime=(Resolve-Path -LiteralPath $Source).Path;$targetRuntime=(Resolve-Path -LiteralPath $Target).Path
if($sourceRuntime -eq $targetRuntime){throw 'Source and target must be different runtime folders.'}
$sourceConfig=Get-TaskJson (Join-Path $sourceRuntime 'runtime.json');$targetConfigPath=Join-Path $targetRuntime 'runtime.json';$targetConfig=Get-TaskJson $targetConfigPath
$sourceJar=(Resolve-Path -LiteralPath (Join-Path $sourceRuntime 'server.jar')).Path;$targetJar=(Resolve-Path -LiteralPath (Join-Path $targetRuntime 'server.jar')).Path
$targetAgent=(Resolve-Path -LiteralPath (Join-Path $targetRuntime 'graceful-stop.jar')).Path
$sourceReceiptPath=(Resolve-Path -LiteralPath (Join-Path $sourceRuntime 'migration-receipt.json')).Path
$targetReceiptPath=Join-Path $targetRuntime 'migration-receipt.json'

# 1. Both runtimes are what their configuration says they are.
if((Get-TaskHash $sourceJar) -ne $sourceConfig.jarSha256){throw 'Source JAR checksum mismatch.'}
if((Get-TaskHash $targetJar) -ne $targetConfig.jarSha256){throw 'Target JAR checksum mismatch.'}
if((Get-TaskHash $targetAgent) -ne $targetConfig.agentSha256){throw 'Target shutdown tool checksum mismatch.'}
if($targetConfig.jarSha256 -eq $sourceConfig.jarSha256){throw 'Target JAR is the source JAR; nothing to swap.'}
foreach($asset in $targetConfig.assets.PSObject.Properties){if((Get-TaskHash (Join-Path (Join-Path $targetRuntime 'assets') $asset.Name)) -ne $asset.Value){throw ('Target asset checksum mismatch: '+$asset.Name)}}
# The login style overlay must be the target JAR's own flow.css, a line feed and its login-shell.css with
# LF line endings, the recipe of the V12 RC1 overlay (the JAR copy of login-shell.css may carry CRLF).
$overlay=Join-Path $targetRuntime 'assets/css/flow.css'
if(Test-Path -LiteralPath $overlay){
    $skin=[byte[]]@((Get-TaskEntryBytes $targetJar 'BOOT-INF/classes/static/next-app/login-shell.css')|Where-Object{$_ -ne 13})
    $expected=(Get-TaskEntryBytes $targetJar 'BOOT-INF/classes/static/css/flow.css')+[byte[]](10)+$skin
    $actual=[IO.File]::ReadAllBytes($overlay)
    if(-not [Linq.Enumerable]::SequenceEqual([byte[]]$expected,[byte[]]$actual)){throw 'Target login style overlay is not the target JAR flow.css plus login-shell.css.'}
}
$java=$targetConfig.java;if(-not(Test-Path -LiteralPath $java)){throw ('Java not found at '+$java)}

# 2. The source receipt is a completed V12 receipt for the source JAR; the target uses the same database file.
$sourceReceipt=Get-TaskJson $sourceReceiptPath
if($sourceReceipt.status -notin @('MIGRATED_V12','MIGRATED_V13','MIGRATED_V14') -or $sourceReceipt.jarSha256 -ne $sourceConfig.jarSha256 -or $sourceReceipt.workaround -ne 'AUTO_COMPACT_FILL_RATE=0'){throw 'Source receipt is not a completed V12/V13 receipt for the source JAR.'}
$db=Get-TaskDatabase $sourceRuntime $sourceConfig
if($sourceReceipt.databasePath -cne $db){throw 'Source receipt is bound to another database path.'}
if((Get-TaskDatabase $targetRuntime $targetConfig) -cne $db){throw 'Target runtime.json database must be the source database file.'}

# 3. Never run both: both recorded a normal stop, nothing names the database, and it opens exclusively.
Assert-TaskStopped $sourceRuntime $sourceJar 'Source'
Assert-TaskStopped $targetRuntime $targetJar 'Target'
$stem=$db.Substring(0,$db.Length-6).Replace('\','/')
$users=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and ($_.CommandLine.Replace('\','/').IndexOf($stem,[StringComparison]::OrdinalIgnoreCase) -ge 0 -or $_.CommandLine.Contains($sourceJar) -or $_.CommandLine.Contains($targetJar)) })
if($users.Count -gt 0){throw ('A Java process still uses this database or JAR (pid '+(($users|ForEach-Object{$_.ProcessId}) -join ',')+').')}
foreach($port in @($sourceConfig.port,$targetConfig.port)){if(Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue){throw ('Port '+$port+' is in use.')}}
$handle=[IO.File]::Open($db,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$handle.Dispose()

if(Test-Path -LiteralPath $targetReceiptPath){
    $existing=Get-TaskJson $targetReceiptPath
    if($existing.status -eq $sourceReceipt.status -and $existing.databasePath -ceq $db -and $existing.jarSha256 -eq $targetConfig.jarSha256){Write-Host 'Target receipt already binds this JAR to this database; unchanged.';exit 0}
    throw 'Target already has a different receipt; refusing to overwrite it.'
}

# 4. The database has not changed since the latest cold inspection of it: a normal stop (stopped-*.json)
#    or a relocation/swap inspection, which both require the database to be closed.
$stops=@(foreach($runtime in @($sourceRuntime,$targetRuntime)){Get-ChildItem -LiteralPath $runtime -File|Where-Object{$_.Name -match '^(stopped|relocation-inspect|swap-inspect)-\d+\.json$' -and (Get-TaskJson $_.FullName).path -ceq $db}})
if($stops.Count -eq 0){throw 'No cold inspection for this database; stop the source runtime normally (STOP.cmd) first.'}
$stopFile=$stops|Sort-Object {[long]($_.BaseName.Substring($_.BaseName.LastIndexOf('-')+1))}|Select-Object -Last 1;$stop=Get-TaskJson $stopFile.FullName
$dbHash=Get-TaskHash $db
if($dbHash -ne $stop.sha256.ToLowerInvariant()){throw ('Database changed after the last normal stop ('+$stopFile.Name+').')}
if(-not((Get-TaskSame $stop.history $sourceReceipt.after.history) -and (Get-TaskSame $stop.columns $sourceReceipt.after.columns))){throw 'Migration history/schema differs from the source V12 receipt.'}

# 5. Identical migrations: same db/migration entries, byte for byte, in both JARs.
$prefix='BOOT-INF/classes/db/migration/'
$sourceMigrations=Get-TaskEntries $sourceJar $prefix;$targetMigrations=Get-TaskEntries $targetJar $prefix
if($sourceMigrations.Count -eq 0){throw 'Source JAR carries no migrations.'}
if(-not(Get-TaskSame $sourceMigrations $targetMigrations)){throw 'Target JAR migrations differ from the source JAR (new, removed or changed migration).'}

# 6. The target JAR's own read-only inspection: Flyway validate with its migrations, V12 current, no pending, same data.
$stamp=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$inspectPath=Join-Path $targetRuntime ('swap-inspect-'+$stamp+'.json')
& $java '-Dloader.main=egovframework.backoffice.mvp.operations.V12PromotionTool' -cp $targetJar org.springframework.boot.loader.launch.PropertiesLauncher inspect $db $inspectPath
if($LASTEXITCODE -ne 0){throw 'Target JAR inspection failed (Flyway validate or schema version).'}
$inspect=Get-TaskJson $inspectPath
foreach($key in 'history','columns','fingerprints'){if(-not(Get-TaskSame $inspect.$key $stop.$key)){throw ('Target inspection differs from the normal-stop inspection: '+$key)}}
if($inspect.sha256.ToLowerInvariant() -ne $dbHash -or (Get-TaskHash $db) -ne $dbHash){throw 'Database bytes changed during inspection.'}

# 7. Issue the target receipt; the source receipt stays untouched and valid for rollback.
$receipt=[ordered]@{status=$sourceReceipt.status;databasePath=$db;jarSha256=$targetConfig.jarSha256;workaround='AUTO_COMPACT_FILL_RATE=0'
    issuedBy='scripts/swap-v12-jar.ps1';issuedAt=[DateTimeOffset]::UtcNow.ToString('o')
    replaces=[ordered]@{runtime=$sourceRuntime;jarSha256=$sourceConfig.jarSha256;receipt=$sourceReceiptPath;receiptSha256=(Get-TaskHash $sourceReceiptPath);migrationPlanSha256=$sourceReceipt.planSha256}
    stopInspection=$stopFile.FullName;stopInspectionSha256=(Get-TaskHash $stopFile.FullName)
    inspection=(Split-Path -Leaf $inspectPath);inspectionSha256=(Get-TaskHash $inspectPath)
    migrations=$targetMigrations;after=$inspect}
Save-TaskJson $receipt $targetReceiptPath
Write-Host ('Receipt issued for '+$targetConfig.kind+' on '+$db+'. Source receipt unchanged. Select the runtime with scripts/select-v12-runtime.ps1.')
