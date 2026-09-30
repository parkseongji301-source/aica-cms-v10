# 08. 백업·복구 가이드

대상: 운영/DB 담당자. 문서 내용만 되돌리는 버전 복구와 DB/runtime 장애 복구를 구분한다. **DB와 대응 runtime을 같은 기준점으로 보관·복구한다. V10 DB에 V3 JAR만 연결하면 안 된다.** 이 단계에서 백업을 위해 현재 서버를 멈추거나 DB 파일을 복사하지 않았다.

> 2026-09-30 기준: 백업 묶음 원칙은 V12에도 같다. "V10"은 "현재 schema(V12)"로, migration 목록은 V1~V12로 읽는다. V12 DB에는 V12 RC JAR과 MIGRATED_V12 receipt를, V11 DB에는 V11 JAR과 receipt를 짝지어 보관한다. V12 기준점의 rollback 자료와 V11 보존 자료는 [V12 안정 기준점](../../V12_STABLE_BASELINE.md)에 정리했다. 아래 경로와 CutoverTool 예시는 V10 당시 PC 기준이며, V12 DB의 cold 검사는 V12 RC의 `V12PromotionTool inspect`를 사용한다.

## 같은 시점의 백업 묶음

| 포함 대상 | 이유 |
|---|---|
| 정상 종료된 전체 `.mv.db` | 콘텐츠·페이지·publication·version·계정·미디어 BLOB 포함 |
| 실제 실행 RC JAR / SHA-256 | schema와 업무 처리 호환 |
| release bundle manifest와 migration V1~V10 SQL/Java·checksum | 실행물과 DB 이력 검증 |
| 소스 전체·Git HEAD/status/diff·미추적 파일 | 현재 작업 상태 재현. Git HEAD만으로 부족 |
| run-dir/input/result/receipt, 고정 cutover 도구, shutdown agent | 정확한 경로와 승인 실행 증거 |
| profile·datasource 절대 경로·포트·Java·환경변수 이름 | 같은 설정 복원. 비밀값은 별도 보호 |
| cold DB hash·크기·시각·Flyway history·테이블 fingerprint·로그 | 파일 동일성과 데이터 보존 확인 |
| 복구 담당자·시각·절차·검증 결과 | 재개/rollback 책임과 증거 |

미디어 bytes는 현재 DB BLOB이므로 DB 백업에 포함된다. 향후 외부 파일 저장소를 도입하면 그 시점부터 별도 일관성 백업이 필요하다. `.trace.db`나 로그가 DB 원본을 대체하지 않는다. 사용자/비밀번호 해시가 들어 있는 DB와 덤프는 공개 문서가 아니다. 접근 통제·암호화 보관·보관 주기/RPO/RTO·별도 장치 사본은 운영 책임자가 확정해야 하며 자동 백업은 아직 구현되지 않았다.

## 백업 절차

1. 쓰기를 중지하고 콘텐츠/페이지/템플릿 저장 완료를 확인한다.
2. [실행 가이드](07_DEPLOYMENT_AND_RUNTIME.md)로 서버를 정상 종료한다. 테스트·H2 Console·유지보수 JVM도 해당 파일을 열지 않아야 한다.
3. 실제 PID 종료와 Hikari shutdown을 확인한다. 해당 DB 절대 경로를 독점 읽기로 열 수 있는지 검사한다. 실패하면 파일 복사를 진행하지 않는다.
4. 새 시각의 백업 디렉터리에 DB와 대응 runtime/소스/config/migration/receipt를 복사한다. 과거 백업을 덮어쓰지 않는다.
5. 원본(정지 상태)과 복사본 SHA-256을 비교한다. read-only inspect로 Flyway history와 모든 테이블 fingerprint를 남긴다. 검사 자체가 DB hash를 바꾸지 않아야 한다.
6. 같은 백업을 대상으로 복구 점검이 가능하도록 manifest와 운영 증거를 함께 보관한다. 정상 실행을 재개할 때는 V10 serve/validate-only다.

파일 잠금·hash 검사의 예시다. **서버/모든 접근 프로세스가 종료된 유지보수 시간에만** 사용한다. 쓰기 중인 파일의 해시는 일관된 백업 증거가 아니다.

```powershell
$projectRoot = 'C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main'
$databasePath = (Resolve-Path -LiteralPath (Join-Path $projectRoot '.local-data/aica-local.mv.db')).Path
$fileHandle = [IO.File]::Open($databasePath, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
try { $fileLength = $fileHandle.Length } finally { $fileHandle.Dispose() }
Get-FileHash -LiteralPath $databasePath -Algorithm SHA256
```

독점 검사는 그 순간의 확인이다. 이후 다른 프로세스를 시작하지 않는 유지보수 통제와 함께 사용한다. 자동 백업/잠금 관리 서비스가 있다는 의미는 아니다.

## 검증된 독립 read-only 검사

승인 RC 안의 CutoverTool `inspect`는 `ACCESS_MODE_DATA=r;IFEXISTS=TRUE`로 읽고, 검사 전후 bytes hash 동일 여부·Flyway 성공 이력·모든 PUBLIC 테이블의 행수/내용 hash를 기록한다. writer workaround도 유지하지만 읽기 권한을 쓰기로 바꾸지는 않는다. 기존 도구를 사용하며 SQL 텍스트를 임의로 붙이지 않는다.

아래는 인수를 각각 문자열로 전달한다. JVM option → classpath → main class → 프로그램 인수 순서다. 출력 디렉터리는 담당자가 만든 새 증거 디렉터리이고 출력 파일은 아직 없어야 한다. DB 자격증명은 별도 보안 환경으로 제공한다.

```powershell
$javaPath = Join-Path $projectRoot '.tools/jdk/jdk-17.0.20.1+1/bin/java.exe'
$runtimePath = Join-Path $projectRoot '.cache/phase5c1c/20260927-223140/release-final/v10-rc.jar'
# $databasePath는 정지된 대상 또는 백업 사본의 검증된 절대 경로.
# $evidencePath는 담당자가 생성한 새 증거 폴더의 절대 경로.
$inspectionFile = Join-Path $evidencePath 'cold-inspection.json'
if (Test-Path -LiteralPath $inspectionFile) { throw 'STOP: evidence exists' }
$inspectArgs = @(
  '-Dfile.encoding=UTF-8',
  '-Dloader.main=egovframework.backoffice.mvp.operations.CutoverTool',
  '-cp', $runtimePath,
  'org.springframework.boot.loader.launch.PropertiesLauncher',
  'inspect', $databasePath, $inspectionFile
)
& $javaPath @inspectArgs
if ($LASTEXITCODE -ne 0) { throw 'STOP: read-only inspection failed' }
```

같은 frozen Java launcher의 `inspect` 인수 계약을 확인한 문서 예시이며 이번 단계에서 실행하지 않았다. 5C-2 보충 SQL 검사도 native 인수 배열을 사용했고 원본 baseline ID까지 독립 확인했다. 명령 quoting을 변경할 경우 원본보다 사본에서 먼저 확인한다.

## V10 정상 백업으로 복구

1. 장애 시각과 영향 범위를 기록하고 쓰기 차단을 유지한다. 현장 DB/schema를 즉석 수정하지 않는다.
2. 모든 접근 프로세스를 정상 종료하고 잠금 해제를 확인한다. 종료 자체가 실패하면 강제 덮어쓰지 말고 담당자에게 에스컬레이션한다.
3. 실패 DB·실행 RC·로그·history·hash를 새 증거 묶음으로 보존한다.
4. 복구할 **V10 DB + 그 DB와 호환되는 승인 V10 runtime + 설정/receipt**를 고른다. 백업 이후 작업이 사라지는 범위를 운영 책임자가 확인한다.
5. 원래 승인된 정확한 경로로 복원하고 cold hash/history/fingerprint를 백업 기준과 비교한다. 재귀 삭제나 폴더 전체 덮어쓰기로 처리하지 않는다.
6. 외부 쓰기를 막은 127.0.0.1 환경에서 해당 receipt와 V10 serve/validate-only로 기동한다. 필요하면 비어 있는 격리 포트를 사용하되 DB 경로는 receipt와 같아야 한다.
7. 로그인·콘텐츠 목록/상세·page 1/65·메뉴·분류·이력·미디어 사용처·공개 API를 조회한다. 검사하려고 원본에 샘플 글/계정을 만들지 않는다.
8. 정상 종료 → 독립 read-only history/fingerprint 비교 → 재시작 → 같은 조회 결과 확인 후 쓰기를 재개한다.

V10 복구는 같은 경로/RC/receipt의 데이터 백업 복원이다. **현재 `cutover.py rollback`은 V3 전환 rollback 전용이며 일반 V10 백업 복구 명령이 아니다.** 다른 경로의 사본 검증은 기존 사본 안전 조건에 따른 별도 격리 절차가 필요하다. receipt를 고쳐서 경로 검사를 통과시키지 않는다.

## 5C에서 검증한 V3 rollback의 의미

5C 중단 시 실패 V10 상태 보존 → 프로세스 종료 → 적용 직전 V3 DB + 대응 V3 JAR 복원 → 격리 로그인/콘텐츠/페이지/메뉴 조회 → 16개 기존 테이블 fingerprint 비교까지 수행했다. 이후 재시도는 성공했고 현재 원본은 V10이다.

보존 기준점은 `.cache/phase5c2-retry/20260927-233113/pre-cutover`, 실행 rollback 묶음은 같은 경로 `original-run/backup`이다. `cutover.py rollback --run-dir ... --authorize-original-rollback`은 명시적 원본 rollback 승인이 필요하며 `original-run/input.json`의 대상과 백업을 사용한다. 현재 V10을 일상적으로 V3로 내리는 권한을 이 문서가 부여하지 않는다.

V3에는 전환 후 version/분류/페이지 변경이 없다. 따라서 지금 V3 rollback을 결정하면 전환 후 모든 운영 변경을 별도 보존하고 손실 범위를 승인해야 한다. DB는 V10인 채 JAR만 V3로 바꾸는 방식은 어떤 경우에도 대체 복구가 아니다.

## 복구 판정표

| 판정 | 확인 기준 |
|---|---|
| 같은 파일/기준점 | 정확한 경로, cold SHA-256, 크기·시각, backup manifest |
| schema | Flyway 목표 버전/성공 history/checksum, validate 성공, pending 0 |
| 기존 데이터 | ID/category/page/menu/계정/미디어/이력 및 모든 테이블 fingerprint 일치 |
| 실행물 | 대응 JAR hash와 receipt 일치; V10 writer 옵션 유지 |
| 기능 | 역할에 맞는 로그인/조회, 발행본 API, 실수한 시험 데이터 없음 |
| 지속성 | 정상 종료/잠금 해제/독립 검사/재시작 후 같은 데이터 |

하나라도 다르면 쓰기 재개를 보류한다. fingerprint 차이를 없애려고 DB를 임의 수정하지 않는다. 승인된 의도 변경과 예기치 않은 변경을 분리해 기록한다.

근거: [중단·복구 기록](../../PHASE5C2_STOP_AND_ROLLBACK.md), [성공 재시도 기록](../../PHASE5C2_RETRY_RESULTS.md), [CutoverTool](../../../src/main/java/egovframework/backoffice/mvp/operations/CutoverTool.java), [readonly 검사 구현](../../../src/main/java/egovframework/backoffice/mvp/operations/FileDatabaseSafety.java).
