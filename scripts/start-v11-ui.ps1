param(
    [Parameter(Mandatory=$true)][string]$UiRelease,
    [string]$Runtime
)
$ErrorActionPreference='Stop'
if(-not $Runtime){$Runtime=Join-Path (Split-Path -Parent $PSScriptRoot) '.cache/runtime-v11'}
$taskRuntime=(Resolve-Path -LiteralPath $Runtime).Path
$taskRelease=(Resolve-Path -LiteralPath $UiRelease).Path
$taskConfig=Get-Content -LiteralPath (Join-Path $taskRuntime 'run/input.json') -Raw -Encoding utf8 | ConvertFrom-Json
$taskTool=Join-Path $PSScriptRoot 'v11/serve_ui.py'
if(Get-NetTCPConnection -LocalPort $taskConfig.port -State Listen -ErrorAction SilentlyContinue){throw 'Port in use; stop the existing runtime normally first.'}
& $taskConfig.python -I -X utf8 -B $taskTool --workspace $taskRuntime --ui-release $taskRelease --verify-only
if($LASTEXITCODE -ne 0){throw 'UI/runtime verification failed.'}
$taskLog=Join-Path $taskRuntime ('start-ui-'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
$taskProcess=Start-Process -FilePath $taskConfig.python -ArgumentList '-I','-X','utf8','-B',('"'+$taskTool+'"'),'--workspace',('"'+$taskRuntime+'"'),'--ui-release',('"'+$taskRelease+'"') -WorkingDirectory $taskRuntime -WindowStyle Hidden -RedirectStandardOutput ($taskLog+'.stdout.log') -RedirectStandardError ($taskLog+'.stderr.log') -PassThru
for($taskAttempt=0;$taskAttempt -lt 240;$taskAttempt++){
    $taskProcess.Refresh()
    if($taskProcess.HasExited){throw ('Server stopped. Read '+$taskLog+'.stderr.log')}
    if((Get-Content -LiteralPath ($taskLog+'.stdout.log') -Raw -ErrorAction SilentlyContinue) -match 'V11 UI ready:'){
        Write-Host ('V11 UI ready: http://127.0.0.1:'+$taskConfig.port+'/admin-next/posts?view=structure')
        exit 0
    }
    Start-Sleep -Milliseconds 500
}
throw 'Startup timed out. Inspect logs and use the runtime STOP.ps1 before retrying.'
