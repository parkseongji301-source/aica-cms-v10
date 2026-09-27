$ErrorActionPreference='Stop'
$taskBundle=(Get-Content .cache/phase5c1b-current.txt -Raw).Trim()
$taskOld=(Get-Content .cache/phase5c1-current.txt -Raw).Trim()
$taskExternal=(Get-Content "$taskBundle/external-root.txt" -Raw).Trim()
$taskCp=(Get-Content "$taskBundle/classpath.txt" -Raw).Trim()
$taskJava=(Resolve-Path .tools/jdk/jdk-17.0.20.1+1/bin/java.exe).Path
$taskResults=@()
function FileEvidence([string]$p) {
 $f=Get-Item -LiteralPath $p
 $h=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash
 $id=(& fsutil file queryfileid $p | Out-String).Trim()
 $locked=$false
 try{$s=[IO.File]::Open($p,'Open','Read','None');$s.Dispose()}catch{$locked=$true}
 return @{path=$f.FullName;size=$f.Length;sha256=$h;modified=$f.LastWriteTimeUtc.ToString('o');fileId=$id;locked=$locked}
}
foreach($place in @('inside','outside')) {
 foreach($variant in @('default','no-compact','upstream-commit','default-again')) {
  foreach($round in 1..3) {
   $name="matrix-$place-$variant-$round"
   $file=if($place -eq 'inside'){"$taskBundle/data/$name.mv.db"}else{"$taskExternal/$name.mv.db"}
   if(Test-Path -LiteralPath $file){throw "Refusing to replace previous evidence: $file"}
   Copy-Item -LiteralPath "$taskOld/backup/pre-cutover-v3.mv.db" -Destination $file
   $registry=Get-Content "$taskBundle/registry.json" -Raw | ConvertFrom-Json
   $registry.allowedFiles=@($registry.allowedFiles)+@($file)
   $registry | ConvertTo-Json -Depth 5 | Set-Content "$taskBundle/registry.json" -Encoding utf8
   $before=FileEvidence $file
   $cp=if($variant -eq 'upstream-commit'){"$taskBundle/tools/diagnostic-h2-patch;$taskCp"}else{$taskCp}
   $javaArgs=@("-Dprobe.registry=$taskBundle/registry.json")
   if($variant -eq 'no-compact'){$javaArgs+='-Dprobe.options=;AUTO_COMPACT_FILL_RATE=0'}
   $javaArgs+=@('-cp',$cp,'BareProbe','different-revision-fixture',$file,"$taskBundle/evidence/$name.json")
   $javaArgs | ConvertTo-Json | Set-Content "$taskBundle/evidence/$name.args.json"
   & $taskJava @javaArgs *> "$taskBundle/evidence/$name.log"
   $exitCode=$LASTEXITCODE
   $after=FileEvidence $file
   & $taskJava '-Dfile.encoding=UTF-8' -cp $taskCp LifetimeProbe read $file "$taskBundle/evidence/$name.external.json" "$taskBundle/registry.json" *> "$taskBundle/evidence/$name.external.log"
   if($LASTEXITCODE -ne 0){throw 'External readonly failed'}
   $external=Get-Content "$taskBundle/evidence/$name.external.json" -Raw | ConvertFrom-Json
   $observed=($external | Where-Object phase -eq 'readonly')
   $taskResults+=@{name=$name;variant=$variant;place=$place;exitCode=$exitCode;before=$before;after=$after;externalVersion=$observed.history[-1].version;externalPage65=($observed.representatives | Where-Object {$_.KIND -eq 'page' -and $_.ID -eq '65'});externalHashUnchanged=($after.sha256 -eq (Get-FileHash $file -Algorithm SHA256).Hash)}
   $taskResults | ConvertTo-Json -Depth 15 | Set-Content "$taskBundle/evidence/comparison-matrix.json" -Encoding utf8
   Write-Output "$name exit=$exitCode externalV=$($observed.history[-1].version)"
  }
 }
}
