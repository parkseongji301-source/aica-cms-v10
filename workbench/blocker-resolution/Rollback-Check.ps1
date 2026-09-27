$ErrorActionPreference='Stop'
$b=(Get-Content .cache/phase5c1b-current.txt -Raw).Trim()
$old=(Get-Content .cache/phase5c1-current.txt -Raw).Trim()
$cp=(Get-Content "$b/classpath.txt" -Raw).Trim()
$java=(Resolve-Path .tools/jdk/jdk-17.0.20.1+1/bin/java.exe).Path
$file=(Resolve-Path "$b/data/server-inside.mv.db").Path
$probe=[IO.File]::Open($file,'Open','Read','None');$probe.Dispose()
Copy-Item -LiteralPath $file -Destination "$b/data/server-inside-v10-before-rollback.mv.db"
$registry=Get-Content "$b/registry.json" -Raw | ConvertFrom-Json
$registry.allowedFiles=@($registry.allowedFiles)+@("$b/data/server-inside-v10-before-rollback.mv.db")
$registry | ConvertTo-Json -Depth 5 | Set-Content "$b/registry.json" -Encoding utf8
Copy-Item -LiteralPath "$b/v3-backup.mv.db" -Destination $file
if((Get-FileHash $file).Hash -ne (Get-FileHash "$b/v3-backup.mv.db").Hash){throw 'V3 restoration byte mismatch'}
& $java "-Dprobe.registry=$b/registry.json" -cp $cp BareProbe snapshot $file "$b/evidence/rollback-before.json"
if($LASTEXITCODE -ne 0){throw 'Before snapshot failed'}
if(Get-NetTCPConnection -LocalPort 8097 -State Listen -ErrorAction SilentlyContinue){throw '8097 occupied'}
$url='jdbc:h2:file:'+$file.Substring(0,$file.Length-6).Replace('\','/')+';IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0'
$jar="$old/release/v3-runtime.jar"
$argsList=@('-Dfile.encoding=UTF-8','-jar',$jar,'--spring.profiles.active=dev','--server.address=127.0.0.1','--server.port=8097',"--spring.datasource.url=$url",'--backoffice.bootstrap.enabled=false','--backoffice.preview=true','--logging.level.com.zaxxer.hikari.HikariConfig=DEBUG')
$argsList | ConvertTo-Json | Set-Content "$b/evidence/rollback.args.json"
$p=Start-Process -FilePath $java -ArgumentList (($argsList | ForEach-Object {'"'+$_+'"'}) -join ' ') -WorkingDirectory (Get-Location).Path -WindowStyle Hidden -RedirectStandardOutput "$b/evidence/rollback.log" -RedirectStandardError "$b/evidence/rollback.err" -PassThru
$p.Id | Set-Content "$b/evidence/rollback.pid"
$ready=$false
for($n=0;$n -lt 60;$n++){Start-Sleep -Seconds 1;if($p.HasExited){throw 'V3 runtime exited'};if((Get-Content "$b/evidence/rollback.log" -Raw) -match 'Started .* in [0-9]'){$ready=$true;break}}
if(!$ready){throw 'V3 startup timeout'}
& $java --add-modules jdk.attach -cp "$b/tools" RuntimeObserver $p.Id "$b/tools/observer.jar" "$b/evidence/rollback.start.json|$file|$url"
if($LASTEXITCODE -ne 0){throw 'V3 observer failed'}
& $java "-Dprobe.registry=$b/registry.json" -cp $cp LockProbe $file "$b/evidence/rollback.concurrent-lock.json"
if($LASTEXITCODE -ne 0){throw 'Concurrent file access check failed'}
& 'C:/Users/sfsf1/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -X utf8 workbench/cutover-rehearsal/http_client.py 8097 read-smoke "$b/evidence/rollback-http.json"
if($LASTEXITCODE -ne 0){throw 'V3 smoke failed'}
& $java --add-modules jdk.attach -cp .cache GracefulStop $p.Id (Resolve-Path .cache/graceful-stop.jar).Path
$p.WaitForExit(30000) | Out-Null
if(!$p.HasExited){throw 'V3 process not stopped'}
$probe=[IO.File]::Open($file,'Open','Read','None');$probe.Dispose()
& $java "-Dprobe.registry=$b/registry.json" -cp $cp BareProbe snapshot $file "$b/evidence/rollback-after.json"
if($LASTEXITCODE -ne 0){throw 'After snapshot failed'}
& $java '-Dfile.encoding=UTF-8' -cp $cp LifetimeProbe read $file "$b/evidence/rollback.external.json" "$b/registry.json" *> "$b/evidence/rollback.external.log"
if($LASTEXITCODE -ne 0){throw 'External rollback inspection failed'}
@{pid=$p.Id;jar=$jar;jarSha256=(Get-FileHash $jar).Hash;jdbcUrl=$url;profile='dev';processExited=$p.HasExited;exclusiveReadSucceeded=$true;physicalPath=$file;sha256=(Get-FileHash $file).Hash;size=(Get-Item $file).Length;modified=(Get-Item $file).LastWriteTimeUtc.ToString('o');fileId=(& fsutil file queryfileid $file | Out-String).Trim()} | ConvertTo-Json | Set-Content "$b/evidence/rollback.result.json"
Write-Output 'V3 DB + V3 runtime rollback completed; original untouched'
