param([switch]$Stop)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$taskConfigPath=Join-Path $taskRoot '.cache/current-ui.json'
if(-not(Test-Path -LiteralPath $taskConfigPath)){throw 'This PC has no current-ui.json runtime configuration.'}
$taskConfig=Get-Content -LiteralPath $taskConfigPath -Raw -Encoding utf8 | ConvertFrom-Json
$taskRuntime=(Resolve-Path -LiteralPath (Join-Path $taskRoot $taskConfig.runtime)).Path
# V12, V13 and V14 runtimes (development copy or RC) share one launcher that validates hashes and the receipt-bound DB.
if($taskConfig.kind -in @('development-v12','v12','v13','v14')){
    if($Stop){
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'start-v12-runtime.ps1') -Runtime $taskRuntime -Stop
    }else{
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'start-v12-runtime.ps1') -Runtime $taskRuntime
    }
    exit $LASTEXITCODE
}
if($Stop){
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $taskRuntime 'STOP.ps1')
}else{
    $taskRelease=(Resolve-Path -LiteralPath (Join-Path $taskRoot $taskConfig.release)).Path
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'start-v11-ui.ps1') -Runtime $taskRuntime -UiRelease $taskRelease
}
exit $LASTEXITCODE
