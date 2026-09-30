# Creates a V15 runtime from a normally stopped V13 or V14 runtime: copies the cold source database into the
# new runtime folder and migrates only that copy, one version at a time: V13 -> V14 with V14PromotionTool
# (V13 source only), then V14 -> V15 with V15PromotionTool. Each step plans against a byte-identical cold
# original (the source database, then a copy of the V14 result kept in promotion-stage/) and must pass that
# tool's own checks. The source runtime, its database and its receipt are never written (rollback target).
# Required: the source runtime is stopped and no Java process names its database; the database equals its
# latest cold inspection; the V15 JAR carries the source JAR's migrations byte for byte plus only the
# missing versions (V14 and V15 for a V13 source, V15 for a V14 source).
param(
    [Parameter(Mandatory=$true)][string]$Source,
    [Parameter(Mandatory=$true)][string]$Target,
    [switch]$AuthorizeMigration
)
$ErrorActionPreference='Stop'
if(-not $AuthorizeMigration){throw 'Explicit -AuthorizeMigration is required.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Save-TaskJson($value,$path){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 50),[Text.UTF8Encoding]::new($false))}
function Get-TaskJson($path){Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json}
function Get-TaskHash($path){(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
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
function Invoke-TaskTool($java,$jar,$version,[string[]]$toolArgs,$log){
    & $java '-Dfile.encoding=UTF-8' ('-Dloader.main=egovframework.backoffice.mvp.operations.V'+$version+'PromotionTool') -cp $jar org.springframework.boot.loader.launch.PropertiesLauncher @toolArgs *> $log
    $LASTEXITCODE
}
# One copy-only step: plan the database against its cold original, then migrate with that version's flag.
function Invoke-TaskStep($java,$jar,$version,$database,$original,$folder,$receiptPath){
    $plan=Join-Path $folder ('migration-plan.v'+$version+'.json')
    if((Invoke-TaskTool $java $jar $version @('plan',$database,$plan,$original) (Join-Path $folder ('plan.v'+$version+'.log'))) -ne 0 -or -not(Test-Path -LiteralPath $plan)){throw ('V'+$version+' plan failed; see plan.v'+$version+'.log.')}
    $planHash=Get-TaskHash $plan
    Set-Item -Path ('Env:AICA_V'+$version+'_PROMOTION_ENABLED') -Value 'true';Set-Item -Path ('Env:AICA_V'+$version+'_PLAN_SHA256') -Value $planHash
    try{$code=Invoke-TaskTool $java $jar $version @('migrate',$plan,$receiptPath) (Join-Path $folder ('migrate.v'+$version+'.log'))}
    finally{Remove-Item -Path ('Env:AICA_V'+$version+'_PROMOTION_ENABLED');Remove-Item -Path ('Env:AICA_V'+$version+'_PLAN_SHA256')}
    if($code -ne 0 -or -not(Test-Path -LiteralPath $receiptPath)){throw ('V'+$version+' migration failed; see migrate.v'+$version+'.log. The source runtime and database were not changed.')}
    $receipt=Get-TaskJson $receiptPath
    if($receipt.status -ne ('MIGRATED_V'+$version) -or $receipt.jarSha256 -ne $targetConfig.jarSha256 -or $receipt.databasePath -cne (Get-TaskRealPath $database) -or @($receipt.after.history).Count -ne [int]$version){throw ('V'+$version+' receipt mismatch.')}
    [ordered]@{version=$version;planSha256=$planHash;receiptSha256=(Get-TaskHash $receiptPath);migratedSha256=(Get-TaskHash $database)}
}

$sourceRuntime=(Resolve-Path -LiteralPath $Source).Path;$targetRuntime=(Resolve-Path -LiteralPath $Target).Path
if($sourceRuntime -eq $targetRuntime){throw 'Source and target must be different runtime folders.'}
$sourceConfig=Get-TaskJson (Join-Path $sourceRuntime 'runtime.json');$targetConfig=Get-TaskJson (Join-Path $targetRuntime 'runtime.json')
$sourceJar=(Resolve-Path -LiteralPath (Join-Path $sourceRuntime 'server.jar')).Path;$targetJar=(Resolve-Path -LiteralPath (Join-Path $targetRuntime 'server.jar')).Path
$targetAgent=(Resolve-Path -LiteralPath (Join-Path $targetRuntime 'graceful-stop.jar')).Path
$sourceReceiptPath=(Resolve-Path -LiteralPath (Join-Path $sourceRuntime 'migration-receipt.json')).Path
$targetReceiptPath=Join-Path $targetRuntime 'migration-receipt.json'
if(Test-Path -LiteralPath $targetReceiptPath){throw 'Target already has a receipt; use a new runtime folder.'}

# 1. Both runtimes are what their configuration says; the target owns its database folder.
if((Get-TaskHash $sourceJar) -ne $sourceConfig.jarSha256){throw 'Source JAR checksum mismatch.'}
if((Get-TaskHash $targetJar) -ne $targetConfig.jarSha256){throw 'Target JAR checksum mismatch.'}
if((Get-TaskHash $targetAgent) -ne $targetConfig.agentSha256){throw 'Target shutdown tool checksum mismatch.'}
if(($targetConfig.PSObject.Properties.Name -contains 'database') -and $targetConfig.database){throw 'A V15 runtime owns its migrated copy; remove "database" from the target runtime.json.'}
foreach($asset in $targetConfig.assets.PSObject.Properties){if((Get-TaskHash (Join-Path (Join-Path $targetRuntime 'assets') $asset.Name)) -ne $asset.Value){throw ('Target asset checksum mismatch: '+$asset.Name)}}
$overlay=Join-Path $targetRuntime 'assets/css/flow.css'
if(Test-Path -LiteralPath $overlay){
    $skin=[byte[]]@((Get-TaskEntryBytes $targetJar 'BOOT-INF/classes/static/next-app/login-shell.css')|Where-Object{$_ -ne 13})
    $expected=(Get-TaskEntryBytes $targetJar 'BOOT-INF/classes/static/css/flow.css')+[byte[]](10)+$skin
    if(-not [Linq.Enumerable]::SequenceEqual([byte[]]$expected,[byte[]][IO.File]::ReadAllBytes($overlay))){throw 'Target login style overlay is not the target JAR flow.css plus login-shell.css.'}
}
$java=$targetConfig.java;if(-not(Test-Path -LiteralPath $java)){throw ('Java not found at '+$java)}

# 2. The source is a completed V13 or V14 runtime, stopped, and its database is unchanged since its last cold inspection.
$sourceReceipt=Get-TaskJson $sourceReceiptPath
if($sourceReceipt.status -notin @('MIGRATED_V13','MIGRATED_V14') -or $sourceReceipt.jarSha256 -ne $sourceConfig.jarSha256 -or $sourceReceipt.workaround -ne 'AUTO_COMPACT_FILL_RATE=0'){throw 'Source receipt is not a completed V13 or V14 receipt for the source JAR.'}
$steps=if($sourceReceipt.status -eq 'MIGRATED_V13'){@('14','15')}else{@('15')}
$expectedAdded=@(@{'14'='h2/V14__site_composition.sql';'15'='h2/V15__structure_membership.sql'}[$steps])
$db=Get-TaskDatabase $sourceRuntime $sourceConfig
if($sourceReceipt.databasePath -cne $db){throw 'Source receipt is bound to another database path.'}
$activePath=Join-Path $sourceRuntime 'active.json'
if((Test-Path -LiteralPath $activePath) -and (Get-TaskJson $activePath).status -ne 'STOPPED'){throw 'Source runtime did not record a normal stop. Stop it with STOP.cmd first.'}
$stem=$db.Substring(0,$db.Length-6).Replace('\','/')
$users=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and ($_.CommandLine.Replace('\','/').IndexOf($stem,[StringComparison]::OrdinalIgnoreCase) -ge 0 -or $_.CommandLine.Contains($sourceJar) -or $_.CommandLine.Contains($targetJar)) })
if($users.Count -gt 0){throw ('A Java process still uses the source database or a JAR (pid '+(($users|ForEach-Object{$_.ProcessId}) -join ',')+').')}
$handle=[IO.File]::Open($db,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$handle.Dispose()
$stops=@(Get-ChildItem -LiteralPath $sourceRuntime -File|Where-Object{$_.Name -match '^(stopped|relocation-inspect|swap-inspect)-\d+\.json$' -and (Get-TaskJson $_.FullName).path -ceq $db})
if($stops.Count -eq 0){throw 'No cold inspection for the source database; stop the source runtime normally first.'}
$stopFile=$stops|Sort-Object {[long]($_.BaseName.Substring($_.BaseName.LastIndexOf('-')+1))}|Select-Object -Last 1
$dbHash=Get-TaskHash $db
if($dbHash -ne (Get-TaskJson $stopFile.FullName).sha256.ToLowerInvariant()){throw ('Source database changed after its last cold inspection ('+$stopFile.Name+').')}

# 3. The V15 JAR keeps every source migration byte for byte and adds only the missing versions.
$prefix='BOOT-INF/classes/db/migration/'
$sourceMigrations=Get-TaskEntries $sourceJar $prefix;$targetMigrations=Get-TaskEntries $targetJar $prefix
foreach($name in $sourceMigrations.Keys){if($targetMigrations[$name] -ne $sourceMigrations[$name]){throw ('Source migration changed or missing in the V15 JAR: '+$name)}}
$added=@($targetMigrations.Keys|Where-Object{-not $sourceMigrations.Contains($_)}|Sort-Object)
if(($added -join ',') -ne ($expectedAdded -join ',')){throw ('The V15 JAR must add exactly '+($expectedAdded -join ', ')+' (added: '+($added -join ', ')+').')}

# 4. Byte-identical copy into the target, then one copy-only step per version.
$targetDbFolder=Join-Path $targetRuntime 'db';New-Item -ItemType Directory -Force -Path $targetDbFolder|Out-Null
$copy=Join-Path $targetDbFolder 'aica-local.mv.db'
if(Test-Path -LiteralPath $copy){throw 'Target database already exists; use a new runtime folder.'}
Copy-Item -LiteralPath $db -Destination $copy
if((Get-TaskHash $copy) -ne $dbHash){throw 'Copy differs from the cold source database.'}
$done=@();$original=$db
foreach($version in $steps){
    if($version -eq '15' -and $steps.Count -gt 1){
        # The V14 result becomes the cold original of the V15 step; it is kept as evidence.
        $stage=Join-Path $targetRuntime 'promotion-stage';New-Item -ItemType Directory -Force -Path $stage|Out-Null
        $original=Join-Path $stage 'aica-local-v14.mv.db';Copy-Item -LiteralPath $copy -Destination $original
        if((Get-TaskHash $original) -ne (Get-TaskHash $copy)){throw 'V14 stage copy differs.'}
    }
    $receiptPath=if($version -eq '15'){$targetReceiptPath}else{Join-Path $targetRuntime ('migration-receipt.v'+$version+'.json')}
    $done+=Invoke-TaskStep $java $targetJar $version $copy $original $targetRuntime $receiptPath
}

# 5. Receipt and originals.
$receipt=Get-TaskJson $targetReceiptPath;$copyReal=Get-TaskRealPath $copy
if($receipt.status -ne 'MIGRATED_V15' -or $receipt.jarSha256 -ne $targetConfig.jarSha256 -or $receipt.databasePath -cne $copyReal -or @($receipt.after.history).Count -ne 15){throw 'V15 receipt mismatch.'}
if((Get-TaskHash $db) -ne $dbHash){throw 'The source database changed during promotion.'}
$summary=[ordered]@{promotedAt=[DateTimeOffset]::UtcNow.ToString('o');tool='scripts/promote-v15-runtime.ps1'
    source=[ordered]@{runtime=$sourceRuntime;status=$sourceReceipt.status;jarSha256=$sourceConfig.jarSha256;database=$db;databaseSha256=$dbHash;coldInspection=$stopFile.FullName;receiptSha256=(Get-TaskHash $sourceReceiptPath)}
    target=[ordered]@{runtime=$targetRuntime;jarSha256=$targetConfig.jarSha256;database=$copyReal;migratedSha256=(Get-TaskHash $copy);receiptSha256=(Get-TaskHash $targetReceiptPath)}
    steps=$done;addedMigrations=$added}
Save-TaskJson $summary (Join-Path $targetRuntime 'promotion.json')
Write-Host ('V15 runtime created: '+$targetConfig.kind+' ('+$copyReal+', steps '+($steps -join ' -> ')+'). The source runtime is unchanged. Select it with scripts/select-v12-runtime.ps1.')
