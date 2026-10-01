param([Parameter(Mandatory=$true)][string]$Runtime,[switch]$Stop)
$ErrorActionPreference='Stop'
$taskRuntime=(Resolve-Path -LiteralPath $Runtime).Path
$taskConfig=Get-Content -LiteralPath (Join-Path $taskRuntime 'runtime.json') -Raw -Encoding utf8 | ConvertFrom-Json
$taskJar=(Resolve-Path -LiteralPath (Join-Path $taskRuntime 'server.jar')).Path
# A replacement JAR (for example RC2) may use the database of the runtime it replaced; its own receipt binds both.
$taskDbSetting='db/aica-local.mv.db'
if(($taskConfig.PSObject.Properties.Name -contains 'database') -and $taskConfig.database){$taskDbSetting=$taskConfig.database}
if(-not [IO.Path]::IsPathRooted($taskDbSetting)){$taskDbSetting=Join-Path $taskRuntime $taskDbSetting}
$taskDb=(Resolve-Path -LiteralPath $taskDbSetting).Path
$taskAgent=(Resolve-Path -LiteralPath (Join-Path $taskRuntime 'graceful-stop.jar')).Path
$taskReceipt=(Resolve-Path -LiteralPath (Join-Path $taskRuntime 'migration-receipt.json')).Path
$taskActivePath=Join-Path $taskRuntime 'active.json'
if((Get-FileHash -LiteralPath $taskJar -Algorithm SHA256).Hash -ne $taskConfig.jarSha256){throw 'Development JAR checksum mismatch.'}
if((Get-FileHash -LiteralPath $taskAgent -Algorithm SHA256).Hash -ne $taskConfig.agentSha256){throw 'Shutdown tool checksum mismatch.'}
$taskJava=$taskConfig.java
function Save-TaskJson($value,$path){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false))}
if($Stop){
 if(-not(Test-Path -LiteralPath $taskActivePath)){throw 'No recorded server to stop.'}
 $taskActive=Get-Content -LiteralPath $taskActivePath -Raw -Encoding utf8 | ConvertFrom-Json
 if($taskActive.status -eq 'STOPPED'){Write-Host 'Already stopped normally.';exit 0}
 $taskServer=Get-Process -Id $taskActive.pid -ErrorAction Stop
 if($taskServer.StartTime.ToUniversalTime().Ticks.ToString() -ne $taskActive.startedTicks -or $taskServer.Path -ne $taskJava){throw 'Process identity changed; refusing to stop.'}
 $taskCommand=(Get-CimInstance Win32_Process -Filter ('ProcessId = '+$taskActive.pid)).CommandLine
 if(-not $taskCommand.Contains($taskJar)){throw 'Server command does not match this runtime.'}
 & $taskJava --add-modules jdk.attach -cp $taskAgent GracefulStop $taskActive.pid $taskAgent
 if($LASTEXITCODE -ne 0){throw 'Normal shutdown request failed.'}
 if(-not $taskServer.WaitForExit(60000)){throw 'Normal shutdown timed out; inspect logs. Do not force termination.'}
 $taskHandle=[IO.File]::Open($taskDb,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$taskHandle.Dispose()
 $taskEvidence=Join-Path $taskRuntime ('stopped-'+[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()+'.json')
 & $taskJava '-Dloader.main=egovframework.backoffice.mvp.operations.V12PromotionTool' -cp $taskJar org.springframework.boot.loader.launch.PropertiesLauncher inspect $taskDb $taskEvidence
 if($LASTEXITCODE -ne 0){throw 'Cold database validation failed.'}
 $taskActive.status='STOPPED';Save-TaskJson $taskActive $taskActivePath;Write-Host 'V12 stopped normally and cold database verified.';exit 0
}
if(Get-NetTCPConnection -LocalPort $taskConfig.port -State Listen -ErrorAction SilentlyContinue){throw 'Port is in use. Stop the current runtime normally first.'}
# Runtimes that share a database must never run together: refuse while any Java process names this database.
$taskDbStem=$taskDb.Substring(0,$taskDb.Length-6).Replace('\','/')
$taskUsers=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Replace('\','/').IndexOf($taskDbStem,[StringComparison]::OrdinalIgnoreCase) -ge 0 })
if($taskUsers.Count -gt 0){throw ('Another process is using this database (pid '+(($taskUsers|ForEach-Object{$_.ProcessId}) -join ',')+'). Stop that runtime normally first.')}
$taskHandle=[IO.File]::Open($taskDb,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None);$taskHandle.Dispose()
foreach($taskAsset in $taskConfig.assets.PSObject.Properties){
 $taskAssetPath=Join-Path (Join-Path $taskRuntime 'assets') $taskAsset.Name
 if((Get-FileHash -LiteralPath $taskAssetPath -Algorithm SHA256).Hash -ne $taskAsset.Value){throw ('Static asset checksum mismatch: '+$taskAsset.Name)}
}
$taskStamp=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$taskLog=Join-Path $taskRuntime ('server-'+$taskStamp)
$taskUrl='jdbc:h2:file:'+($taskDb.Substring(0,$taskDb.Length-6).Replace('\','/'))+';IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0'
$taskAssets=([Uri]((Join-Path $taskRuntime 'assets')+'\')).AbsoluteUri
# Every promotion flag up to the receipt's schema version is passed off explicitly (the server refuses them anyway).
$taskSchemaMatch=[regex]::Match((Get-Content -LiteralPath $taskReceipt -Raw -Encoding utf8 | ConvertFrom-Json).status,'^MIGRATED_V(\d+)$')
if(-not $taskSchemaMatch.Success){throw 'Receipt status is not a completed MIGRATED_V<n> receipt.'}
$taskPromotionFlags=@(11..([int]$taskSchemaMatch.Groups[1].Value) | ForEach-Object { '--AICA_V'+$_+'_PROMOTION_ENABLED=false' })
$taskArgs=@('-Dfile.encoding=UTF-8','-jar',('"'+$taskJar+'"'),'--spring.profiles.active=dev','--server.address=127.0.0.1',('--server.port='+$taskConfig.port),('"--spring.datasource.url='+$taskUrl+'"'),'--backoffice.bootstrap.enabled=false','--AICA_CUTOVER_ENABLED=false')+$taskPromotionFlags+@(('"--AICA_RUNTIME_RECEIPT='+$taskReceipt+'"'),('"--spring.web.resources.static-locations='+$taskAssets+',classpath:/static/"'))
$taskServer=Start-Process -FilePath $taskJava -ArgumentList $taskArgs -WorkingDirectory $taskRuntime -WindowStyle Hidden -RedirectStandardOutput ($taskLog+'.stdout.log') -RedirectStandardError ($taskLog+'.stderr.log') -PassThru
$taskActive=[ordered]@{status='RUNNING';pid=$taskServer.Id;startedTicks=$taskServer.StartTime.ToUniversalTime().Ticks.ToString();jar=$taskJar;database=$taskDb;log=$taskLog;port=$taskConfig.port}
Save-TaskJson $taskActive $taskActivePath
for($taskAttempt=0;$taskAttempt -lt 100;$taskAttempt++){
 $taskServer.Refresh();if($taskServer.HasExited){throw ('Server failed; inspect '+$taskLog+'.stdout.log')}
 try{$taskResponse=Invoke-WebRequest -Uri ('http://127.0.0.1:'+$taskConfig.port+'/login') -UseBasicParsing -TimeoutSec 1;if($taskResponse.StatusCode -eq 200){Write-Host ('V12 ready: http://127.0.0.1:'+$taskConfig.port+'/ ('+$taskConfig.kind+')');exit 0}}catch{}
 Start-Sleep -Milliseconds 500
}
throw 'Startup timed out. Inspect logs and use STOP.cmd before retrying.'
