# 5C-1 당시 전환 검토 기록 — 현재 실행 절차로 사용하지 않음

> 5C-1C 이후의 실제 실행 절차는 [최종 runbook](PHASE5C2_FINAL_RUNBOOK.md)이다. 이 문서 아래의 copy-promote 제안과 당시 STOP 항목은 과거 기록이며, 현재 단일 실행 경로는 승인된 대상에서 수행하는 one-shot migration이다. 실제 원본 전환 승인은 아직 주어지지 않았다.

> 5C-1B 갱신: H2 2.3.232 종료 압축/commit 순서 원인과 `AUTO_COMPACT_FILL_RATE=0` 사본 회피 검증을 [별도 보고서](PHASE5C1B_BLOCKER_RESOLUTION.md)에 기록했다. 아래는 이전 5C-1 당시의 검토 절차이며 그대로 실행하지 않는다. 원본 guard와 전환 보류는 유지된다. 회피 설정을 모든 writer에 고정한 승인 실행 구성 또는 정식 수정 버전의 별도 검증, 좁은 fail-closed guard, 사용자 재개 승인 전에는 5C-2를 실행하지 않는다.

작성일 2026-09-27. 이번에 실행한 것은 5C-1 사본 리허설뿐이다.

**현재 STOP:** 연결 수명 시험에서 값/이력 유지 실패가 재현됐고, 현재 V10 RC는 원본 경로를 의도적으로 차단한다. 원인 및 방지책 검증, 좁은 원본 전환 guard 설계, 운영 후보 사전, 전환 방식/환경에 대한 승인 전에는 아래 원본 교체 단계를 실행하지 않는다. `flyway.enabled=false`, 기존 checksum 변경, `repair`, 잠금 해제 또는 경로 위장으로 통과시키지 않는다.

## 전환 방식 추천

현재는 H2 파일 DB다. 쓰기를 완전히 중단한 시점의 V3를 복사해 별도 JVM에서 V4~V10·사전·baseline을 완료하고 **정상 종료/독립 검증된 V10 파일을 원본 경로로 승격**하는 방식을 추천한다. 원본 파일에서 부분 DDL을 수행하는 방식보다 실패 증거와 V3 기준점을 분리하기 쉽다.

이 역시 원본 DB 전환이다. 5C-2 승인이 필요하다. 이번에 만든 fixture DB를 승격하지 않으며, 이번 후보 baseline DB도 다음 전환 시점의 최신 원본을 대신하지 않는다. 아래 절차는 이 방식 기준이다. 인플레이스 migration이 필요하다면 원본용 단일 JVM migration 도구와 정확한 경로 제한을 별도로 검토해야 하며 이 runbook을 그대로 사용하지 않는다.

## 0. 시작 조건 — 하나라도 충족하지 않으면 STOP

- 5C-2를 실행하라는 사용자 승인과 작업 시간/쓰기 재개 책임자 확정.
- 연결 종료 후 이력/값 손실 원인 또는 검증된 회피책 확정. 같은 환경과 비동기 종료 조건을 포함한 별도 사본 재시험 결과 승인.
- 원본에 연결할 승인된 V10 JAR/profile/기동 인수 확정. 현재 `ClassificationMigrationConfiguration`은 local 및 `/.local-data/`를 막으므로 그대로는 사용할 수 없음. 원본 경로의 좁은 허용 변경을 준비하되 migration checksum과 업무 기능은 변경하지 않고 새 RC 사본 리허설을 다시 수행.
- 운영 분류 목록 승인(5종 등록형 타입, 기수 2개, REVIEW 3개/FAQ 7개 주제). 검증 fixture 포함 금지.
- DB 보관 경로, 백업 사본 위치, 프로세스 실행 주체 확정. OneDrive를 운영 저장소로 승인했다고 간주하지 않음.
- V3 원본과 대응 V3 JAR, 승인 V10 JAR, 소스/React/설정/도구/manifest 보관. 원본 V3가 더 변경됐으면 새 백업부터 시작.
- 서비스 공개 범위, 로그인 자격증명, bootstrap=false, preview=false 등 운영 설정 확인. 사본용 1234/1234·preview=true를 운영 기준으로 사용하지 않음.

다음 명령은 PowerShell에서 프로젝트 루트를 작업 디렉터리로 사용한다. 경로에 공백이 있으므로 항상 인수를 변수 또는 따옴표로 전달한다. 예시의 승인 JAR·기동 인수는 **아직 존재하지 않는 결정사항**이며 임의 값으로 대체하지 않는다.

```powershell
$ErrorActionPreference='Stop'
$taskRoot=(Get-Location).Path
$taskEvidence=(Get-Content .cache/phase5c1-current.txt -Raw).Trim()
$taskJava=Join-Path $taskRoot '.tools/jdk/jdk-17.0.20.1+1/bin/java.exe'
$taskJcmd=Join-Path $taskRoot '.tools/jdk/jdk-17.0.20.1+1/bin/jcmd.exe'
$taskDb=(Resolve-Path -LiteralPath '.local-data/aica-local.mv.db').Path
$taskStamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$taskWindow=Join-Path $taskRoot ".cache/phase5c1/cutover-$taskStamp"
New-Item -ItemType Directory -Path $taskWindow | Out-Null
# 아래 승인 파일은 5C-2 준비가 완료된 뒤 실제 값/해시로 생성한다. 지금은 없음.
$taskApprovalPath=Join-Path $taskRoot 'release-approved.json'
if(!(Test-Path -LiteralPath $taskApprovalPath)){throw 'STOP: 승인된 RC/전환 설정 없음'}
$taskApproved=Get-Content -LiteralPath $taskApprovalPath -Raw | ConvertFrom-Json
if(!$taskApproved.cutoverAuthorized -or !$taskApproved.persistenceIssueResolved){throw 'STOP: 전환/영속성 검증 미승인'}
if($taskApproved.originalDb -ne $taskDb){throw 'STOP: 승인 대상 경로 불일치'}
if((Get-FileHash -LiteralPath $taskApproved.v10Jar).Hash -ne $taskApproved.v10Sha256){throw 'STOP: V10 JAR 해시 불일치'}
if((Get-FileHash -LiteralPath "$taskEvidence/release/v3-runtime.jar").Hash -ne 'C20F0F4D92E1867FBDF326C30B67949CEEDF71AA3C0A2D597DC4432E1201CF6D'){throw 'STOP: V3 JAR 해시 불일치'}
```

`release-approved.json`에 필요한 값: cutoverAuthorized, persistenceIssueResolved, originalDb, v10Jar/v10Sha256, 승인 profile 및 원본/사본별 기동 인수 배열. 원본의 copy-validation 값을 true로 위장하는 설정은 승인하지 않는다. 승인 문서는 소스/운영 담당자가 실제 검증 후 작성해야 한다.

## 1. 쓰기 중단과 실행 프로세스 확인

운영자 편집 창을 닫고 접근/배치/IDE 자동 실행을 중단한다. 아직 전용 유지보수 모드가 없으므로 그것이 있다고 가정하지 않는다. 현재는 loopback 실행이며 실제 배포 시 접근 차단 수단을 먼저 확정한다.

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
 Select-Object ProcessId,ExecutablePath,CommandLine |
 ConvertTo-Json -Depth 4 | Set-Content "$taskWindow/processes-before.json"
Get-NetTCPConnection -State Listen | Select-Object LocalAddress,LocalPort,OwningProcess
# 기록에서 해당 DB와 대응 runtime을 쓰는 PID만 직접 확인한다.
$taskServerPid=[int](Read-Host '확인된 원본 서버 PID (실행 중인 경우)')
& $taskJcmd $taskServerPid VM.command_line
# 출력의 JAR, profile, datasource를 승인 정보와 대조한 뒤에만:
& $taskJava --add-modules jdk.attach -cp .cache GracefulStop $taskServerPid "$taskRoot/.cache/graceful-stop.jar"
if($LASTEXITCODE -ne 0){throw 'STOP: 정상 종료 요청 실패'}
```

PID가 없으면 종료 명령은 생략한다. `Stop-Process -Name java`처럼 다른 검증 서버까지 일괄 종료하지 않는다. Hikari shutdown complete 로그, 실제 PID 소멸을 기다린다. 응답 중인 작업이 남거나 종료 여부가 불명확하면 STOP. 강제 종료했다면 정상 콜드 백업으로 취급하지 않고 별도 복구/무결성 검사를 먼저 한다.

```powershell
if(Get-Process -Id $taskServerPid -ErrorAction SilentlyContinue){throw 'STOP: 서버 실행 중'}
$taskProbe=[IO.File]::Open($taskDb,'Open','Read','None')
$taskProbe.Dispose()
```

독점 읽기 실패 시 STOP. 단순히 `.lock.db`가 없다는 이유로 안전하다고 판단하지 않는다. 이후에도 재시작 주체를 차단해 점검과 복사 사이에 새 writer가 생기지 않게 한다.

## 2. 최신 콜드 백업, 실행 파일, 해시

```powershell
Copy-Item -LiteralPath $taskDb -Destination "$taskWindow/pre-cutover-v3.mv.db"
Copy-Item -LiteralPath "$taskEvidence/release/v3-runtime.jar" -Destination "$taskWindow/v3-runtime.jar"
Copy-Item -LiteralPath $taskApproved.v10Jar -Destination "$taskWindow/v10-approved.jar"
$taskBeforeHash=(Get-FileHash -LiteralPath $taskDb).Hash
if($taskBeforeHash -ne (Get-FileHash -LiteralPath "$taskWindow/pre-cutover-v3.mv.db").Hash){throw 'STOP: 콜드 백업 불일치'}
Get-FileHash -LiteralPath "$taskWindow/pre-cutover-v3.mv.db","$taskWindow/v3-runtime.jar","$taskWindow/v10-approved.jar" |
 ConvertTo-Json | Set-Content "$taskWindow/initial-checksums.json"
```

승인 RC의 소스/React/설정/migration 목록도 같은 묶음에 보관한다. H2 DB를 실행 중에 단순 복사하지 않는다. 이 단계 뒤 원본에 쓰기가 발생했다면 1단계부터 다시 시작한다.

## 3. 최종 migration 사본 시험

승인 RC에서 추출한 클래스/라이브러리로 classpath를 재구성한다. 5C-1의 `tools/classpath.txt`는 당시 RC 전용이며 승인 JAR가 달라졌다면 그대로 재사용하지 않는다. `CutoverAudit`와 `CandidateDictionary`는 workbench 소스를 컴파일해 사용한다.

```powershell
$taskCp=(Get-Content "$taskEvidence/tools/classpath.txt" -Raw).Trim() # 동일 RC인 경우만
# 새 RC이면 이 줄에서 STOP하고 해당 RC 추출물/도구로 classpath를 다시 기록한다.
Copy-Item -LiteralPath "$taskWindow/pre-cutover-v3.mv.db" -Destination "$taskWindow/trial-v10.mv.db"
& $taskJava -cp $taskCp CutoverAudit snapshot "$taskWindow/pre-cutover-v3.mv.db" "$taskWindow/before.json"
if($LASTEXITCODE -ne 0){throw 'STOP: 적용 전 fingerprint 실패'}
& $taskJava -cp $taskCp CutoverAudit migrate "$taskWindow/trial-v10.mv.db" "$taskWindow/trial-migration.json" *> "$taskWindow/trial-migration.log"
if($LASTEXITCODE -ne 0){throw 'STOP: 최종 사본 migration 실패'}
& $taskJava -cp $taskCp CutoverAudit snapshot "$taskWindow/trial-v10.mv.db" "$taskWindow/trial-independent.json"
if($LASTEXITCODE -ne 0){throw 'STOP: 독립 프로세스 조회 실패'}
```

V4~V10 순차 성공, checksum, 각 단계 validate/재적용 0, 기존 컬럼 보존, V8 메타데이터/ledger, 마지막 V10과 독립 snapshot 일치를 검사한다. exit code뿐 아니라 JSON의 `after`와 독립 snapshot도 같아야 한다. 실패하거나 이력이 이전 버전으로 보이면 증거를 보존하고 **repair/재적용으로 덮지 말고 STOP**.

이 사본에서 runtime 기능 smoke를 수행할 경우 fixture 데이터를 넣을 수 있지만, 그 사본은 승격 금지다. `.cache/phase5c1` 밖 DB는 도구가 거부한다.

## 4. 실제 승격 후보의 schema migration

최종 시험과 별도로, 동일한 최신 V3 콜드 백업에서 **새 승격 후보**를 만든다. 이 파일에는 fixture를 넣지 않는다. ID 생성이 무작위인 V8은 시험 사본의 ID와 일치할 필요가 없으며, 해당 후보 안의 안정성과 초안/발행본 관계가 맞아야 한다.

```powershell
Copy-Item -LiteralPath "$taskWindow/pre-cutover-v3.mv.db" -Destination "$taskWindow/promote-v10.mv.db"
& $taskJava -cp $taskCp CutoverAudit migrate "$taskWindow/promote-v10.mv.db" "$taskWindow/promote-migration.json" *> "$taskWindow/promote-migration.log"
if($LASTEXITCODE -ne 0){throw 'STOP: 승격 후보 migration 실패'}
& $taskJava -cp $taskCp CutoverAudit snapshot "$taskWindow/promote-v10.mv.db" "$taskWindow/promote-independent.json"
if($LASTEXITCODE -ne 0){throw 'STOP: 승격 후보 영속성 실패'}
```

3단계와 같은 내용 비교를 다시 수행한다. 원본 경로의 기존 V3 파일은 아직 그대로이며 어떤 V10 프로세스도 연결하지 않는다.

## 5. 별도 사전과 baseline

운영 사전 승인 목록과 `CandidateDictionary`가 다르면 실행하지 않는다. 등록 전에 후보 파일에 실행 중인 프로세스가 없어야 한다.

```powershell
& $taskJava -cp $taskCp CandidateDictionary "$taskWindow/promote-v10.mv.db" "$taskWindow/dictionary-first.json"
if($LASTEXITCODE -ne 0){throw 'STOP: 후보 사전 충돌/실패'}
& $taskJava -cp $taskCp CandidateDictionary "$taskWindow/promote-v10.mv.db" "$taskWindow/dictionary-repeat.json"
if($LASTEXITCODE -ne 0){throw 'STOP: 후보 사전 재실행 실패'}
```

두 JSON의 snapshot이 같아야 한다. 이름 충돌을 강제로 덮어쓰지 않는다. 기존 category 연결은 건드리지 않는다.

승인된 V10 JAR를 승격 후보 DB와 loopback 검증 포트에 연결한다. **DB 경로와 인수가 승인된 사본용 값인지 확인**하고 Start-Process는 Hidden으로 실행한다. 다음 `$taskCandidateArgs`에는 `-jar`, 승인 JAR, 사본 JDBC 절대 경로/IFEXISTS, 승인 profile, loopback 포트, bootstrap=false 등이 들어가야 한다. 승인 문서의 값으로 채우며 실행 중인 8096 서버가 있으면 해당 사본 서버만 정상 종료한다.

```powershell
$taskCandidateArgs=$taskApproved.candidateArguments
if(!$taskCandidateArgs){throw 'STOP: 사본 기동 인수 미승인'}
$taskProc=Start-Process -FilePath $taskJava -ArgumentList (($taskCandidateArgs|ForEach-Object{'"'+$_+'"'}) -join ' ') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$taskWindow/candidate.log" -RedirectStandardError "$taskWindow/candidate.err"
```

validate/migrate 0 및 datasource 경로를 로그에서 다시 확인한다. SUPER_ADMIN으로 로그인한 세션/CSRF로 baseline을 호출한다. 아래 API는 기존 기능이며 신규 migration에 합치지 않는다.

```powershell
$taskBase='http://127.0.0.1:8096' # 승인된 실제 격리 포트와 맞춰야 함
$taskLogin=Invoke-WebRequest "$taskBase/login" -SessionVariable taskSession
$taskToken=[regex]::Match($taskLogin.Content,'name="_csrf"[^>]*value="([^"]+)"').Groups[1].Value
$taskCredential=Get-Credential -Message '승인된 SUPER_ADMIN 계정'
$taskLoginForm=@{username=$taskCredential.UserName;password=$taskCredential.GetNetworkCredential().Password;_csrf=$taskToken}
Invoke-WebRequest "$taskBase/login" -WebSession $taskSession -Method Post -Body $taskLoginForm | Out-Null
$taskBootstrap=Invoke-RestMethod "$taskBase/api/admin/next/bootstrap" -WebSession $taskSession
if($taskBootstrap.user.role -ne 'SUPER_ADMIN'){throw 'STOP: baseline 권한 없음'}
$taskHeaders=@{}; $taskHeaders[$taskBootstrap.csrf.headerName]=$taskBootstrap.csrf.token
$taskBaseline=Invoke-RestMethod "$taskBase/api/admin/next/version-baseline" -WebSession $taskSession -Headers $taskHeaders -Method Post -ContentType 'application/json' -Body '{"confirmed":true}'
$taskBaseline | ConvertTo-Json | Set-Content "$taskWindow/baseline-first.json"
$taskRepeat=Invoke-RestMethod "$taskBase/api/admin/next/version-baseline" -WebSession $taskSession -Headers $taskHeaders -Method Post -ContentType 'application/json' -Body '{"confirmed":true}'
$taskRepeat | ConvertTo-Json | Set-Content "$taskWindow/baseline-repeat.json"
```

최초 건수는 전환 당일 원본 데이터 수로 판단한다. 이번 5C-1의 13건을 고정 기대값으로 사용하지 않는다. 두 번째는 모두 0이어야 한다. 원본 대상별 version ID/reason/source revision을 기록한다. 실패하면 STOP. 후보 서버를 정상 종료하고 콜드 fingerprint와 독립 조회를 다시 기록한다. runtime smoke fixture 스크립트는 승격 후보에서 실행하지 않는다.

## 6. 원본 경로 승격 — 여기부터 원본을 바꾸는 단계

현재 5C-1에서는 실행하지 않았다. 0단계의 승인과 모든 검사를 통과한 경우에만 진행한다. 아직 쓰기를 재개하지 않는다.

```powershell
if((Get-FileHash -LiteralPath $taskDb).Hash -ne $taskBeforeHash){throw 'STOP: 백업 이후 원본 변경'}
$taskProbe=[IO.File]::Open($taskDb,'Open','Read','None');$taskProbe.Dispose()
$taskCandidate=(Resolve-Path -LiteralPath "$taskWindow/promote-v10.mv.db").Path
$taskDbDir=Split-Path -Parent $taskDb
$taskStage=Join-Path $taskDbDir "aica-local.cutover-$taskStamp.mv.db"
$taskRetired=Join-Path $taskDbDir "aica-local.pre-cutover-$taskStamp.mv.db"
if((Split-Path -Parent ([IO.Path]::GetFullPath($taskStage))) -ne $taskDbDir){throw 'STOP: staging 경로 오류'}
if(Test-Path -LiteralPath $taskStage){throw 'STOP: staging 파일 존재'}
if(Test-Path -LiteralPath $taskRetired){throw 'STOP: 이전 원본 보관 파일 존재'}
Copy-Item -LiteralPath $taskCandidate -Destination $taskStage
if((Get-FileHash -LiteralPath $taskStage).Hash -ne (Get-FileHash -LiteralPath $taskCandidate).Hash){throw 'STOP: 승격 복사 불일치'}
# 재귀 이동/와일드카드 금지. 확인한 .mv.db 파일 하나만 이동.
Move-Item -LiteralPath $taskDb -Destination $taskRetired
Move-Item -LiteralPath $taskStage -Destination $taskDb
```

두 파일 이동을 단일 원자적 DB 전환이라고 주장하지 않는다. 중간 실패 시 서버를 시작하지 말고 9단계로 간다. 이전 V3 원본을 지우지 않는다. `.trace.db` 등 부가 파일이 있으면 특정 경로/해시로 별도 보존하며 다른 DB 파일을 묶어 이동하지 않는다.

## 7. 승인된 V10 runtime 실행과 smoke

```powershell
$taskOriginalArgs=$taskApproved.originalArguments
if(!$taskOriginalArgs){throw 'STOP: 원본 기동 인수 미승인'}
$taskLive=Start-Process -FilePath $taskJava -ArgumentList (($taskOriginalArgs|ForEach-Object{'"'+$_+'"'}) -join ' ') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$taskWindow/v10-live.log" -RedirectStandardError "$taskWindow/v10-live.err"
& $taskJcmd $taskLive.Id VM.command_line
```

명령의 JAR·실제 datasource·profile·port를 확인한다. V10/validate 성공/추가 migration 0이어야 한다. 원본 guard가 막으면 STOP이며 임의 해제하지 않는다.

읽기 검증: SUPER_ADMIN 로그인, 기존 콘텐츠와 page 1·65, 메뉴·계정 역할, 분류 사전, baseline 대상/count, 두 탐색 모드·block 딥링크, 공개 `/api/public/v1/menus`, `/pages/1`, `/pages/65`, 콘텐츠 공개 목록. 원본에 없는 발행 콘텐츠는 정상적으로 빈 목록/404일 수 있다. 보안상 비로그인 관리자 API는 거부되어야 한다.

쓰기/발행 경계·역할별 금지/허용·복구·템플릿·미디어·POSTS의 파괴적 fixture 검증은 최종 시험 사본에서 한다. **runtime_smoke.py/roles_smoke.py를 운영 DB에 실행하지 않는다.** 운영 데이터에 임의 공개 콘텐츠를 만들지 않는다. 원본 실제 편집 확인이 필요하면 대상/변경 내용을 별도 승인한다.

기동 후 정상 종료/재시작에서도 baseline/version/ID/발행본이 동일한지 최종 확인한다. 불일치, schema pending, 잠금 오류, 예상치 못한 데이터 감소면 쓰기를 열지 않고 rollback한다.

## 8. 쓰기 재개

모든 결과를 기록하고 작업 책임자가 승인한 뒤 차단을 해제한다. 전환 후 첫 저장부터 새 V10 데이터가 생기므로, 이후 V3 백업으로 rollback하면 그 변경을 잃는다. 쓰기 재개 시각과 그 이후 변경 회수 방식을 인수인계한다. 실제 공개 홈페이지가 아직 없다면 렌더링 E2E는 계속 5D 미완료다.

## 9. 실패 시 DB + runtime 동시 rollback

1. 쓰기 차단 유지. 정확한 V10 PID/DB 확인 → 정상 종료 → Hikari 종료/PID 소멸/독점 읽기 확인.
2. V10 실패 DB·로그·전체 Flyway 이력·설정·JAR 해시를 먼저 보존. 열리는 사본에서만 실패 snapshot을 추출. 실패 원본을 repair하지 않는다.
3. 적용 직전 V3 백업을 새 복원 파일로 복사, byte hash 확인. V10 파일을 명시적 보존 이름으로 이동하고 V3를 원본 경로로 복원.
4. **대응 V3 JAR**와 V3 설정으로 먼저 loopback 격리 포트 8097에서 실행. V10 JAR를 남긴 채 V3 DB로 기동하거나 V10 DB에 V3 JAR를 연결하지 않는다.
5. 로그인·콘텐츠·페이지·메뉴 확인 후 정상 종료. 콜드 사본 fingerprint를 적용 전 `before.json`과 비교. 동일해야 한다.
6. 실제 기존 서비스 포트/실행 파일 지정을 V3 쌍으로 되돌린 뒤 검수. V3는 이전 단계 권한/기능으로 돌아간다는 점도 확인하고 쓰기 재개를 판단한다.

파일 복원 명령 예시(종료 확인 및 절대 경로 검증 후에만):

```powershell
$taskFailed=Join-Path $taskDbDir "aica-local.failed-v10-$taskStamp.mv.db"
$taskRestore=Join-Path $taskDbDir "aica-local.restore-$taskStamp.mv.db"
if(Test-Path -LiteralPath $taskFailed){throw 'STOP: 실패 보관 이름 충돌'}
if(Test-Path -LiteralPath $taskRestore){throw 'STOP: 복원 staging 이름 충돌'}
Copy-Item -LiteralPath "$taskWindow/pre-cutover-v3.mv.db" -Destination $taskRestore
if((Get-FileHash -LiteralPath $taskRestore).Hash -ne $taskBeforeHash){throw 'STOP: V3 복원 해시 불일치'}
if(Test-Path -LiteralPath $taskDb){Move-Item -LiteralPath $taskDb -Destination $taskFailed}
Move-Item -LiteralPath $taskRestore -Destination $taskDb
# 이후 승인된 V3 기동 인수로 실행. V3 JAR 해시를 다시 확인한다.
```

DB 파일 복원만으로 끝내지 않는다. 5C-1에서는 V3 JAR 8097 기동·로그인·데이터 조회·정상 종료 후 기존 16개 테이블과 Flyway fingerprint 동일성까지 실제 확인했다. 원본 전환 때도 같은 검사를 생략하지 않는다.
