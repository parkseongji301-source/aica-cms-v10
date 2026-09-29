param([Parameter(Mandatory)][string]$ShellPath,[Parameter(Mandatory)][string]$ScriptPath,[Parameter(Mandatory)][string]$WorkingDirectory,[Parameter(Mandatory)][string]$OutputPrefix)
$ErrorActionPreference='Stop'
$taskProcess=Start-Process -FilePath $ShellPath -ArgumentList '-NoProfile','-File',('"'+$ScriptPath+'"') -WorkingDirectory $WorkingDirectory -WindowStyle Hidden -RedirectStandardOutput ($OutputPrefix+'.stdout.log') -RedirectStandardError ($OutputPrefix+'.stderr.log') -PassThru
@{pid=$taskProcess.Id;script=$ScriptPath}|ConvertTo-Json|Set-Content -Encoding utf8 ($OutputPrefix+'-wrapper.json')
