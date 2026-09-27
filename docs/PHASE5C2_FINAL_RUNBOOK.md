# 5C-2 최종 전환·복구 runbook

이 절차는 5C-1C에서 **새 V3 사본으로만** 검증한다. 실제 `.local-data/aica-local.mv.db` 전환은 사용자의 별도 5C-2 승인 이후에만 실행한다. 기존 V3 DB 및 V3 실행 파일은 유지한다.

## 고정 실행물과 역할

- `scripts/cutover/cutover.py`: `plan` → `run` → `serve` 또는 `rollback`의 단일 운영 진입점. 리허설과 실제 전환은 같은 파일을 사용한다.
- V10 RC에 포함된 `CutoverTool`: 웹 서버를 시작하지 않는 일회성 migration 프로세스. `PropertiesLauncher`로 **해당 JAR 안의 코드와 migration만** 실행한다.
- `FileRuntimeConfiguration`: 정상 서버의 실제 Hikari 설정을 첫 연결 전에 검사한다. 파일 DB는 이미 V10이어야 하고 Flyway는 validate만 실행한다.
- V3 rollback JAR: 5C-1B에서 검증한 기존 실행물. 새 빌드로 대체하지 않는다.
- `release/runtime-hashes.json`과 release bundle manifest를 확인한다. JAR/스크립트/SQL이 달라지면 승인과 사본 리허설을 다시 수행한다.

H2는 2.3.232, Flyway는 10.20.1을 유지한다. V4~V10 파일은 변경하지 않는다.

## 시작 조건 및 STOP

1. 실제 원본 전환에 대한 별도 승인, 작업 시간, 쓰기 재개 책임자가 정해져 있어야 한다.
2. 원본을 사용하는 V3 서버·테스트·H2 Console·유지보수 도구를 정상 종료한다. 이 스크립트는 임의의 다른 Java 프로세스를 강제 종료하지 않는다. `Get-CimInstance Win32_Process`로 주체를 확인하고, 해당 서버의 정상 종료 절차를 사용한다.
3. 사용자의 관리자 브라우저 작업과 외부 쓰기를 중단한다. 검증 서버는 loopback에만 바인딩한다. 실제 운영 트래픽 차단/해제는 운영자의 작업이며, 새로운 maintenance UI나 방화벽 자동화 기능을 구현한 것은 아니다.
4. DB 독점 파일 접근이 불가능하면 STOP. 파일 잠금을 강제로 제거하지 않는다.
5. 승인 경로·DB hash/fingerprint·V3 이력·V1~V3 checksum·V10 RC SHA·V4~V10 목록·회피 옵션 중 하나라도 다르면 STOP.
6. `repair`, `clean`, Flyway 비활성화, 임의의 `.local-data` 경로 위장, 자동 생성 DB로 우회하지 않는다.
7. JVM agent/loader 환경 변수 오염(`JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS`, `LOADER_PATH` 등)이 있으면 STOP. 검증된 실행 조건부터 복구한다.

## 설정과 승인 수명

V10 파일 쓰기 URL은 정규화된 절대 경로에 다음 옵션을 사용한다.

```text
jdbc:h2:file:<absolute-base-without-.mv.db>;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0
```

실제 Hikari URL이 datasource URL과 다르거나 다른 dataSourceProperties/driver/INIT SQL을 사용하면 서버를 시작하지 않는다. 별도 Flyway URL 및 미승인 migration 경로도 차단한다. `AUTO_COMPACT_FILL_RATE=0`을 바꾸는 connection-init-sql도 차단한다. 정상 파일 DB 초기화 SQL 실행은 허용하지 않는다.

`plan`은 읽기 전용으로 승인 후보 JSON을 생성한다. DB 파일 hash 및 모든 테이블 fingerprint, 현재 V3/V1~V3 checksum, 실행 JAR SHA-256, V1~V10 목록, 정확한 URL/경로, 24시간 만료 시각을 담는다. 비밀번호는 포함하지 않는다.

`run`은 외부에서 지정한 `--approved-plan-sha`로 그 문서를 고정한다. 일회성 `migrate` 자식 프로세스에만 `AICA_CUTOVER_ENABLED=true`와 승인 hash를 전달한다. 웹 서버에는 이 환경을 전달하지 않는다. 승인 파일의 `.spent`가 있으면 성공/실패와 관계없이 재사용하지 않는다. 실패 후 임의로 `.spent`를 삭제하지 말고 원인을 확인하고 복구한 뒤 새 승인 문서를 만든다.

성공 receipt는 **대상 절대 경로와 동일 RC JAR SHA**에 묶인다. 원본 정상 서버는 이 receipt와 V10 schema 검증이 없으면 시작하지 않는다. receipt는 매 시작 시 migration을 허용하는 토큰이 아니다. 일반 서버에는 migration 경로 자체가 없다.

## 사전 정책

이번 RC는 `dictionaryMode=DEFER`다. migration V5의 등록형 5종 타입 및 기존 글의 GENERAL 초기화만 적용하고, 기수·주제는 비워 둔다. 운영 후보 사전은 아직 원본 등록 승인이 없으므로 임의 입력하지 않는다.

후기/FAQ/맛집 smoke용 2개 기수와 10개 주제는 `<run>/smoke/fixture.mv.db`에만 등록한다. 해당 파일은 실제 운영 대상으로 승격하지 않는다. 향후 운영 사전 확정 시 별도 승인·사본 검증을 거치며, 이 절차가 운영 사전 승인을 대신하지 않는다.

## 실행

PowerShell에서 경로는 모두 명시한다. `<release>`는 5C-1C 결과 보고서의 고정 release 디렉터리다. 원본 경로를 넣는 명령은 아직 실행하지 않는다.

```powershell
$taskPython = 'C:/Users/sfsf1/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$taskJava = '<JDK17>/bin/java.exe'
$taskRelease = '<release>'
$taskTarget = '<approved absolute .mv.db path>'
$taskPlan = '<new approval.json>'
$taskRun = '<new run directory>'

& $taskPython -X utf8 "$taskRelease/cutover/cutover.py" plan `
  --java $taskJava --rc "$taskRelease/v10-rc.jar" --db $taskTarget --output $taskPlan
```

계획 내용을 확인하고 SHA-256을 승인한다. 실행 시점에 다시 계산한 hash만 무조건 승인하는 방식으로 외부 검토를 생략하지 않는다. 운영 계정은 환경 변수 `AICA_SMOKE_USER`, `AICA_SMOKE_PASSWORD`에 넣고, DB 인증이 필요하면 `AICA_DB_USER`, `AICA_DB_PASSWORD`에 넣는다. 비밀번호를 명령 인수나 승인 JSON에 기록하지 않는다. 끝나면 해당 프로세스 환경에서 제거한다.

```powershell
& $taskPython -X utf8 "$taskRelease/cutover/cutover.py" run `
  --java $taskJava --rc "$taskRelease/v10-rc.jar" `
  --plan $taskPlan --approved-plan-sha '<separately approved SHA-256>' `
  --v3-runtime "$taskRelease/v3-runtime.jar" `
  --v3-sha256 c20f0f4d92e1867fbdf326c30b67949ceedf71aa3c0a2d597dc4432e1201cf6d `
  --run-dir $taskRun --port 8095 --smoke-port 8096 --restarts 4
```

- 사본 리허설: 위 명령에 `--rollback-rehearsal`을 붙여 V3 복구까지 검사한다.
- 실제 원본: 별도 5C-2 승인 후에만 `--authorize-original-cutover`를 붙인다. 이 옵션은 나머지 guard를 우회하지 않는다.
- 기존 run 디렉터리나 승인 문서를 덮어쓰지 않는다.

`run`의 실제 순서:

1. 대상의 독점 파일 접근·승인 hash 확인 → 최신 V3 DB와 대응 V3 JAR 백업 → 독립 readonly fingerprint.
2. 동일 백업으로 새 last-copy 생성 → 같은 one-shot guard와 RC로 V4→V10 → 단계별 기존 값·페이지 블록 내용·이력 확인 → writer 종료 후 readonly V10 확인.
3. 실제 승인 대상 hash 재확인 → 동일 guard로 V4→V10 적용 → `.spent` 소모 → 정상 종료 후 readonly 확인과 receipt 생성.
4. 운영 사전 DEFER 상태 확인 → V10 정상 서버 시작 → SUPER_ADMIN 로그인 → baseline 생성 및 재실행 0건 확인.
5. 페이지 65의 **동일 내용 명시적 저장**을 4회 수행한다. 공개 snapshot과 원래 편집 내용은 유지하고 새 version 행을 만든다. 따라서 실제 전환 시에도 검증용 명시 저장 이력이 4건 생긴다. 본문에 검증 문자열을 넣지 않는다.
6. 각 회 정상 종료 → 독점 파일 접근 및 hash/mtime/PID 기록 → 별도 JVM readonly 검사 → 같은 경로/JAR로 재시작 → 최신 페이지·version·공개 snapshot 비교. 마지막 확인용 기동을 포함해 총 5회 기동한다.
7. 대상의 새 smoke 사본에서만 fixture 사전/콘텐츠/계정 생성 → 역할, 발행 경계, POSTS 세 방식, 후기/FAQ/맛집, 블록, 템플릿, 버전 복구, 미디어 보호 검증 → 정상 종료·재시작 비교.
8. 사본 리허설 옵션이면 아래 rollback까지 수행한다. 실제 전환이면 서버를 정지한 상태로 최종 결과를 남긴다. 검증을 통과했다고 외부 쓰기를 자동 재개하지 않는다.

쓰기 재개를 승인한 뒤 정상 서버는 다음과 같이 시작한다. cutover flag는 사용하지 않는다.

```powershell
& $taskPython -X utf8 "$taskRelease/cutover/cutover.py" serve `
  --java $taskJava --run-dir $taskRun --port 8095
```

`serve`는 receipt + V10 검증만 수행한다. Ctrl+C는 검증된 shutdown agent로 Spring/Hikari 정상 종료를 요청한다. OS에서 창/프로세스를 강제 종료하는 방식은 정상 종료 절차가 아니다.

## 실패 시 rollback

오류가 하나라도 발생하면 쓰기를 재개하지 않는다. 오류 로그·승인·receipt·PID를 유지한다. 살아 있는 해당 서버를 정상 종료하고 파일 독점 접근을 확인한다. 부분 migration 상태를 `repair`로 성공처럼 만들지 않는다.

```powershell
& $taskPython -X utf8 "$taskRelease/cutover/cutover.py" rollback `
  --java $taskJava --run-dir $taskRun --port 8095
```

원본 대상으로 복구하는 경우에는 별도 명시적 확인 옵션 `--authorize-original-rollback`도 필요하다.

복구 순서: 현재 V10 DB + RC를 `v10-preserved`에 보존 → 백업 V3 DB hash/fingerprint 확인 → **정확히 승인된 대상 파일만** V3 사본으로 복원 → 백업의 대응 V3 JAR를 `active-rollback`에 복원 → 해당 JAR로 로그인/페이지·콘텐츠 조회 → 정상 종료 → 독립 readonly V3 이력 및 전체 테이블 fingerprint 비교. V10 파일에 V3 JAR만 실행하는 경로는 제공하지 않는다.

복구 후 정상 운영 재개는 V3 구성으로 별도 수행한다. V10 receipt는 남아 있어도 schema=V3 검사가 통과하지 않으므로 V10 서버가 잘못 시작되지 않는다.

쓰기 재개 이후의 V10 작업은 전환 직전 V3 백업에 포함되지 않는다. 이 경우 보존한 V10 파일에서 작업을 별도로 확인해야 하며, rollback이 새 작업까지 V3로 자동 이전한다고 해석하지 않는다. V3 실행물의 기존 기능·권한 동작도 함께 돌아간다.

## 잔여 위험과 범위

- H2 2.3.232 종료 압축 뒤 commit 순서 문제를 회피하기 위한 설정이다. 자동 종료 압축을 끄면 파일 크기가 더 커질 수 있다. 임의의 `SHUTDOWN COMPACT`/옵션 제거로 해결하지 않는다.
- 공식 수정 H2 버전으로의 업그레이드는 별도 작업이다. 새 버전에서 동일한 반복 종료·독립 readonly·rollback 시험을 통과한 뒤에만 workaround 제거를 판단한다. 이번 dependency는 변경하지 않는다.
- Flyway의 H2 지원 경고는 별도로 남는다. V4~V10 migrate/validate 성공이 공급자의 호환 보장을 대신하지 않는다.
- 실제 공개 홈페이지가 없으므로 렌더링 E2E는 완료되지 않았다. 이번 smoke는 CMS와 공개 API의 발행 경계를 검증한다.
- OneDrive 아래 원본 파일의 장기 운영 적합성, 백업 보관·접근 권한·운영 계정/포트 공개 범위는 운영 배치 결정사항이다. 이번 작업에서 저장 위치를 임의로 이동하거나 비밀번호·역할을 바꾸지 않는다.
