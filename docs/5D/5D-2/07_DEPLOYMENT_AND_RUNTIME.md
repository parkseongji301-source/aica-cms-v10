# 07. 배포·실행 가이드

대상: 현재 Windows 실행 환경 담당자. **이 문서 작성 단계에서는 아래 실행·종료 명령을 실행하지 않았다.** 현재 서버를 중복 실행하지 않는다. 새 호스트 배포는 끝부분의 별도 이관 조건을 따른다.

## 현재 V12 실행 요약 (2026-09-30)

- 현재 기준 실행물은 V12 RC1이다. JAR·receipt·DB 경로와 시작·정상 종료·되돌리기 절차는 [V12 안정 기준점](../../V12_STABLE_BASELINE.md)을 따른다.
- 이 PC에서는 저장소 루트의 `START.cmd`/`STOP.cmd`가 Git 제외 파일 `.cache/current-ui.json`이 가리키는 실행본을 쓴다. V12 실행본이면 `scripts/start-v12-runtime.ps1`이 JAR·종료 도구·정적 파일 해시를 확인한 뒤 validate-only로 127.0.0.1:8095에서 시작하고, STOP은 GracefulStop 정상 종료 후 `V12PromotionTool inspect`로 DB를 읽기 전용 검사한다.
- V12 로그에서는 `V12 file runtime`, 정확한 DB 경로, `AUTO_COMPACT_FILL_RATE=0`, `migration=validate-only`를 확인한다.
- V11 기준점 실행 기록은 [V11 운영 기준점](../../V11_OPERATING_BASELINE.md)(이전 PC 경로)에 있다.
- 아래 "현재 고정 실행 기준" 이후 내용은 **V10 실행 절차의 보존 기록**이다. 경로·RC·run-dir는 V10 당시 PC 기준이며 V12 DB에 사용하지 않는다. `AUTO_COMPACT_FILL_RATE=0`·정상 종료·강제 종료 금지 원칙은 V12에도 그대로 적용한다.

## 반드시 유지할 파일 DB 설정

> **H2 2.3.232 파일 DB에 쓰는 모든 V10 runtime에는 `AUTO_COMPACT_FILL_RATE=0`이 필요하다. 임의로 제거하지 않는다.**

5C-1B에서 같은 파일을 정상 종료 후 다시 열었을 때 마지막 변경이 보존되지 않던 현상을 종료 시 파일 압축/마지막 commit 순서 문제로 확인했다. 해당 옵션으로 반복 재현이 사라졌고 같은 조건으로 원본 전환·재시작을 검증했다. Flyway의 H2 지원 경고는 이 현상의 원인으로 합쳐 설명하지 않는다.

현재 파일 writer는 정확한 절대 경로와 `IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0`을 요구한다. INIT/AUTO_SERVER/AUTO_RECONNECT 등 임의 옵션을 추가하지 않는다. 파일 검사만 하는 연결은 ACCESS_MODE_DATA=r을 사용하며 읽기 전용이라고 운영 파일 동시 접근을 허용하지 않는다.

FileRuntimeConfiguration이 실제 Hikari URL을 첫 쓰기 연결 전에 검사한다. URL 불일치·별도 Flyway datasource·대체 연결 속성·cutover flag·부적합 receipt·V10 아님·pending migration이면 시작을 거부한다. 설정을 빼고 경고만 무시하며 기동하는 경로를 운영 절차로 쓰지 않는다.

H2 업그레이드는 별도 승인 작업이다. 공식 수정 포함 여부, 같은 파일 종료/재시작·사본 migration·rollback·전체 회귀를 검증한 뒤 workaround 제거 여부를 결정한다. 업그레이드와 기능 전환을 한 번에 섞지 않는다.

## 현재 고정 실행 기준

아래 경로는 이 저장소의 실제 위치다. 경로를 다른 PC에 그대로 사용하면 안 된다.

| 항목 | 현재 기준 |
|---|---|
| repository root | `C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main` |
| Java | `.tools/jdk/jdk-17.0.20.1+1/bin/java.exe` (JDK 17) |
| Python | `C:/Users/sfsf1/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe` |
| 승인 RC | `.cache/phase5c1c/20260927-223140/release-final/v10-rc.jar` |
| RC SHA-256 | `606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e` |
| 고정 실행 도구 | 같은 release-final의 `cutover/cutover.py` |
| 현재 run-dir | `.cache/phase5c2-retry/20260927-233113/original-run` |
| receipt | run-dir의 `runtime-receipt.json` |
| DB 파일 | `.local-data/aica-local.mv.db` |
| Spring profile | dev (실제 승인 경로로 datasource를 덮어씀) |
| bind/port | 127.0.0.1:8095 |
| 정상 실행 | validate-only, cutover flag=false |

JDBC base는 `.mv.db`를 제외한 절대 경로다. `dev` 기본값의 다른 `backoffice` DB나 과거 상대 경로를 원본이라고 추정하지 않는다. 현재 원본은 `aica-local.mv.db`다. 실행 전 receipt/input.json/process metadata의 경로·RC hash를 대조한다.

`scripts/run-local.ps1`은 보존된 V3 실행 경로다. **원본 V10에 사용하지 않는다.** `run-dev.ps1`, 사본별 `run-*-copy.ps1`, seed/fixture 스크립트도 현재 원본의 운영 진입점으로 쓰지 않는다. 기존 파일은 동결 범위에서 보존돼 있다.

## 정상 재시작 절차

1. 작업 중인 운영자에게 저장 완료·쓰기 중단을 확인한다. 같은 DB를 여는 서버/테스트/유지보수 프로세스가 없어야 한다.
2. 이미 서버가 실행 중이면 아래 정상 종료를 먼저 수행한다. PID 종료·Hikari shutdown·포트 해제·파일 잠금 해제를 확인한다.
3. `run-dir/input.json`, 성공 `result.json`, receipt와 승인 RC/bundle hash가 맞는지 확인한다. 현재 V10에는 과거 plan/run migration을 다시 실행하지 않는다.
4. 필요한 DB 자격증명은 승인된 보안 채널로 프로세스 환경에 제공한다. `AICA_DB_USER`, `AICA_DB_PASSWORD`의 값을 문서·명령 기록·로그에 넣지 않는다. 미검토 JVM/loader 환경변수를 추가하지 않는다.
5. 다음 명령을 저장소 루트에서 실행한다. 운영 JAR을 재빌드하지 않는다.

```powershell
$projectRoot = 'C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main'
$pythonPath = 'C:/Users/sfsf1/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$javaPath = Join-Path $projectRoot '.tools/jdk/jdk-17.0.20.1+1/bin/java.exe'
$releasePath = Join-Path $projectRoot '.cache/phase5c1c/20260927-223140/release-final'
$runPath = Join-Path $projectRoot '.cache/phase5c2-retry/20260927-233113/original-run'
& $pythonPath -X utf8 (Join-Path $releasePath 'cutover/cutover.py') serve `
  --java $javaPath --run-dir $runPath --port 8095
```

`serve`는 원본 잠금·RC hash·기존 성공 결과를 확인하고 validate-only V10을 실행한다. 새 `serve-<시각>` 증거 디렉터리에 process metadata와 로그를 기록한다. 정상 출력의 PID·주소를 기록한다. readiness 실패나 guard 오류가 나면 STOP하고 원인을 확인한다. receipt를 수동 편집하거나 copy-validation을 원본 우회에 쓰지 않는다.

6. 로그에서 `V10 file runtime`, 정확한 path/profile, `AUTO_COMPACT_FILL_RATE=0`, `migration=validate-only`, 서버 Started를 확인한다. `/login`, `/admin-next`, 권한에 맞는 콘텐츠·페이지·메뉴 조회 및 익명 `/api/public/v1/pages/65`를 확인한다. 빈 사전/미디어/템플릿은 현재 정상 데이터 상태다.

## 정상 종료

위 `serve`를 실행한 콘솔이 있으면 **Ctrl+C**를 사용한다. wrapper가 검증된 GracefulStop agent로 Java 종료를 요청하고 exit 0과 파일 잠금 해제를 검사한다. 단순 강제 프로세스 종료를 정상 종료라고 기록하지 않는다.

기존 콘솔이 없는 현재 실행을 종료해야 한다면, 승인된 `run-dir/shutdown-tool/graceful-stop.jar`를 사용할 수 있다. 실행 전에 최신 `serve-*/normal-runtime-process.json`의 PID와 8095 리스너의 PID, RC/DB 경로를 대조한다. PID 9788은 문서 작성 당시 값일 뿐 고정 실행 인수가 아니다.

```powershell
# 위에서 설정한 $javaPath / $runPath 사용. 운영 중인 대상 PID를 먼저 확인한다.
$serveEvidence = Get-ChildItem -LiteralPath $runPath -Directory |
  Where-Object Name -Like 'serve-*' | Sort-Object Name | Select-Object -Last 1
$runtimeInfo = Get-Content -LiteralPath (Join-Path $serveEvidence.FullName 'normal-runtime-process.json') -Raw | ConvertFrom-Json
$serverProcessId = [int]$runtimeInfo.pid
$listener = Get-NetTCPConnection -LocalPort 8095 -State Listen -ErrorAction Stop
if ($listener.OwningProcess -notcontains $serverProcessId) { throw 'STOP: PID mismatch' }
$stopAgent = Join-Path $runPath 'shutdown-tool/graceful-stop.jar'
& $javaPath --add-modules jdk.attach -cp $stopAgent GracefulStop ([string]$serverProcessId) $stopAgent
if ($LASTEXITCODE -ne 0) { throw 'STOP: graceful stop request failed' }
Wait-Process -Id $serverProcessId -Timeout 60 -ErrorAction SilentlyContinue
if (Get-Process -Id $serverProcessId -ErrorAction SilentlyContinue) { throw 'STOP: process remains' }
```

이 명령은 종료 요청만 한다. 담당자는 로그의 graceful shutdown/Hikari 종료, 포트 해제, [백업 가이드의 독점 파일 검사](08_BACKUP_AND_RECOVERY.md)를 추가로 확인한다. agent attach가 실패하면 강제 종료로 자동 전환하지 않는다. Windows 서비스 등록·자동 재기동 기능은 현재 없다.

## 로그·진단·STOP

- 최신 `original-run/serve-*/normal-runtime.log`: 시작·권한·DB 안전 검사·Spring/Hikari 종료.
- 같은 폴더 `normal-runtime-process.json`: PID/profile/절대 DB/RC hash/cutover flag. 새 증거를 과거 로그에 덮어쓰지 않는다.
- `original-run/evidence/`: 전환·5회 수준 지속성·분리 smoke 증거. 현재 정상 실행 로그와 구분한다.
- `DB safety STOP`, checksum mismatch, lock held, stale revision, session/CSRF 오류를 서로 구분한다. DB 손상이라고 단정해 `clean/repair`하지 않는다.
- 정상 종료 후 cold 독립 검사는 백업 가이드의 기존 CutoverTool inspect를 사용한다. 실행 중 원본 파일을 H2 Console/별도 Java로 열지 않는다.

## React 포함 및 개발용 빌드

현재 RC에는 이미 React가 들어 있다. 소스 변경을 승인받은 향후 개발 환경에서만 `scripts/build-admin-next.ps1`을 사용한다. 이 스크립트는 pnpm frozen lock 설치·TypeScript/Vite 빌드·Maven verify를 수행하고, 결과 JAR은 새 release 검증 대상이다. 현재 receipt와 다른 checksum의 JAR을 원본에 넣으면 안 된다.

`frontend/vite.config.ts`의 개발 proxy는 과거 8081 대상이다. 현재 8095 운영 UI를 Vite 개발 서버로 여는 절차가 아니다. 배포 시 Vite 산출물은 `/next-app/`로 제공된다. 지원 Node/패키지 매니저 도구를 준비하되 dependency upgrade를 운영 재시작에 섞지 않는다.

## 공개 API 확인 주소

| 익명 GET | 내용 |
|---|---|
| `/api/public/v1/menus` | 현재 노출 메뉴. apiHref는 데이터 URL |
| `/api/public/v1/pages/65` 또는 `/pages/by-slug/about` | 페이지 발행본 |
| `/api/public/v1/pages/{id}/blocks/{blockId}/posts` | 해당 발행 페이지의 visible POSTS 결과 |
| `/api/public/v1/posts/{id}` | 현재 공개 콘텐츠 발행본 |
| `/api/public/v1/posts?categoryId=...&page=1&limit=6` | 기존 category 목록, 조건 생략은 전체 공개 글 |
| `/api/public/v1/media/{id}/file` | 현재 공개 참조가 있는 파일 |

실제 홈페이지·도메인·TLS·CORS 허용 목록·reverse proxy·운영 서비스 설치는 아직 확정하지 않았다. 공개 API는 익명 읽기 전용이며 관리자 API를 공개 사이트에 사용하지 않는다. 일반 posts API의 신규 분류 필터를 가정하지 말고 게시된 POSTS 블록을 이용한다.

## 다른 운영 업체/호스트로 이관할 때

현재 실행은 Windows·개인 PC의 OneDrive 경로·절대 DB 경로·승인 RC receipt에 묶여 있다. receipt를 수정해 옮긴 경로를 통과시키는 범용 배포 도구는 없다. 동일 경로 복원과 새 호스트/경로 이관을 구분한다.

이관 시 새 저장 위치·프로세스 관리·접근 주소·비밀값 전달·백업 소유권을 확정하고, 경로/receipt 승인 방식을 별도 설계·검증해야 한다. 파일 DB를 OneDrive 동기화에 장기 의존할지 판단한다. 동기화를 과거 persistence 오류 원인으로 단정하지 않는다. 이러한 후속 결정은 현재 CMS 기능을 새로 만드는 이유가 아니라 운영 배치와 실행 승인 범위다.

근거: [고정 실행 코드](../../../scripts/cutover/cutover.py), [실제 datasource 검사](../../../src/main/java/egovframework/backoffice/mvp/config/FileRuntimeConfiguration.java), [파일 안전 규칙](../../../src/main/java/egovframework/backoffice/mvp/operations/FileDatabaseSafety.java), [5C-2 성공 기록](../../PHASE5C2_RETRY_RESULTS.md).
