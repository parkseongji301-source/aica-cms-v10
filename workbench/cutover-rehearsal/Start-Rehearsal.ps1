param([Parameter(Mandatory=$true)][ValidateSet('v3','v10')][string]$Runtime,
 [Parameter(Mandatory=$true)][string]$Database,
 [Parameter(Mandatory=$true)][ValidateSet(8095,8096,8097)][int]$Port,
 [Parameter(Mandatory=$true)][string]$LogName)
$ErrorActionPreference='Stop'
$taskBundle=(Get-Content .cache/phase5c1-current.txt -Raw).Trim()
$taskResolved=(Resolve-Path -LiteralPath $Database).Path
$taskAllowed=(Resolve-Path -LiteralPath "$taskBundle/data").Path+[IO.Path]::DirectorySeparatorChar
if(!$taskResolved.StartsWith($taskAllowed,[StringComparison]::OrdinalIgnoreCase) -or !$taskResolved.EndsWith('.mv.db')){throw 'Only existing rehearsal copies are allowed'}
if(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue){throw 'Port already in use'}
$taskProbe=[IO.File]::Open($taskResolved,'Open','Read','None');$taskProbe.Dispose()
$taskJar=if($Runtime -eq 'v3'){"$taskBundle/release/v3-runtime.jar"}else{"$taskBundle/release/v10-rc.jar"}
$taskBase=$taskResolved.Substring(0,$taskResolved.Length-6).Replace('\','/')
$taskArguments=@('-Dfile.encoding=UTF-8','-jar',$taskJar,'--spring.profiles.active=dev','--server.address=127.0.0.1',"--server.port=$Port","--spring.datasource.url=jdbc:h2:file:$taskBase;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0",'--backoffice.bootstrap.enabled=false','--backoffice.preview=true')
if($Runtime -eq 'v10'){$taskArguments+='--backoffice.classification-migration.copy-validation=true'}
$taskArguments | ConvertTo-Json | Set-Content "$taskBundle/evidence/$LogName-arguments.json"
$taskCommand=($taskArguments | ForEach-Object {'"'+$_+'"'}) -join ' '
$taskProcess=Start-Process -FilePath .tools/jdk/jdk-17.0.20.1+1/bin/java.exe -ArgumentList $taskCommand -WorkingDirectory (Get-Location).Path -WindowStyle Hidden -RedirectStandardOutput "$taskBundle/evidence/$LogName.log" -RedirectStandardError "$taskBundle/evidence/$LogName.err" -PassThru
$taskProcess.Id | Set-Content "$taskBundle/evidence/$LogName.pid"
Write-Output "Started $Runtime on $Port, PID $($taskProcess.Id)"
