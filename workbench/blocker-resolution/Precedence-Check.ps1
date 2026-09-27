$ErrorActionPreference='Stop'
$b=(Get-Content .cache/phase5c1b-current.txt -Raw).Trim();$old=(Get-Content .cache/phase5c1-current.txt -Raw).Trim()
$external=(Get-Content "$b/external-root.txt" -Raw).Trim();$cp=(Get-Content "$b/classpath.txt" -Raw).Trim()
$java=(Resolve-Path .tools/jdk/jdk-17.0.20.1+1/bin/java.exe).Path
$file=(Resolve-Path "$external/server-outside.mv.db").Path
$url='jdbc:h2:file:'+$file.Substring(0,$file.Length-6).Replace('\','/')+';IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0'
$envPrior=$env:BACKOFFICE_DEV_DB_URL;$profilePrior=$env:SPRING_PROFILES_ACTIVE
try {
 $env:BACKOFFICE_DEV_DB_URL="jdbc:h2:file:$($external.Replace('\','/'))/must-not-open-env;IFEXISTS=TRUE"
 $env:SPRING_PROFILES_ACTIVE='intentionally-unselected-env'
 $jvmUrl="jdbc:h2:file:$($external.Replace('\','/'))/must-not-open-jvm;IFEXISTS=TRUE"
 $argsList=@('-Dfile.encoding=UTF-8',"-Dspring.datasource.url=$jvmUrl",'-Dspring.profiles.active=intentionally-unselected-jvm','-jar',"$old/release/v10-rc.jar",'--spring.profiles.active=dev','--server.address=127.0.0.1','--server.port=8096',"--spring.datasource.url=$url",'--backoffice.bootstrap.enabled=false','--backoffice.preview=true','--backoffice.classification-migration.copy-validation=true','--logging.level.com.zaxxer.hikari.HikariConfig=DEBUG')
 if(Get-NetTCPConnection -LocalPort 8096 -State Listen -ErrorAction SilentlyContinue){throw '8096 occupied'}
 $argsList | ConvertTo-Json | Set-Content "$b/evidence/precedence.args.json"
 @{environmentUrl=$env:BACKOFFICE_DEV_DB_URL;environmentProfile=$env:SPRING_PROFILES_ACTIVE;beforeHash=(Get-FileHash $file).Hash} | ConvertTo-Json | Set-Content "$b/evidence/precedence.environment.json"
 $p=Start-Process -FilePath $java -ArgumentList (($argsList | ForEach-Object {'"'+$_+'"'}) -join ' ') -WorkingDirectory (Get-Location).Path -WindowStyle Hidden -RedirectStandardOutput "$b/evidence/precedence.log" -RedirectStandardError "$b/evidence/precedence.err" -PassThru
 $p.Id | Set-Content "$b/evidence/precedence.pid"
 $ready=$false;for($n=0;$n -lt 60;$n++){Start-Sleep -Seconds 1;if($p.HasExited){throw 'Precedence startup failed'};if((Get-Content "$b/evidence/precedence.log" -Raw) -match 'Started .* in [0-9]'){$ready=$true;break}}
 if(!$ready){throw 'Startup timeout'}
 & $java --add-modules jdk.attach -cp "$b/tools" RuntimeObserver $p.Id "$b/tools/observer.jar" "$b/evidence/precedence.start.json|$file|$url"
 if($LASTEXITCODE -ne 0){throw 'Observer failed'}
 & $java "-Dprobe.registry=$b/registry.json" -cp $cp LockProbe $file "$b/evidence/precedence.concurrent-lock.json"
 if($LASTEXITCODE -ne 0){throw 'V10 concurrent connection test failed'}
 & $java --add-modules jdk.attach -cp .cache GracefulStop $p.Id (Resolve-Path .cache/graceful-stop.jar).Path
 $p.WaitForExit(30000) | Out-Null;if(!$p.HasExited){throw 'Process still alive'}
 $s=[IO.File]::Open($file,'Open','Read','None');$s.Dispose()
 & $java '-Dfile.encoding=UTF-8' -cp $cp LifetimeProbe read $file "$b/evidence/precedence.external.json" "$b/registry.json" *> "$b/evidence/precedence.external.log"
 if($LASTEXITCODE -ne 0){throw 'Readonly failed'}
 if((Test-Path "$external/must-not-open-env.mv.db") -or (Test-Path "$external/must-not-open-jvm.mv.db")){throw 'Unselected file created'}
 Write-Output "PASS command-line dev/profile + absolute JDBC override env/JVM, no wrong file created; PID $($p.Id) exited"
} finally {$env:BACKOFFICE_DEV_DB_URL=$envPrior;$env:SPRING_PROFILES_ACTIVE=$profilePrior}
