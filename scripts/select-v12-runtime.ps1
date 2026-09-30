# Points START.cmd/STOP.cmd (.cache/current-ui.json) at another V12 runtime, for example RC1 -> RC2
# or back for rollback. Refuses while the current or the selected runtime is running or while any
# Java process names either database, so two runtimes never share a database at the same time.
param(
    [Parameter(Mandatory=$true)][string]$Runtime,
    [switch]$AuthorizeSelection,
    # Rehearsals pass a copy; START.cmd/STOP.cmd always read .cache/current-ui.json.
    [string]$CurrentUi
)
$ErrorActionPreference='Stop'
if(-not $AuthorizeSelection){throw 'Explicit -AuthorizeSelection is required.'}
function Save-TaskJson($value,$path){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false))}
function Get-TaskJson($path){Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json}
function Get-TaskDatabase($runtime,$config){
    $setting='db/aica-local.mv.db'
    if(($config.PSObject.Properties.Name -contains 'database') -and $config.database){$setting=$config.database}
    if(-not [IO.Path]::IsPathRooted($setting)){$setting=Join-Path $runtime $setting}
    (Resolve-Path -LiteralPath $setting).Path
}
function Assert-TaskIdle($runtime){
    $config=Get-TaskJson (Join-Path $runtime 'runtime.json');$jar=Join-Path $runtime 'server.jar';$db=Get-TaskDatabase $runtime $config
    $activePath=Join-Path $runtime 'active.json'
    if((Test-Path -LiteralPath $activePath) -and ($active=Get-TaskJson $activePath).status -ne 'STOPPED'){
        $process=Get-CimInstance Win32_Process -Filter ('ProcessId = '+[int]$active.pid) -ErrorAction SilentlyContinue
        if($process -and $process.CommandLine -and $process.CommandLine.Contains($jar)){throw ('Runtime is running: '+$runtime+'. Stop it with STOP.cmd first.')}
    }
    $stem=$db.Substring(0,$db.Length-6).Replace('\','/')
    $users=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Replace('\','/').IndexOf($stem,[StringComparison]::OrdinalIgnoreCase) -ge 0 })
    if($users.Count -gt 0){throw ('A Java process uses '+$db+' (pid '+(($users|ForEach-Object{$_.ProcessId}) -join ',')+').')}
    if(Get-NetTCPConnection -LocalPort $config.port -State Listen -ErrorAction SilentlyContinue){throw ('Port '+$config.port+' is in use.')}
}
$root=Split-Path -Parent $PSScriptRoot
$configPath=Join-Path $root '.cache/current-ui.json';if($CurrentUi){$configPath=[IO.Path]::GetFullPath($CurrentUi)}
$selected=(Resolve-Path -LiteralPath $Runtime).Path
if(-not $selected.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Runtime must be inside this repository.'}
$selectedConfig=Get-TaskJson (Join-Path $selected 'runtime.json')
$receipt=Get-TaskJson (Join-Path $selected 'migration-receipt.json')
if($receipt.status -notin @('MIGRATED_V12','MIGRATED_V13','MIGRATED_V14','MIGRATED_V15') -or $receipt.jarSha256 -ne $selectedConfig.jarSha256 -or $receipt.workaround -ne 'AUTO_COMPACT_FILL_RATE=0'){throw 'Selected runtime has no completed V12/V13 receipt for its JAR.'}
if((Get-FileHash -LiteralPath (Join-Path $selected 'server.jar') -Algorithm SHA256).Hash -ne $selectedConfig.jarSha256){throw 'Selected JAR checksum mismatch.'}
if($receipt.databasePath -ne (Get-TaskDatabase $selected $selectedConfig)){throw 'Selected receipt is bound to another database path.'}
$relative=$selected.Substring($root.Length+1).Replace('\','/')
if(Test-Path -LiteralPath $configPath){
    $current=Get-TaskJson $configPath
    $currentRuntime=(Resolve-Path -LiteralPath (Join-Path $root $current.runtime)).Path
    if($currentRuntime -eq $selected){Write-Host ('Already selected: '+$relative);exit 0}
    if($current.kind -in @('v12','v13','v14','v15','development-v12')){Assert-TaskIdle $currentRuntime}
    Copy-Item -LiteralPath $configPath -Destination (Join-Path (Split-Path -Parent $configPath) ('current-ui.before-'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()+'.json'))
}
Assert-TaskIdle $selected
Save-TaskJson ([ordered]@{kind=$(switch($receipt.status){'MIGRATED_V15'{'v15'}'MIGRATED_V14'{'v14'}'MIGRATED_V13'{'v13'}default{'v12'}});runtime=$relative}) $configPath
Write-Host ('Selected '+$selectedConfig.kind+': '+$relative+'. Start with START.cmd.')
