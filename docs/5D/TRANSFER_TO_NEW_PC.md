# V10 CMS — 새 Windows PC 이관 절차

작성 기준: 2026-09-28, V10 승인 RC 및 현재 저장소를 읽어 확인한 결과.

**현재 공식 절차: `relocate-plan → relocate-approve → serve`. 이미 V10인 DB를 새 절대 경로에서 읽기 전용으로 재검증하고 실행 자료를 생성한다. 승인 RC·React·기존 runtime guard는 그대로 사용한다.**

기존 `plan/run/rollback`은 V3 전환 전용이다. V10 이관에 사용하지 않는다. 기존 receipt/input/result를 텍스트 편집하거나 copy-validation·심볼릭 링크·경로 위장으로 검사를 통과시키지 않는다. 새 PC의 실제 도구 설치·파일 전달·계정 확인은 그 PC에서 완료해야 한다.

이관 도구는 별도 사본에서 검증했다. 집 PC 원본 V10 서버·DB, 애플리케이션 소스, migration, dependency 및 승인 RC는 변경하지 않았다. 회사 PC에서의 실제 이관은 아직 수행하지 않았다. 아래 명령은 **실제 이관 작업용 절차**다.

## 1. 준비물과 현재 기준

### 준비물 체크리스트

- [ ] 회사 PC에서 private GitHub 저장소에 접근할 권한
- [ ] JDK 17 전체 배포본과 Python, Git, PowerShell
- [ ] 아래에 정의한 최신 V10 DB/승인 RC/증거 묶음
- [ ] 별도 보안 채널로 전달받은 기존 DB·CMS 계정 정보
- [ ] 집 PC 쓰기 중단·정상 종료·복사 시간 합의
- [ ] 암호화한 이관 매체 또는 접근 통제된 파일 전달 경로
- [ ] 회사 PC의 기준본 보관 위치와 실행 사본 위치 결정
- [ ] 검증한 최신 이관 도구 5개 확보 및 C4 공식 재승인
- [ ] 기존 SUPER_ADMIN 로그인 UI 문제 처리 방침 — 현재 별도 미해결

### 확인한 소스와 실행물

| 항목 | 확인값 |
|---|---|
| 새 GitHub 저장소 | `https://github.com/parkseongji301-source/aica-cms-v10.git` (비공개) |
| 새 저장소 기본 브랜치 | `main` |
| 이관 도구 추가 전 소스 기준점 | `92a9ffa1d433708f0457a78e6581befa1518c92d` (clone HEAD 검사값이 아님) |
| 이관할 소스 commit | 저장소 게시 완료 보고의 전체 commit SHA를 별도 확인해 C1에 입력 |
| 집 PC 저장소 | `C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main` |
| 현재 DB | 위 저장소의 `.local-data/aica-local.mv.db` |
| 승인 release | `.cache/phase5c1c/20260927-223140/release-final` |
| 승인 JAR | release의 `v10-rc.jar` — 39,150,478 bytes |
| 승인 JAR SHA-256 | `606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e` |
| 현재 run-dir | `.cache/phase5c2-retry/20260927-233113/original-run` |
| 정상 서버 | `dev`, `127.0.0.1:8095`, validate-only, cutover 비활성 |
| 최근 실행 증거 | run-dir의 `serve-1790520102610346900/normal-runtime-process.json` 및 `.log` |

작성 시 기존 PID 9788의 존재와 `/login` HTTP 200을 확인했다. PID는 재시작 때 달라지므로 명령에 고정하지 않는다. 현재 startup log는 10개 migration validate 성공과 workaround 적용을 기록한다. 이번에는 실행 중 DB를 별도 H2 연결로 열거나 cold hash를 생성하지 않았다. **이관 기준 DB SHA-256은 실제 정상 종료 후 새로 구한다.** 과거 receipt의 `after.sha256`은 migration 직후 값이므로 최신 운영 DB hash와 같아야 하는 값이 아니다.

이번 확인에서 승인 release의 source manifest와 대응하는 애플리케이션·프런트 설정 198개 파일이 일치했다. Git commit은 문서 변경도 포함하므로 JAR의 동일성은 commit 이름이 아닌 위 SHA-256으로 판정한다.

## 2. Git으로 오는 것 / 별도로 옮길 것

### Git clone으로 확보되는 항목

| 항목 | 실제 위치·설명 |
|---|---|
| Java/Spring, Security, MyBatis 소스 | `src/main/java`, mapper XML, 테스트 |
| React/TypeScript 소스 | `frontend/src`, `frontend/tests`, `index.html` |
| 프런트 설정 | `package.json`, `pnpm-lock.yaml`, `vite.config.ts`, `tsconfig.json` |
| Spring 설정 | `application.yml`, `application-dev.yml` 및 보존된 과거 profile 파일 |
| Maven 설정 | `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/` |
| migration V1~V10 | SQL 9개와 Java migration V8. V8은 `src/main/java/db/migration/h2/V8__page_block_identity.java` |
| 실행·검증 scripts | `scripts/` — 존재한다고 모두 현재 V10 실행용은 아님 |
| 문서 | `docs/`, `docs/5D/`, README |

실행 가능한 `.env.example`은 현재 추적되지 않는다. `.gitignore`에 예외 규칙만 있다고 실제 파일이 있는 것은 아니다. Spring 설정 파일만 clone하고 바로 실행하면 dev 기본 상대 경로의 다른 DB를 가리킬 수 있다.

`.cache`, `.local-data`, `.tools`, `target`, `frontend/node_modules`, `src/main/resources/static/next-app`, `.env*`, H2 파일은 Git에서 제외된다. 새 저장소에는 이관 도구와 이 문서를 포함한다. C1에서 전달받은 commit을 확인하고 H4의 검증된 도구 묶음 hash도 확인한다. 이전 frozen release의 cutover.py에는 relocation 명령이 없다.

### 별도 이관 묶음: 최소 정상 실행 의존성과 검증 자료

다음은 파일을 보관할 **전달용 배치**다. C4에서 새 실행 경로에 대한 공식 승인 자료를 별도로 생성한다.

```text
V10-transfer-<실제 백업 시각>/
  db/aica-local.mv.db
  runtime/v10-rc.jar
  compatibility/v3-runtime.jar
  tools/cutover.py
  tools/http_client.py
  tools/relocation.py
  tools/RelocationInspect.java
  tools/GracefulStop.java
  tools/graceful-stop.jar
  approval/input.json
  approval/result.json
  approval/runtime-receipt.json
  evidence/bundle-manifest.json
  evidence/runtime-hashes.json
  evidence/source-manifest.json
  evidence/home-runtime-process.json
  evidence/home-runtime.log
  evidence/home-cold.json
  evidence/copied-cold.json
  evidence/git-state.txt
  evidence/git-diff.patch
  evidence/transfer-manifest.json
  docs/TRANSFER_TO_NEW_PC.md
```

| 파일 | 집 PC의 원본 | 필요한 이유 |
|---|---|---|
| `db/aica-local.mv.db` | `.local-data/aica-local.mv.db` | 전체 최신 V10 데이터. 정상 종료 후 복사 |
| `runtime/v10-rc.jar` | 승인 release | React·Spring·dependency·migration을 포함한 고정 실행물 |
| `compatibility/v3-runtime.jar` | 승인 release | **현 `serve`가 이 파일 hash도 검사하므로 실행 스크립트 의존성**. V10 DB에 실행할 파일은 아님 |
| `tools/cutover.py`, `relocation.py`, `RelocationInspect.java`, `GracefulStop.java`, `http_client.py` | 이번 검증을 마친 저장소 `scripts/cutover/` | 새 relocation 명령과 기존 serve 경로. 5개를 같은 묶음으로 전달 |
| `tools/graceful-stop.jar` | run-dir의 `shutdown-tool/graceful-stop.jar` | 정상 종료용 검증된 attach agent |
| `approval/` 3개 JSON | run-dir | 현재 대상·실행물·성공 상태·receipt 원문 증거. 내부 경로 수정 금지 |
| 3개 manifest/hash JSON | 승인 release | RC·도구·소스 대응 근거 |
| 현재 process/log, 새 cold 검사 | 최신 실행 및 백업 시 생성 | datasource, 종료, V10 이력, 데이터 fingerprint 확인 |
| Git 상태·이 문서 | 현재 저장소 | 소스 기준과 재현 절차 |

V3 JAR SHA-256: `c20f0f4d92e1867fbdf326c30b67949ceedf71aa3c0a2d597dc4432e1201cf6d`. compatibility 파일은 hash 검사에만 사용하며 V10 DB에 실행하지 않는다.

현재 shutdown agent SHA-256: `89768be097fce095034638b0aa206c5e268a67099f0bd39a34f037b10a448558`.

`bundle-manifest.json`은 과거 승인 release 15개 항목을 기록한다. RC/V3 JAR의 승인 hash 근거이며, 새 이관 도구의 hash는 아니다. 새 도구 5개는 `transfer-manifest.json`과 생성한 relocation plan에 각각 묶인다. 전달 manifest는 runtime receipt를 대신하지 않는다.

### 실행 필수와 분리할 자료

- **집 PC 복구 보관:** 이번 최신 V10 기준본과 동일 RC/receipt/설정. 회사 PC 실패 때문에 이 기준본을 덮어쓰지 않는다.
- **감사·과거 전환 복구 보관:** 5C의 V3 백업, `.spent` plan, 과거 acceptance/cold 검사·rollback 기록, source-snapshot.zip. 집 PC/보호된 백업에 남기되 회사 정상 실행 필수 파일로 분류하지 않는다.
- **불필요:** 과거 RC, fixture DB, smoke DB, 운영 후보 사전 seed, 오래된 사본, Maven/pnpm 캐시, `target`, `node_modules`, 과거 `.trace.db`·`.lock.db`.
- **미디어:** 현재 업로드 이미지·첨부 bytes는 `media.data` BLOB에 저장된다. DB 복사에 포함되며 별도 uploads 폴더는 현 구현에 없다. 홈페이지 디자인 자산은 JAR에 포함된다. 본문에 사용자가 넣은 외부 URL의 대상 파일은 DB가 보관해 주지 않는다.
- 세션/쿠키는 옮기지 않는다. 새 PC에서 다시 로그인한다. 비밀값은 파일 묶음/문서/Git/PowerShell 기록에 평문으로 넣지 않는다.

## 3. 필요한 프로그램·버전

일반 권장 버전이 아니라 현재 저장소·RC·이 PC에서 확인한 값이다. 설치 파일을 이번 작업에서 내려받거나 설치하지 않았다.

| 프로그램 | 승인 RC 실행만 | 이후 개발·빌드 | 근거 |
|---|---|---|---|
| JDK | **17**, 현재 Temurin `17.0.20.1+1` 재현 권장 | 동일 | pom enforcer `[17,18)`, RC Build-Jdk-Spec 17, 실제 Java 출력 |
| JDK 도구 | `java`, `javac`, `jar`, `jdk.attach` 필요 | 동일 | 읽기 전용 이관 검사 helper와 종료 agent만 로컬 컴파일. CMS/React 재빌드 아님 |
| Python | 현재 **3.12.14**, 표준 라이브러리만 | 동일 | frozen `cutover.py` / `http_client.py`; 별도 pip 설치 없음 |
| PowerShell | 현재 **7.6.5**에서 점검 | 동일 실행 환경 재현 | 아래 명령은 이 환경 기준; 다른 버전은 별도 확인 |
| Git for Windows | clone 시 필요, 현재 **2.52.0.windows.1** | 필요 | 현재 실행 출력. 프로젝트가 최소 버전을 고정한 것은 아님 |
| Maven | 불필요 | wrapper **3.9.11**, wrapper 자체 **3.3.2** | `.mvn/wrapper/maven-wrapper.properties`, enforcer Maven `[3.9,4)` |
| Node.js | 불필요 | 현재 **24.19.0** | 실제 설치값. Vite lockfile engine `^20.19.0 || >=22.12.0`; 테스트의 strip-types 지원도 필요 |
| pnpm | 불필요 | **11.19.0** | `packageManager`와 실제 설치 package 일치 |
| 별도 H2/Flyway 설치 | 불필요 | JAR/Maven dependency 사용 | RC 내 H2 **2.3.232**, Flyway **10.20.1**, Spring Boot **3.4.5** |

회사 PC에서 Codex가 반드시 필요한 것은 아니다. 집 PC Python/Node가 Codex 캐시 아래에 있다는 것은 현재 설치 위치일 뿐이다. 회사 PC에서는 동일 버전의 별도 설치 경로를 명시한다. private GitHub 인증은 회사가 승인한 credential manager/SSH 등으로 처리하고 URL에 token을 넣지 않는다.

## 4. 환경변수·profile·JVM option

| 이름/옵션 | 용도·설정 위치 |
|---|---|
| `JAVA_HOME` | 개발용 Maven/JDK 경로. frozen serve는 `--java` 절대 경로 사용 |
| `AICA_DB_USER`, `AICA_DB_PASSWORD` | CutoverTool/serve가 읽는 DB 자격증명. 실행할 PowerShell 프로세스에 보안 채널로 주입; 값은 기록하지 않음 |
| `SPRING_DATASOURCE_USERNAME/PASSWORD` | serve가 위 DB 변수로 자식 Java 환경을 구성함. 기존 별도 값에 의존하지 않음 |
| `--spring.profiles.active=dev` | frozen serve의 현재 정상 profile |
| `--spring.datasource.url=...` | frozen serve가 input의 정확한 DB 절대 경로로 생성 |
| `--AICA_RUNTIME_RECEIPT=...` | run-dir의 receipt 지정. C4가 실제 검사 결과로 새 자료 생성 |
| `--backoffice.bootstrap.enabled=false` | 서버 시작 시 계정·데이터 bootstrap 금지 |
| `--server.address=127.0.0.1`, `--server.port=8095` | 현재 loopback 실행. 인터넷 공개·회사망 공개 설정이 아님 |
| `BACKOFFICE_TIME_ZONE` | 현 기본 `Asia/Seoul`. 이관 중 임의 변경하지 않음 |
| `BACKOFFICE_POST_DRAFT_VERSIONS`, `BACKOFFICE_PAGE_DRAFT_VERSIONS`, `BACKOFFICE_TEMPLATE_VERSIONS` | 현 기본 20. 이관 중 보관 정책 변경하지 않음 |
| `AICA_CUTOVER_ENABLED`, `AICA_CUTOVER_APPROVAL_SHA256` | relocation에는 설정하지 않음. cutover true 또는 V3 approval 값이 있으면 새 도구가 STOP. 웹 서버도 cutover true 거부 |
| `AICA_SMOKE_USER/PASSWORD` | write smoke/cutover 검증용. 일반 `serve`에는 필요하지 않으며 이번 이관 조회 검사에도 쓰지 않음 |

JDBC 형식:

```text
jdbc:h2:file:<검증된 DB 절대 경로에서 .mv.db를 뺀 값>;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0
```

**H2 2.3.232의 파일 쓰기 연결에서 `AUTO_COMPACT_FILL_RATE=0`을 빼지 않는다.** `FileDatabaseSafety.requireWriter`는 위 세 옵션 외의 임의 옵션도 거부한다. INIT/AUTO_SERVER/AUTO_RECONNECT/임의 MODE를 추가하지 않는다. 독립 `inspect`는 같은 URL에 `ACCESS_MODE_DATA=r`을 붙인다. workaround가 있어도 이 연결은 읽기 전용이다.

`JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`, `LOADER_PATH`, `LOADER_HOME`, `LOADER_MAIN`에 값이 있으면 frozen script는 STOP한다. 새 PC의 `SPRING_*`, `BACKOFFICE_*`, `AICA_*`도 이름과 승인 여부를 확인한다. **환경변수 전체를 값과 함께 출력하지 않는다.** Hikari 별도 URL/connection-init override, 별도 Flyway datasource, `spring.sql.init.mode=always` 등은 금지한다.

`application-dev.yml`의 기본 상대 경로 `./.local-data/backoffice`는 현재 원본 파일명이 아니다. `local` profile과 `scripts/run-local.ps1`은 보존된 V3 경로다. 현재 V10에 사용하지 않는다. `.env`를 놓는다고 frozen Python/Java가 자동 로드하는 구조도 아니다.

## 5. 새 경로 승인: 공식 명령과 안전 검사

| 공식 경로 | 실제 하는 일 | V10 이관 용도 |
|---|---|---|
| RC `CutoverTool inspect` | read-only history/fingerprint/hash 기록 | 집 PC 기준본·회사 사본의 독립 cold 검사 |
| `cutover.py relocate-plan` | 실제 V10 사본을 read-only validate하고 전달 기준과 비교 | 24시간 유효한 V10 전용 plan 생성 |
| `cutover.py relocate-approve` | plan SHA와 명시적 승인 확인 후 파일을 다시 검사 | 일회성 승인 후 새 input/result/receipt 생성 |
| `cutover.py serve` | 새 실행 자료·최초 파일 hash 및 기존 RC guard 검사 | 정상 validate-only 실행. baseline/seed 없음 |
| 기존 `plan/run` | V3에서 V4~V10 전환 | 이관에는 사용 금지 |
| 기존 `rollback` | 전환 직전 V3 복원 | relocation run-dir에서는 차단 |

### 검사와 생성 자료

- [relocation.py](../../scripts/cutover/relocation.py): 절대 경로, 독점 접근, read lease, RC/V3/전달 증거 SHA, plan 유효기간·일회성 승인, 최초 실행 파일 hash 검사.
- [RelocationInspect.java](../../scripts/cutover/RelocationInspect.java): 변경하지 않은 RC의 `FileDatabaseSafety.requireV10`, `migrations`, `inspect` 호출. V1~V10/checksum/validate/pending 0과 전체 fingerprint 검사. 연결은 `ACCESS_MODE_DATA=r`만 사용.
- [FileDatabaseSafety](../../src/main/java/egovframework/backoffice/mvp/operations/FileDatabaseSafety.java), [FileRuntimeConfiguration](../../src/main/java/egovframework/backoffice/mvp/config/FileRuntimeConfiguration.java): 기존 소스·RC 그대로. 실제 DB 경로·JAR hash·workaround·cutover flag·V10 schema 검사 유지.
- [cutover.py](../../scripts/cutover/cutover.py): 새 명령을 별도 분기로 추가. 기존 V3 전환 명령을 relocation 승인으로 재해석하지 않음.

plan과 approve 각각에서 DB bytes가 검사 전후 같은지 확인한다. 검사 중 Windows share-read handle로 쓰기·삭제를 막는다. 전달받은 cold inspection·source receipt·release manifest의 hash를 명시적으로 전달하고, 그 내용과 실제 새 파일을 대조한다. source receipt만 복사하여 승인하지 않는다.

승인 결과에는 새 경로의 `input.json`, `result.json`, `runtime-receipt.json`, `relocation-plan.json`, `approval-inspection.json`, shutdown tool이 생성된다. `approvalKind=V10_RELOCATION`, `migrationPerformed=false`, `migrationsExecuted=0`, `baselineCreated=false`로 목적을 구분한다. RC 호환을 위한 receipt의 기존 `status=MIGRATED_V10`은 **schema가 V10이라는 기존 guard 계약**이며 새 migration을 실행했다는 뜻이 아니다.

### 재사용과 정상 운영

- plan은 SHA로 승인하며 24시간 유효하고, approve 시 `.spent`로 소모한다. 실패한 plan도 재사용하지 않는다.
- 새 자료는 DB 절대 경로·승인 당시 hash/fingerprint·RC hash·도구 hash에 묶인다. 첫 serve 직전 DB bytes가 달라지면 재검사·재승인해야 한다.
- 첫 정상 실행 후 도구가 `activation.json`을 기록한다. 이후에는 정상 업무 저장을 허용하기 위해 승인 시의 DB hash를 영구 고정하지 않는다. 매번 경로·RC·receipt·V10 validate/pending 0·workaround 검사는 유지한다.
- 이관 도구 5개를 승인 후 수정하면 기존 relocation 실행 자료로 serve할 수 없다. 검증한 도구 묶음을 보존한다.
- 승인 파일은 기존 cutover처럼 로컬 운영자의 승인 자료이며 전자서명 서비스가 아니다. 머신 관리자처럼 모든 파일을 수정할 수 있는 사람에 대한 방어까지 제공하지 않는다. 전달 manifest hash를 별도 신뢰 경로로 확인하고 승인 자료 접근 권한을 제한한다.

DB가 바뀐 뒤 다시 이관한다면 정지 후 최신 cold 기준을 새로 확보한다. 과거 hash에 맞추려고 DB를 되돌리지 않는다. V3 plan/run·copy-validation·guard disable·receipt 수동 편집은 계속 금지한다. 집 PC의 현재 frozen 실행 경로는 이번 도구 변경으로 바뀌지 않는다.

## 6. 집 PC에서 할 일 — 실제 복사할 날에만 실행

### H1. 변수·현재 대상·hash 확인

아래 경로는 집 PC의 실제 경로다. PowerShell에서 순서대로 실행한다. 현재 쓰기 작업 중인 사람이 없어야 한다.

```powershell
$ErrorActionPreference = 'Stop'
$repoPath = 'C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main'
$javaPath = Join-Path $repoPath '.tools/jdk/jdk-17.0.20.1+1/bin/java.exe'
$pythonPath = 'C:/Users/sfsf1/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$releasePath = Join-Path $repoPath '.cache/phase5c1c/20260927-223140/release-final'
$runPath = Join-Path $repoPath '.cache/phase5c2-retry/20260927-233113/original-run'
$dbPath = (Resolve-Path -LiteralPath (Join-Path $repoPath '.local-data/aica-local.mv.db')).Path
$rcPath = Join-Path $releasePath 'v10-rc.jar'
$stopAgent = Join-Path $runPath 'shutdown-tool/graceful-stop.jar'
$expectedRcSha = '606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e'
if ((Get-FileHash -LiteralPath $rcPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedRcSha) { throw 'STOP: RC mismatch' }
$receipt = Get-Content -LiteralPath (Join-Path $runPath 'runtime-receipt.json') -Raw | ConvertFrom-Json
if ($receipt.databasePath -cne $dbPath -or $receipt.jarSha256 -ne $expectedRcSha) { throw 'STOP: receipt mismatch' }
$serveEvidence = Get-ChildItem -LiteralPath $runPath -Directory |
  Where-Object Name -Like 'serve-*' | Sort-Object Name | Select-Object -Last 1
if (-not $serveEvidence) { throw 'STOP: runtime evidence missing' }
$processFile = Join-Path $serveEvidence.FullName 'normal-runtime-process.json'
$runtimeLog = Join-Path $serveEvidence.FullName 'normal-runtime.log'
$runtimeInfo = Get-Content -LiteralPath $processFile -Raw | ConvertFrom-Json
if ($runtimeInfo.database -cne $dbPath -or $runtimeInfo.jarSha256 -ne $expectedRcSha -or $runtimeInfo.cutoverFlag) { throw 'STOP: process metadata mismatch' }
$serverProcessId = [int]$runtimeInfo.pid
if ($runtimeInfo.profile -ne 'dev' -or $runtimeInfo.jdbcUrl -notmatch ';AUTO_COMPACT_FILL_RATE=0(?:;|$)') { throw 'STOP: profile/workaround mismatch' }
if (-not (Select-String -LiteralPath $runtimeLog -Pattern 'Successfully validated 10 migrations' -Quiet)) { throw 'STOP: validate log missing' }
if (-not (Select-String -LiteralPath $runtimeLog -Pattern 'migration=validate-only' -Quiet)) { throw 'STOP: normal runtime log missing' }
Select-String -LiteralPath $runtimeLog -Pattern 'Successfully validated 10 migrations','V10 file runtime','migration=validate-only'
```

실제 datasource URL은 위 metadata의 `jdbcUrl` 및 최신 log의 path/profile과 대조한다. `AUTO_COMPACT_FILL_RATE=0`이 없거나, 이미 종료된 오래된 로그뿐이면 진행하지 않는다. 로그 출력은 비밀값을 포함한 전체 환경/명령줄 출력으로 대체하지 않는다.

### H2. 정상 종료·PID·포트·Hikari·파일 잠금 확인

실행 중인 `serve` 콘솔이 있으면 Ctrl+C로 종료한다. wrapper가 GracefulStop 요청→exit code→독점 접근을 검사한다. 그 콘솔이 없다면 다음의 기존 agent를 사용한다. 두 종료 방식을 연속 실행하지 않는다.

```powershell
$serverProcessId = [int]$runtimeInfo.pid
$serverProcess = Get-Process -Id $serverProcessId -ErrorAction Stop
$listener = @(Get-NetTCPConnection -LocalPort 8095 -State Listen -ErrorAction Stop)
if ($listener.OwningProcess -notcontains $serverProcessId) { throw 'STOP: listener PID mismatch' }
if ($serverProcess.ProcessName -ne 'java') { throw 'STOP: unexpected process' }
$stopAgent = Join-Path $runPath 'shutdown-tool/graceful-stop.jar'
if ((Get-FileHash -LiteralPath $stopAgent).Hash.ToLowerInvariant() -ne '89768be097fce095034638b0aa206c5e268a67099f0bd39a34f037b10a448558') { throw 'STOP: shutdown tool mismatch' }
& $javaPath --add-modules jdk.attach -cp $stopAgent GracefulStop ([string]$serverProcessId) $stopAgent
if ($LASTEXITCODE -ne 0) { throw 'STOP: graceful stop request failed' }
```

어느 정상 종료 방법을 사용했든, H1 변수가 유지된 PowerShell에서 다음 공통 종료 확인을 수행한다. Ctrl+C로 wrapper가 끝난 경우에는 위 agent 요청을 다시 실행하지 않는다.

```powershell
Wait-Process -Id $serverProcessId -Timeout 60 -ErrorAction SilentlyContinue
if (Get-Process -Id $serverProcessId -ErrorAction SilentlyContinue) { throw 'STOP: process still running' }
if (Get-NetTCPConnection -LocalPort 8095 -State Listen -ErrorAction SilentlyContinue) { throw 'STOP: listener remains' }
if (-not (Select-String -LiteralPath $runtimeLog -Pattern 'HikariPool-.*Shutdown completed' -Quiet)) { throw 'STOP: Hikari shutdown not confirmed' }
$dbHandle = [IO.File]::Open($dbPath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
try { $dbFileLength = $dbHandle.Length } finally { $dbHandle.Dispose() }
```

PID 종료만으로 Hikari 종료나 DB 독점 접근 확인을 대신하지 않는다. 권한 부족·attach 실패·잔류 프로세스·lock 실패가 있으면 STOP한다. 강제 종료나 lock 파일 삭제로 진행하지 않는다. 독점 검사 이후에도 다른 서버/테스트/H2 Console/동기화 복사를 시작하지 않는다. 이미 정지했다면 무관한 PID에 agent를 실행하지 말고, 종료한 대상의 증거를 확인한 뒤 Hikari/포트/독점 검사를 수행한다.

### H3. 새 증거 폴더와 read-only 검사

다음은 기존 RC의 `inspect`를 호출하며 DB 내용은 변경하지 않는다. 보관 위치는 OneDrive 밖의 예시다. 공유·외부 전달은 승인된 방법으로 수행한다.

```powershell
$transferRoot = Join-Path $env:USERPROFILE ('AICA-transfer/V10-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $transferRoot) { throw 'STOP: choose a new transfer directory' }
New-Item -ItemType Directory -Path $transferRoot | Out-Null
foreach ($folder in @('db','runtime','compatibility','tools','approval','evidence','docs')) {
  New-Item -ItemType Directory -Path (Join-Path $transferRoot $folder) | Out-Null
}
$homeInspection = Join-Path $transferRoot 'evidence/home-cold.json'
$inspectArgs = @(
  '-Dfile.encoding=UTF-8',
  '-Dloader.main=egovframework.backoffice.mvp.operations.CutoverTool',
  '-cp', $rcPath,
  'org.springframework.boot.loader.launch.PropertiesLauncher',
  'inspect', $dbPath, $homeInspection
)
& $javaPath @inspectArgs
if ($LASTEXITCODE -ne 0) { throw 'STOP: cold inspection failed' }
$cold = Get-Content -LiteralPath $homeInspection -Raw | ConvertFrom-Json
if ($cold.path -cne $dbPath -or $cold.jarSha256 -ne $expectedRcSha) { throw 'STOP: inspected target differs' }
if ((@($cold.history | ForEach-Object { [string]$_.version }) -join ',') -ne '1,2,3,4,5,6,7,8,9,10') { throw 'STOP: not V10' }
if (($cold.history | ConvertTo-Json -Depth 15 -Compress) -cne ($receipt.migrations | ConvertTo-Json -Depth 15 -Compress)) { throw 'STOP: migration manifest differs' }
if ((Get-FileHash -LiteralPath $dbPath).Hash.ToLowerInvariant() -ne $cold.sha256) { throw 'STOP: DB changed after inspection' }
```

JVM option → `-cp` → main class → 프로그램 인수 순서다. `-D...`를 인용된 배열 요소로 전달해 과거 Java 인수 작성 오류를 피한다. `inspect`는 출력 덮어쓰기를 거부하므로 재검사할 때는 새 출력 이름을 사용한다. V1~V10의 성공 이력·checksum을 최신 시작 시 validate 로그와 함께 기록한다. inspect 자체는 Flyway validate를 실행하는 명령이 아니다.

### H4. 정지된 DB와 필요한 파일만 복사

```powershell
$copyMap = [ordered]@{
  'db/aica-local.mv.db' = $dbPath
  'runtime/v10-rc.jar' = $rcPath
  'compatibility/v3-runtime.jar' = (Join-Path $releasePath 'v3-runtime.jar')
  'tools/cutover.py' = (Join-Path $repoPath 'scripts/cutover/cutover.py')
  'tools/http_client.py' = (Join-Path $repoPath 'scripts/cutover/http_client.py')
  'tools/relocation.py' = (Join-Path $repoPath 'scripts/cutover/relocation.py')
  'tools/RelocationInspect.java' = (Join-Path $repoPath 'scripts/cutover/RelocationInspect.java')
  'tools/GracefulStop.java' = (Join-Path $repoPath 'scripts/cutover/GracefulStop.java')
  'tools/graceful-stop.jar' = $stopAgent
  'approval/input.json' = (Join-Path $runPath 'input.json')
  'approval/result.json' = (Join-Path $runPath 'result.json')
  'approval/runtime-receipt.json' = (Join-Path $runPath 'runtime-receipt.json')
  'evidence/bundle-manifest.json' = (Join-Path $releasePath 'bundle-manifest.json')
  'evidence/runtime-hashes.json' = (Join-Path $releasePath 'runtime-hashes.json')
  'evidence/source-manifest.json' = (Join-Path $releasePath 'source-manifest.json')
  'evidence/home-runtime-process.json' = $processFile
  'evidence/home-runtime.log' = $runtimeLog
  'docs/TRANSFER_TO_NEW_PC.md' = (Join-Path $repoPath 'docs/5D/TRANSFER_TO_NEW_PC.md')
}
foreach ($item in $copyMap.GetEnumerator()) {
  $destination = Join-Path $transferRoot $item.Key
  if (Test-Path -LiteralPath $destination) { throw 'STOP: destination already exists' }
  Copy-Item -LiteralPath $item.Value -Destination $destination
  if ((Get-FileHash -LiteralPath $item.Value).Hash -ne (Get-FileHash -LiteralPath $destination).Hash) { throw 'STOP: copy hash mismatch' }
}
$copiedDb = Join-Path $transferRoot 'db/aica-local.mv.db'
$copyInspection = Join-Path $transferRoot 'evidence/copied-cold.json'
$inspectArgs[-2] = $copiedDb
$inspectArgs[-1] = $copyInspection
& $javaPath @inspectArgs
if ($LASTEXITCODE -ne 0) { throw 'STOP: copied DB inspection failed' }
$copied = Get-Content -LiteralPath $copyInspection -Raw | ConvertFrom-Json
if ($copied.sha256 -ne $cold.sha256) { throw 'STOP: DB bytes differ' }
foreach ($field in @('history','columns','fingerprints')) {
  if (($cold.$field | ConvertTo-Json -Depth 30 -Compress) -cne ($copied.$field | ConvertTo-Json -Depth 30 -Compress)) { throw "STOP: $field differs" }
}
git -C $repoPath rev-parse HEAD | Set-Content -LiteralPath (Join-Path $transferRoot 'evidence/git-state.txt') -Encoding utf8
if ($LASTEXITCODE -ne 0) { throw 'STOP: git revision failed' }
git -C $repoPath status --short | Add-Content -LiteralPath (Join-Path $transferRoot 'evidence/git-state.txt') -Encoding utf8
git -C $repoPath diff --binary | Set-Content -LiteralPath (Join-Path $transferRoot 'evidence/git-diff.patch') -Encoding utf8
$transferManifest = [ordered]@{}
Get-ChildItem -LiteralPath $transferRoot -File -Recurse | ForEach-Object {
  $relative = $_.FullName.Substring($transferRoot.Length + 1).Replace('\','/')
  $transferManifest[$relative] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
}
$transferManifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $transferRoot 'evidence/transfer-manifest.json') -Encoding utf8
Get-FileHash -LiteralPath (Join-Path $transferRoot 'evidence/transfer-manifest.json') -Algorithm SHA256
```

manifest 자체의 hash는 별도의 신뢰할 수 있는 경로로 전달한다. manifest와 파일을 함께 바꿀 수 있는 상황에서는 hash만으로 발신자를 인증할 수 없다. 이번에 승인한 이관 도구/검증 스크립트/문서 변경 외에 Git 차이가 있으면 STOP해 확인한다. 미추적 파일은 diff에 들어가지 않으므로 status도 확인한다. 실행에 필요한 새 도구는 H4의 copyMap에 포함되어 있으며 별도 전달 hash로 검증한다. DB·자격 증명·과거 fixture를 Git에 추가하지 않는다.

**집 PC 재개:** 복사 후에도 집 PC 기준본은 그대로 남긴다. 집/회사 양쪽에서 독립적으로 업무 쓰기를 재개하면 자동 병합되지 않는다. 어느 PC가 기준인지 정한다. 집 PC를 재시작할 필요가 있을 때만 기존 승인 경로를 그대로 사용한다.

```powershell
& $pythonPath -X utf8 -B (Join-Path $releasePath 'cutover/cutover.py') serve `
  --java $javaPath --run-dir $runPath --port 8095
```

이 명령은 **집 PC의 기존 승인 경로 재시작용**이다. 회사 PC의 새 경로 승인 명령이 아니다.

## 7. 회사 PC에서 할 일

### C1. 소스 확보·버전 확인

다음 `D:/AICA/...`는 회사 담당자가 선택할 예시 위치다. 실제 존재하는 로컬 디스크의 절대 경로로 바꾸되 receipt/input은 수정하지 않는다. 동기화 폴더와 네트워크 드라이브를 실행 DB 위치로 선택하지 않는 방향으로 별도 확정한다.

```powershell
$ErrorActionPreference = 'Stop'
$companyRepo = 'D:/AICA/source'
git --version
git clone 'https://github.com/parkseongji301-source/aica-cms-v10.git' $companyRepo
if ($LASTEXITCODE -ne 0) { throw 'STOP: clone failed' }
$sourceCommit = (git -C $companyRepo rev-parse HEAD).Trim()
$approvedSourceCommit = (Read-Host '게시 완료 보고에서 확인한 전체 commit SHA 40자').Trim()
if ($approvedSourceCommit -notmatch '^[0-9a-f]{40}$' -or $sourceCommit -ne $approvedSourceCommit) { throw 'STOP: source revision needs review' }
git -C $companyRepo status --short
```

이미 clone한 경우 작업물이 없는지 확인하고 `git -C $companyRepo pull --ff-only` 후 같은 검사를 한다. 승인 commit은 현재 HEAD 값을 그대로 복사하지 말고 전달 담당자의 게시 완료 보고에서 확인한다. 이후 새 commit은 담당자가 차이를 확인한 뒤 기준 commit을 갱신한다. 자동으로 앱 변경까지 허용하지 않는다. C4/C5에서 사용하는 전달 묶음의 새 도구 5개를 과거 frozen release의 cutover.py와 섞지 않는다.

```powershell
$javaPath = 'D:/AICA/tools/jdk-17.0.20.1+1/bin/java.exe'
$pythonPath = 'D:/AICA/tools/python312/python.exe'
& $javaPath -version
& $javaPath --list-modules | Select-String 'jdk.attach'
& $pythonPath --version
Get-ChildItem Env: | Where-Object Name -Match '^(JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS|_JAVA_OPTIONS|LOADER_|SPRING_|BACKOFFICE_|AICA_)' | Select-Object Name
```

Java/Python 경로는 실제 설치 위치로 바꾼다. 존재하지 않는 예시 경로로 진행하지 않는다. JDK 17 전체와 Python 3.12.14를 준비하고, 미승인 환경 override가 있으면 값을 노출하지 않은 채 담당자에게 확인한다. 환경변수 삭제를 guard 우회 수단으로 자동 수행하지 않는다.

### C2. 전달받은 기준본 검사

```powershell
$receivedRoot = 'D:/AICA/received/V10-YYYYMMDD-HHMMSS'
$manifestFile = Join-Path $receivedRoot 'evidence/transfer-manifest.json'
# 이 출력을 별도 경로로 전달받은 manifest SHA-256과 대조한다.
Get-FileHash -LiteralPath $manifestFile -Algorithm SHA256
$manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
foreach ($entry in $manifest.PSObject.Properties) {
  $file = Join-Path $receivedRoot $entry.Name
  if (-not (Test-Path -LiteralPath $file)) { throw 'STOP: missing transferred file' }
  if ((Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant() -ne $entry.Value) { throw 'STOP: transferred hash mismatch' }
}
$rcPath = Join-Path $receivedRoot 'runtime/v10-rc.jar'
if ((Get-FileHash -LiteralPath $rcPath).Hash.ToLowerInvariant() -ne '606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e') { throw 'STOP: unapproved RC' }
```

이 기준본을 애플리케이션의 쓰기 대상으로 사용하지 않는다. 백업을 보관하면서 실행할 DB 사본을 새로 만든다. C4에서 승인할 경로는 실제 계속 사용할 경로여야 한다. 승인 후 폴더를 옮기면 다시 승인해야 한다.

### C3. 새 경로 사본의 공식 read-only 검사

```powershell
$companyWork = 'D:/AICA/validation/V10-YYYYMMDD-HHMMSS'
if (Test-Path -LiteralPath $companyWork) { throw 'STOP: validation directory must be new' }
New-Item -ItemType Directory -Path $companyWork | Out-Null
$companyDb = Join-Path $companyWork 'aica-local.mv.db'
Copy-Item -LiteralPath (Join-Path $receivedRoot 'db/aica-local.mv.db') -Destination $companyDb
$companyDb = (Resolve-Path -LiteralPath $companyDb).Path
$companyInspection = Join-Path $companyWork 'company-cold.json'
$dbHandle = [IO.File]::Open($companyDb, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
$dbHandle.Dispose()
$inspectArgs = @(
  '-Dfile.encoding=UTF-8',
  '-Dloader.main=egovframework.backoffice.mvp.operations.CutoverTool',
  '-cp', $rcPath,
  'org.springframework.boot.loader.launch.PropertiesLauncher',
  'inspect', $companyDb, $companyInspection
)
& $javaPath @inspectArgs
if ($LASTEXITCODE -ne 0) { throw 'STOP: company read-only inspection failed' }
$expected = Get-Content -LiteralPath (Join-Path $receivedRoot 'evidence/home-cold.json') -Raw | ConvertFrom-Json
$actual = Get-Content -LiteralPath $companyInspection -Raw | ConvertFrom-Json
if ($actual.sha256 -ne $expected.sha256) { throw 'STOP: copied DB bytes differ' }
foreach ($field in @('history','columns','fingerprints')) {
  if (($expected.$field | ConvertTo-Json -Depth 30 -Compress) -cne ($actual.$field | ConvertTo-Json -Depth 30 -Compress)) { throw "STOP: $field differs" }
}
```

`path`, PID, 검사 시각은 PC마다 다르다. history/columns/fingerprints/DB hash는 전달 기준과 같아야 한다. 전달받은 기존 DB 자격 증명으로 접속하고, 새 DB나 계정을 초기화하지 않는다.

### C4. 새 절대 경로 공식 승인

전달 manifest를 이미 신뢰 경로로 대조했고 C3의 DB hash/history/fingerprint가 일치해야 한다. 새 명령은 **전달한 최신 도구**를 사용하며 과거 frozen cutover.py를 사용하지 않는다. 아래에서 전달 기준본은 읽기만 하고, 새로운 실행 자료는 `$companyWork`에 생성한다.

```powershell
$relocationTool = Join-Path $receivedRoot 'tools/cutover.py'
$sourceCold = Join-Path $receivedRoot 'evidence/home-cold.json'
$sourceReceipt = Join-Path $receivedRoot 'approval/runtime-receipt.json'
$releaseManifest = Join-Path $receivedRoot 'evidence/bundle-manifest.json'
$v3Runtime = Join-Path $receivedRoot 'compatibility/v3-runtime.jar'
$relocationPlan = Join-Path $companyWork 'relocation-plan.json'
$companyRun = Join-Path $companyWork 'run'
$planArgs = @(
  '-X', 'utf8', '-B', $relocationTool, 'relocate-plan',
  '--java', $javaPath,
  '--rc', $rcPath,
  '--rc-sha256', '606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e',
  '--v3-runtime', $v3Runtime,
  '--v3-sha256', 'c20f0f4d92e1867fbdf326c30b67949ceedf71aa3c0a2d597dc4432e1201cf6d',
  '--db', $companyDb,
  '--source-inspection', $sourceCold,
  '--source-inspection-sha256', $manifest.'evidence/home-cold.json',
  '--source-receipt', $sourceReceipt,
  '--source-receipt-sha256', $manifest.'approval/runtime-receipt.json',
  '--release-manifest', $releaseManifest,
  '--release-manifest-sha256', $manifest.'evidence/bundle-manifest.json',
  '--output', $relocationPlan
)
& $pythonPath @planArgs
if ($LASTEXITCODE -ne 0) { throw 'STOP: V10 relocation plan failed' }
$plan = Get-Content -LiteralPath $relocationPlan -Raw | ConvertFrom-Json
$plan | Select-Object approvalKind,databasePath,rc,rcSha256,v3,v3Sha256,jdbcUrl,expiresAt
$plan.database | Select-Object sha256,validated,pending,migrationsExecuted,bytesUnchanged
$plan.migrations | Format-Table version,script,checksum
$planSha = (Get-FileHash -LiteralPath $relocationPlan -Algorithm SHA256).Hash.ToLowerInvariant()
$planSha
```

담당자가 새 절대 경로·RC·hash·V1~V10·pending 0·읽기 전용 결과를 확인한 후 아래 명시적 승인을 실행한다. 조건이 다르면 STOP한다. source receipt와 새 receipt를 편집하지 않는다.

```powershell
& $pythonPath -X utf8 -B $relocationTool relocate-approve `
  --java $javaPath --plan $relocationPlan --approved-plan-sha $planSha `
  --run-dir $companyRun --authorize-relocation
if ($LASTEXITCODE -ne 0) { throw 'STOP: relocation approval failed; do not reuse spent plan' }
$approvalResult = Get-Content -LiteralPath (Join-Path $companyRun 'result.json') -Raw | ConvertFrom-Json
if (-not $approvalResult.success -or -not $approvalResult.databaseBytesUnchanged -or $approvalResult.migrationsExecuted -ne 0) { throw 'STOP: invalid approval result' }
```

`$companyRun`에는 새 경로와 실제 검사 결과에서 생성한 input/result/receipt가 들어간다. 집 PC에서 가져온 `approval/` 원문은 출처 증거로만 보관한다. `.spent` plan은 재사용하지 않는다. 실패 원인을 해결한 뒤 새 plan/output/run-dir로 처음부터 승인한다.

### C5. 정상 serve, 종료, 재시작

```powershell
# 이 콘솔은 서버 실행 동안 유지한다. 별도 PowerShell/브라우저에서 C6 검사를 한다.
& $pythonPath -X utf8 -B $relocationTool serve `
  --java $javaPath --run-dir $companyRun --port 8095
```

첫 serve는 승인 당시 DB hash까지 검사하고 성공 시 activation.json을 만든다. 이후 정상 업무 변경 때문에 DB hash가 달라져도 기존 guard가 유지되는 한 재시작할 수 있다. DB·RC·run-dir·도구 묶음의 위치/내용을 임의로 바꾸지 않는다.

정상 종료는 위 콘솔에서 **Ctrl+C**를 사용한다. wrapper가 agent 종료·exit 0·lock 해제를 검사한다. 콘솔을 잃었다면 H2의 같은 agent 종료 절차를 사용하되, `$runPath=$companyRun`, 최신 company `serve-*` metadata/로그와 실제 PID를 사용한다. 집 PC PID나 옛 shutdown-tool 경로를 사용하지 않는다.

종료 후 H2의 공통 종료 확인과 C3의 독립 inspect를 반복한다. 출력 파일은 `company-after-stop-1.json`처럼 새 이름으로 지정한다. history/columns/fingerprints가 첫 cold와 같아야 한다. H2가 정상 쓰기 연결을 열고 닫으며 파일 배치를 변경할 수 있으므로 **서버 실행 후에는 최초 bytes hash가 아닌 전체 테이블 fingerprint로 내용 보존을 비교**한다. inspect 자체의 전후 bytes는 같아야 한다.

동일한 C5 serve 명령으로 재시작하고 C6 조회를 반복한다. 두 번째 정상 종료/cold 검사까지 통과하면, 계속 사용할 경우 같은 명령으로 다시 실행한다. 조회 시험 중 업무 저장·baseline·복구는 수행하지 않는다.

## 8. C6. 성공 확인 조건

업무 데이터를 바꾸지 않고 GET·로그·cold fingerprint로 확인한다. 자동저장을 유발하는 입력, 수동 저장, 발행, baseline 생성, version 복구, fixture 입력은 하지 않는다.

| 검사 | 합격 조건 |
|---|---|
| DB/runtime | 새 공식 승인 자료와 실제 파일/RC SHA 일치 |
| Flyway | V1~V10 validate 성공, pending 0, 추가 migration 0 |
| 정상 모드 | cutover 비활성, copy-validation 예외 미사용 |
| H2 | 실제 datasource 절대 경로, `AUTO_COMPACT_FILL_RATE=0`, 나머지 허용 옵션 일치 |
| 로그인 | 기존 계정으로 일반 브라우저 로그인 |
| React | `/admin-next?view=manage`、`?view=structure` |
| 동일 원본 | page 1/65, content ID, block ID가 집 PC 기준과 같음 |
| 버전 이력 | 목록과 과거 snapshot 조회. 복구는 실행하지 않음 |
| 공개 API | menus/pages/slug/posts 익명 GET 후 집 PC 발행 데이터와 비교 |
| 종료 | 정상 종료, PID/listener 종료, Hikari 종료, file lock 해제 |
| cold | history/columns/fingerprints가 회사 PC 시작 전과 일치 |
| 재시작 | 다시 validate-only로 동일 ID·원본·공개 결과 조회 |

현재 비교 후보: page `[1,65]`, post `[1,2,3,4,5,6,7,8,33]`, GENERAL/모두 초안, cohort/topic 0개, template/media 0개. page 65 HERO는 `block_44c17a08-32c1-4bdd-a42c-a4266a9ad033`이다. **실제 기준은 이관 당일 cold fingerprint와 승인된 데이터다. 이 목록과 맞추려고 DB를 바꾸지 않는다.**

```powershell
# C5 정상 서버 실행 후 사용. 모두 익명 GET.
$baseUrl = 'http://127.0.0.1:8095'
Invoke-RestMethod "$baseUrl/api/public/v1/menus"
Invoke-RestMethod "$baseUrl/api/public/v1/pages/1"
Invoke-RestMethod "$baseUrl/api/public/v1/pages/65"
Invoke-RestMethod "$baseUrl/api/public/v1/pages/by-slug/about"
Invoke-RestMethod "$baseUrl/api/public/v1/posts"
```

관리 화면은 브라우저에서 확인한다. 현재 원본에 SUPPORTER 계정이 없으므로 검사용으로 임의 생성하지 않는다. 숫자형 SUPER_ADMIN ID가 email 입력 검사에 막히는 기존 문제는 회사 PC에서도 저절로 해결되지 않는다. HTML 수정·다른 profile·인증 우회를 절차로 삼지 않는다. 기존 ADMIN으로 확인 가능한 범위와 SUPER_ADMIN 미해결 범위를 구분하고, 전체 합격으로 처리하지 않는다.

업무 조회만 하더라도 정상 서버의 H2 연결·종료 때문에 파일 hash/mtime이 달라질 수 있다. **복사 직후에는 bytes hash 완전 일치, 서버 실행 후에는 V10 history·전체 테이블 fingerprint 일치**를 주 기준으로 삼는다. 실행 후 새 cold hash도 기록하되, 이전과 hash가 다르다는 이유만으로 논리 데이터 손실이라고 단정하지 않는다. 독립 read-only `inspect` 자체는 실행 전후 bytes 동일성을 검사한다.

## 9. 실패 시 복구

1. 회사 PC에서 실패하면 업무 쓰기를 시작하지 않는다. 오류·대상 경로·RC hash·로그를 남긴다.
2. 서버가 실행됐다면 기존 정상 종료 수단으로 멈추고 PID/포트/Hikari/lock을 확인한다. 종료 실패 상태에서 DB를 덮어쓰지 않는다.
3. 실패한 회사 PC 실행 사본과 로그를 보관한다. 집 PC 원본·전달 기준본·receipt 원문은 변경하지 않는다.
4. 재시도는 전달받은 최신 V10 기준본에서 **별도의 새 검증 폴더**로 복사해 시작한다. 실패 사본은 조사 완료 후 폐기할 수 있다. 이 문서는 재귀 삭제 명령을 제공하지 않는다.
5. 승인 실패가 있었다면 새로운 plan/output/run-dir를 사용해 C3~C4를 반복한다. 소모된 승인이나 수동 변경한 자료로 실행하지 않는다.
6. 집 PC에는 원래 V10 DB/RC/receipt가 남아 있다. 필요하면 집 PC의 기존 serve 경로로 재개한다. 회사 쪽 변경을 집 쪽으로 자동 역동기화하지 않는다.

**`cutover.py rollback`을 사용하지 않는다.** 과거 V3 전환 rollback 전용이며 회사 PC의 최신 V10 시험 실패를 복구하는 명령이 아니다. V10 DB에 V3 runtime을 연결하지 않는다. 이번 복구 기준은 새로 확보한 V10 백업이다.

## 10. React 재빌드 필요 여부

### A. 현재 V10을 그대로 실행

**불필요하다.** RC를 ZIP으로 읽어 다음 파일이 포함된 것을 확인했다.

| RC 내부 경로 (`BOOT-INF/classes/static/next-app/` 아래) | SHA-256 |
|---|---|
| `index.html` | `4b44e47853ff9da780f84fe40565e167569899954eb80737f0f75221adba7b72` |
| `assets/index-CoqNfsMs.js` | `3903dc606d8f8e3ed5d8e949947b29baf6986a8de05de38304c63acddc52e263` |
| `assets/index-D03pTr5T.css` | `18a996f26143e8432970359a467d8480ba1c69e450653c1c025f723142d186f0` |

이 3개 파일은 현재 작업 폴더의 build 출력과도 일치했다. 별도 Vite 서버나 pnpm install은 필요 없다. 승인 JAR 하나에서 Spring/React/API를 제공한다. V1~V7/V9/V10 SQL과 V8 Java class, H2/Flyway dependency도 JAR 안에 있다.

### B. 이후 React/Java 개발 재개

개발 재개가 별도로 승인되면 Git 소스와 lockfile로 다시 생성할 수 있다. 아래는 향후 개발용이며 이번 이관 실행 순서에 포함하지 않는다.

```powershell
# 개발 재개 승인 후, 소스 repository root에서 실행하는 예시.
$env:JAVA_HOME = 'D:/AICA/tools/jdk-17.0.20.1+1'
node --version
pnpm.cmd --version
.\mvnw.cmd --version
.\scripts\build-admin-next.ps1
```

build script는 `pnpm install --frozen-lockfile`, TypeScript/Vite build, Maven `-Pegov43-probe clean verify`를 수행한다. Maven wrapper distribution과 dependency는 재다운로드 가능하다. 새 build는 checksum이 다른 새로운 검증 대상이며 현재 RC/receipt 대신 원본에 넣지 않는다. Maven만 실행하면 React build가 자동 수행되지 않는다. Vite 개발 proxy는 현재 8081이므로 8095 실행 환경에 자동 연결되지 않는다.

## 11. 알려진 주의사항

- 새 V10 경로는 공식 relocate-plan/relocate-approve로 승인한다. 원본 서버를 멈추지 않은 이번 검증은 보존된 cold V10 사본으로 수행했으며 실제 회사 PC 실행은 별도 확인해야 한다.
- H2 2.3.232 workaround를 유지한다. 공식 수정 버전으로의 dependency upgrade와 제거 판단은 별도 작업이다.
- Flyway의 H2 지원 경고가 남아 있다. migration/지속성 검사 성공과 경고 잔존을 함께 기록한다.
- 기존 SUPER_ADMIN의 일반 브라우저 로그인 불일치는 별도 운영 문제이며 이번에 수정하지 않았다.
- 실제 공개 홈페이지가 없다. API 확인을 홈페이지 E2E 완료로 기록하지 않는다.
- 운영 사전과 전체 IA는 미등록/미확정이다. seed/fixture로 채우지 않는다.
- 페이지 65 발행본에 테스트 문구와 로컬 관리자 링크가 남아 있다. 이관하면서 임의 정리하지 않는다.
- OneDrive를 과거 H2 persistence 문제의 원인으로 단정하지 않는다. 이번 기준 복사는 정지된 단일 파일을 명시적으로 확보하며 동기화에 맡기지 않는다.
- 문서에 비밀값을 쓰지 않아도 DB에는 계정·비밀번호 hash·콘텐츠가 포함된다. private GitHub에도 DB/receipt 묶음을 push하지 않고 보호된 방법으로 전달한다.

## 12. 내일 따라 할 명령 순서 체크리스트

각 명령은 해당 절의 블록을 사용하고, 예시 경로는 실제 설치 위치에 맞춘다. 어떤 검사든 실패하면 STOP한다. receipt/input 내용은 수정하지 않는다.

- [ ] 집 PC H1: 변수·RC hash·receipt·최신 process metadata·validate 로그 대조
- [ ] 집 PC H2: 기존 serve의 Ctrl+C **또는** 검증된 GracefulStop. PID/포트/Hikari/파일 독점 검사
- [ ] 집 PC H3: 새 transfer 폴더 → RC `CutoverTool inspect` → V1~V10/history/checksum/hash 확인
- [ ] 집 PC H4: 정해진 파일만 Copy-Item → 복사 DB inspect → fingerprint 비교 → transfer manifest
- [ ] 전달: 보호된 방법으로 폴더 전달. manifest hash는 별도 경로로 대조
- [ ] 회사 PC C1: `git clone` / `pull --ff-only` → commit 확인 → Java/Python/Git/환경변수 이름 확인
- [ ] 회사 PC C2: 받은 manifest와 파일별 hash 확인. 기준본 보관
- [ ] 회사 PC C3: 새 검증 폴더의 DB 사본 → 기존 RC `inspect` → 집 PC cold와 history/fingerprint/hash 비교
- [ ] 회사 PC C4: `relocate-plan` → 대상·hash·V10·pending 0 확인 → `relocate-approve --authorize-relocation`
- [ ] 회사 PC C5: 새 run-dir로 `serve` → V10 validate-only/추가 migration 0/cutover 비활성 확인
- [ ] 회사 PC C6: 로그인/두 탐색 모드/동일 ID/버전 이력/공개 API 읽기 확인
- [ ] 회사 PC C5: Ctrl+C 정상 종료 → PID/포트/Hikari/lock 확인 → 새 이름의 독립 cold 검사
- [ ] 동일 C5 `serve`로 재시작 → 동일 데이터 조회 → 정상 종료/cold 검사 → 쓰기 재개 판단

관련 문서: [실행](5D-2/07_DEPLOYMENT_AND_RUNTIME.md), [백업·복구](5D-2/08_BACKUP_AND_RECOVERY.md), [migration](5D-2/09_DATABASE_MIGRATIONS.md), [제한사항](5D-2/11_LIMITATIONS_AND_ACCEPTANCE.md).

이관 도구 검증 결과는 [V10_RELOCATION_RESULTS.md](V10_RELOCATION_RESULTS.md)에 기록한다. 과거 5D-2 문서의 “새 경로 승인 도구 없음” 설명은 이번 이관 도구에 한해 본 문서가 대체한다. CMS 기능·계정·운영 데이터의 기존 제한은 그대로 유지된다.
