param([ValidateSet('inside','outside')][string]$Place='inside',[int]$Rounds=4)
$ErrorActionPreference='Stop'
$taskBundle=(Get-Content .cache/phase5c1b-current.txt -Raw).Trim()
$taskOld=(Get-Content .cache/phase5c1-current.txt -Raw).Trim()
$taskExternal=(Get-Content "$taskBundle/external-root.txt" -Raw).Trim()
$taskCp=(Get-Content "$taskBundle/classpath.txt" -Raw).Trim()
$taskJava=(Resolve-Path .tools/jdk/jdk-17.0.20.1+1/bin/java.exe).Path
$taskPython='C:/Users/sfsf1/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$taskFile=if($Place -eq 'inside'){"$taskBundle/data/server-inside.mv.db"}else{"$taskExternal/server-outside.mv.db"}
if(Test-Path -LiteralPath $taskFile){throw 'Refusing to replace previous server evidence'}
Copy-Item -LiteralPath "$taskBundle/v3-backup.mv.db" -Destination $taskFile
$taskRegistry=Get-Content "$taskBundle/registry.json" -Raw | ConvertFrom-Json
$taskRegistry.allowedFiles=@($taskRegistry.allowedFiles)+@($taskFile)
$taskRegistry | ConvertTo-Json -Depth 5 | Set-Content "$taskBundle/registry.json" -Encoding utf8
$taskBefore=Get-FileHash $taskFile -Algorithm SHA256
& $taskJava '-Dfile.encoding=UTF-8' -cp $taskCp LifetimeProbe migrate $taskFile "$taskBundle/evidence/server-$Place-migrate.json" "$taskBundle/registry.json" ';AUTO_COMPACT_FILL_RATE=0' *> "$taskBundle/evidence/server-$Place-migrate.log"
if($LASTEXITCODE -ne 0){throw 'Migration failed'}
$taskResolved=(Resolve-Path -LiteralPath $taskFile).Path
$taskUrl='jdbc:h2:file:'+$taskResolved.Substring(0,$taskResolved.Length-6).Replace('\','/')+';IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0'
$taskJar="$taskOld/release/v10-rc.jar"
foreach($round in 1..$Rounds) {
 if(Get-NetTCPConnection -LocalPort 8096 -State Listen -ErrorAction SilentlyContinue){throw '8096 occupied'}
 $taskProbe=[IO.File]::Open($taskResolved,'Open','Read','None');$taskProbe.Dispose()
 $name="server-$Place-$round"
 $before=@{sha256=(Get-FileHash $taskFile -Algorithm SHA256).Hash;fileId=(& fsutil file queryfileid $taskResolved | Out-String).Trim();path=$taskResolved;size=(Get-Item $taskFile).Length;modified=(Get-Item $taskFile).LastWriteTimeUtc.ToString('o')}
 $taskArgs=@('-Dfile.encoding=UTF-8','-jar',$taskJar,'--spring.profiles.active=dev','--server.address=127.0.0.1','--server.port=8096',"--spring.datasource.url=$taskUrl",'--backoffice.bootstrap.enabled=false','--backoffice.preview=true','--backoffice.classification-migration.copy-validation=true','--logging.level.com.zaxxer.hikari.HikariConfig=DEBUG')
 $taskArgs | ConvertTo-Json | Set-Content "$taskBundle/evidence/$name.args.json"
 $taskCommand=($taskArgs | ForEach-Object {'"'+$_+'"'}) -join ' '
 $taskProcess=Start-Process -FilePath $taskJava -ArgumentList $taskCommand -WorkingDirectory (Get-Location).Path -WindowStyle Hidden -RedirectStandardOutput "$taskBundle/evidence/$name.log" -RedirectStandardError "$taskBundle/evidence/$name.err" -PassThru
 $taskProcess.Id | Set-Content "$taskBundle/evidence/$name.pid"
 $started=$false
 for($n=0;$n -lt 60;$n++) {
  Start-Sleep -Seconds 1
  if($taskProcess.HasExited){throw 'Server exited during startup'}
  if((Get-Content "$taskBundle/evidence/$name.log" -Raw) -match 'Started .* in [0-9]'){$started=$true;break}
 }
 if(!$started){throw 'Startup timeout'}
 & $taskJava --add-modules jdk.attach -cp "$taskBundle/tools" RuntimeObserver $taskProcess.Id "$taskBundle/tools/observer.jar" "$taskBundle/evidence/$name.start.json|$taskResolved|$taskUrl"
 if($LASTEXITCODE -ne 0){throw 'Runtime observer failed'}
 $lockBlocked=$false
 try{$s=[IO.File]::Open($taskResolved,'Open','Read','None');$s.Dispose()}catch{$lockBlocked=$true}
 if(!$lockBlocked){throw 'Live database lock absent'}
 & $taskPython -X utf8 workbench/blocker-resolution/runtime_check.py "$taskBundle/evidence/server-$Place-http-$round.json" "server-$Place" $round
 if($LASTEXITCODE -ne 0){throw 'HTTP persistence failed'}
 & $taskJava --add-modules jdk.attach -cp "$taskBundle/tools" RuntimeObserver $taskProcess.Id "$taskBundle/tools/observer.jar" "$taskBundle/evidence/$name.before-stop.json|$taskResolved|$taskUrl"
 if($LASTEXITCODE -ne 0){throw 'Before-stop observer failed'}
 & .tools/jdk/jdk-17.0.20.1+1/bin/jcmd.exe $taskProcess.Id VM.command_line > "$taskBundle/evidence/$name.command.txt"
 & $taskJava --add-modules jdk.attach -cp .cache GracefulStop $taskProcess.Id (Resolve-Path .cache/graceful-stop.jar).Path
 if($LASTEXITCODE -ne 0){throw 'Graceful shutdown request failed'}
 $taskProcess.WaitForExit(30000) | Out-Null
 if(!$taskProcess.HasExited){throw 'Server did not exit'}
 $taskProbe=[IO.File]::Open($taskResolved,'Open','Read','None');$taskProbe.Dispose()
 $after=@{sha256=(Get-FileHash $taskFile -Algorithm SHA256).Hash;fileId=(& fsutil file queryfileid $taskResolved | Out-String).Trim();path=$taskResolved;size=(Get-Item $taskFile).Length;modified=(Get-Item $taskFile).LastWriteTimeUtc.ToString('o')}
 if(!((Get-Content "$taskBundle/evidence/$name.log" -Raw) -match 'HikariPool-1 - Shutdown completed')){throw 'Pool shutdown missing'}
 & $taskJava '-Dfile.encoding=UTF-8' -cp $taskCp LifetimeProbe read $taskFile "$taskBundle/evidence/$name.external.json" "$taskBundle/registry.json" *> "$taskBundle/evidence/$name.external.log"
 if($LASTEXITCODE -ne 0){throw 'External inspection failed'}
 $ext=Get-Content "$taskBundle/evidence/$name.external.json" -Raw | ConvertFrom-Json
 $read=$ext | Where-Object phase -eq 'readonly'
 if($read.history[-1].version -ne '10'){throw 'Version regressed'}
 $saved=Get-Content "$taskBundle/evidence/server-$Place-http-$round.json" -Raw | ConvertFrom-Json
 if(($read.representatives | Where-Object {$_.KIND -eq 'page' -and $_.ID -eq '65'}).TITLE -ne $saved.saved.title){throw 'Changed row lost'}
 if((Get-FileHash $taskFile -Algorithm SHA256).Hash -ne $after.sha256){throw 'Readonly inspection changed file'}
 @{name=$name;profile='dev';pid=$taskProcess.Id;jdbcUrl=$taskUrl;jar=$taskJar;jarSha256=(Get-FileHash $taskJar -Algorithm SHA256).Hash;before=$before;after=$after;liveLockBlocked=$lockBlocked;processExited=$taskProcess.HasExited;shutdownLockReleased=$true;flywayVersion=$read.history[-1].version;revision=$saved.saved.revision} | ConvertTo-Json -Depth 10 | Set-Content "$taskBundle/evidence/$name.result.json" -Encoding utf8
 Write-Output "PASS $name PID=$($taskProcess.Id) V10 revision=$($saved.saved.revision), normal shutdown, lock released"
}
