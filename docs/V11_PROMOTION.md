# V10 → V11 공식 승격 절차

작성일: 2026-09-29. 처음에는 승격 준비용으로 작성했으며, 이후 사용자의 실제 승격 승인에 따라 아래 절차를 실행했다. 실제 8095 V11 승격·데이터 보존·정상 종료·재시작은 PASS했다. 현재 실행 경로와 확정 해시는 [V11 운영 기준점](V11_OPERATING_BASELINE.md)을 따른다. 새 환경의 추가 승격에는 해당 환경의 최신 백업과 새 승인이 필요하다.

## 고정 범위와 실행물

포함: React 게시물 작성·수정·게시·재게시, 휴지통 이동·복원·영구삭제, `V11__post_trash.sql`.

제외: 전체 UX/UI 개편, Thymeleaf 제거, `/admin-next` 경로 정리, 동적 IA, 대시보드, 방문자 통계, 기타 신규 기능. 기존 화면·기능이 실행물에 존재하는 것은 이번 신규 개발 범위에 포함된다는 뜻이 아니다.

- 승인 V10 JAR SHA-256: `606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e`
- V11 RC1: `.cache/v11-release/V11-RC1-20260929/runtime/v11-rc.jar`
- V11 RC1 SHA-256: `5be79fd5ce32e348e6ddf0f3b5acd5f32bebd0441802ac3356d64deca800906e`
- 실행 도구: 같은 릴리스의 `tools/v11/promotion.py` 및 `tools/cutover/` 지원 도구. 소스 작업 폴더의 도구를 배포 중 덮어쓰지 않는다.
- V11 React는 RC JAR 안의 정적 파일을 사용한다. V10 외부 `ui-assets` 설정을 V11 실행에 상속하면 안 된다.
- 기존 승인 V10의 JAR, 외부 UI, 시작 설정, receipt와 이관 도구는 보존한다. 새 RC가 기존 JAR을 덮어쓰지 않는다.

## V10 승인 기준과의 차이

| 항목 | 기존 V10 | V11 |
| --- | --- | --- |
| 정상 실행 schema | V1~V10 | V1~V11, pending 0 |
| 정상 실행 receipt | `MIGRATED_V10` | `MIGRATED_V11`, `approvalKind=V10_TO_V11` |
| schema 변경 | 이미 승인된 V10 | `post_trash(post_id)` 추가 1건, 기존 posts FK·삭제 cascade |
| 기존 migration | 승인 V1~V10 | SQL 원본 바이트와 V8 Java class 바이트, Flyway checksum 전부 동일 |
| 기존 업무 데이터 | 승인 V10 데이터 | 전체 기존 테이블/컬럼/값 보존, 옛 삭제 행 backfill 없음 |
| 실행물 | V10 JAR와 당시 외부 UI | 새 서버/API와 일치하는 React를 포함한 V11 JAR |
| 삭제 의미 | 기존 삭제 정책 | 휴지통 보관 → 임시보관 복원 → 명시적 재게시, 휴지통에서 영구삭제 |
| rollback | V10 전체 구성 | V11 보존 후 적용 직전 V10 DB + JAR + UI + 설정 + receipt 전체 복귀 |

권한은 확대하지 않는다. 휴지통은 기존 삭제 권한인 SUPER_ADMIN, 게시·재게시 권한은 기존 정책을 따른다. 복원은 같은 ID의 최신 초안을 DRAFT로 되돌리며 자동 공개하지 않는다. 영구삭제는 게시물 소유 데이터/버전을 제거하고 미디어 원본은 남긴다.

## 1. 적용 전 백업 및 실행 중지

1. 실제 승격 승인을 받은 뒤 쓰기 작업을 중지하고 기존 8095 프로세스의 JAR·DB·포트를 승인 기록과 대조한다.
2. 승인된 `GracefulStop` 도구로 정상 종료한다. 강제 종료나 열린 H2 파일 복사를 사용하지 않는다. 종료 코드 0, Spring/Hikari 정상 종료 로그, 포트 해제, DB 단독 접근 가능 여부를 확인한다.
3. 최신 cold DB를 읽기 전용으로 검사한다. schema는 정확히 V10이고 승인 migration checksum과 일치해야 한다. `repair`, `baseline`, 초기화, seed를 실행하지 않는다.
4. DB, 승인 V10 JAR, 외부 `ui-assets`, `ui-launch.json`, `start-ui.ps1`, `run` 전체(receipt/input/result/계획/종료 도구), 승인 이관 도구를 함께 백업하고 파일별 SHA-256을 기록한다. 현행 승인 도구의 역사적 의존성인 V3 JAR도 보관하되 rollback 실행물은 V10이다.
5. `capture_v10.py`는 이미 종료된 8095에서 승인 V10을 한 번 기동·정상 종료하고 위 묶음을 만드는 도구다. 새 출력 경로만 허용하고 완료 후 서버를 종료 상태로 둔다. 기존 `start-ui.ps1`은 UTF-8 기본 PowerShell 7에서 실행한다. Windows PowerShell 5.1은 한글 JSON 경로를 잘못 읽을 수 있다.
6. 적용 직전 백업 이후 운영 쓰기가 재개되었다면 그 백업으로 승격하지 않는다. 다시 정상 종료·백업·계획 발급을 수행한다. 이번 준비 리허설의 백업은 후일 승격 시점의 최신 백업을 대신하지 않는다.

백업의 `source.zip`은 백업 시점 작업 폴더의 소스 증거이며 V10 소스라는 의미가 아니다. 복구 실행의 기준은 별도 SHA-256으로 검증한 승인 V10 JAR/UI/설정이다. DB와 계정·첨부가 포함된 백업은 Git 제외 폴더에 보관한다.

## 2. 계획 발급과 승격

아래 변수는 운영자가 실제 승인 경로와 해시로 설정한다. 예시는 절차 설명이며 이 문서 작성 중 운영에 실행하지 않는다. 실행 전에 릴리스 manifest의 모든 해시를 확인한다.

```powershell
# $taskPython, $taskJava: ui-launch.json에서 확인한 승인 실행 환경
# $taskRelease: 검증한 V11-RC1-20260929 절대 경로
# $taskDb: 정상 종료된 적용 대상 .mv.db의 절대 경로
# $taskCapture: 바로 직전 전체 백업 폴더(backup/와 backup-manifest.json 포함)
# $taskPlan, $taskRun: 새 파일/폴더의 절대 경로
$taskRc = Join-Path $taskRelease 'runtime/v11-rc.jar'
$taskTool = Join-Path $taskRelease 'tools/v11/promotion.py'
$taskRcHash = '5be79fd5ce32e348e6ddf0f3b5acd5f32bebd0441802ac3356d64deca800906e'
& $taskPython -B $taskTool plan --java $taskJava --rc $taskRc --rc-sha256 $taskRcHash --db $taskDb --output $taskPlan
# 위 명령 성공 여부와 생성된 계획 내용을 검토하고 해시를 승인 기록에 고정한다.
$taskPlanHash = (Get-FileHash -LiteralPath $taskPlan -Algorithm SHA256).Hash.ToLowerInvariant()
$taskBackup = Join-Path $taskCapture 'backup-manifest.json'
$taskBackupHash = (Get-FileHash -LiteralPath $taskBackup -Algorithm SHA256).Hash.ToLowerInvariant()
```

계획에는 DB 절대 경로·URL·파일 hash, 기존 모든 테이블 fingerprint/컬럼, V1~V11 manifest, RC hash, 24시간 유효기간이 들어간다. DB·JAR·계획이 변하거나 만료되면 새 계획을 발급한다.

```powershell
# 아래는 실제 승격에 대한 별도 승인 후에만 실행한다.
& $taskPython -B $taskTool migrate --java $taskJava --rc $taskRc --rc-sha256 $taskRcHash --plan $taskPlan --approved-plan-sha $taskPlanHash --backup-manifest $taskBackup --backup-manifest-sha256 $taskBackupHash --run-dir $taskRun --authorize-original
```

사본 리허설에서는 `--authorize-original`을 쓰지 않는다. 운영 대상은 해당 옵션뿐 아니라 정확한 계획·백업·RC 검증도 통과해야 한다. 도구는 외부 migration/JVM 설정을 거부하고 일회용 `.spent` 기록으로 재실행을 차단한다. 실패한 계획을 수동 수정하거나 재사용하지 않는다.

## 3. MIGRATED_V11 receipt 발급 조건

다음 조건을 모두 통과한 도구만 `runtime-receipt.json`을 새로 발급한다.

1. 대상이 승인한 정확한 V10 DB이며 계획·JAR·전체 백업 hash가 일치한다.
2. pending migration이 `V11__post_trash.sql` 한 개다.
3. 실제 적용 건수가 1이고 Flyway validation이 성공한다.
4. 기존 모든 컬럼의 값과 첨부 BLOB을 포함한 fingerprint가 동일하다.
5. 추가 테이블은 `POST_TRASH`뿐이며 초기 행 수는 0이다.
6. 연결을 종료한 후 cold inspect에서도 V1~V11 checksum과 기존 데이터가 동일하다.

receipt는 `status=MIGRATED_V11`, `approvalKind=V10_TO_V11`, DB 절대 경로, RC SHA-256, 승인 계획 SHA-256, 실제 migration 1건, `legacyDataPreserved=true`, `baselineCreated=0`, `businessWrites=0`, migration 목록과 cold 증거를 포함한다. V10 receipt를 편집해 만들지 않는다.

## 4. 정상 실행과 공개 전 확인

```powershell
# 별도 승격 승인 및 migration 성공 후 실행한다. V10 외부 UI 환경 변수는 없어야 한다.
& $taskPython -B $taskTool serve --java $taskJava --rc $taskRc --rc-sha256 $taskRcHash --run-dir $taskRun --port 8095
```

정상 실행은 receipt를 확인하고 schema를 검증만 한다. `copy-validation`을 사용하지 않으며 자동 migration하지 않는다. 웹 실행에 일회성 migration flag를 남기면 시작을 거부한다. 실행 evidence에는 실제 PID·DB·JAR hash가 기록된다.

리허설에서는 새 테스트 계정/글을 별도 사본에만 만들고 React 작성·수정·게시·재게시, 휴지통/복원/재게시/영구삭제, 첨부·이력 보존, 정상 종료·cold inspect·재시작을 검사한다. 운영 공개 전에는 로그인 화면·기존 공개 데이터 조회 등 읽기 검증과 정상 종료·cold 검사·재기동을 수행한다. 운영 원본에 리허설 fixture를 넣지 않는다. 최종 쓰기 재개는 운영자가 결정한다.

## 5. V10 전체 묶음 rollback

1. 쓰기 재개 전에 실패하면 즉시 작업을 중지한다. 쓰기 재개 후라면 새 V11 작성 내용이 과거 V10 DB에 없으므로 먼저 쓰기를 닫고 차이 보존·재입력 방침을 결정한다.
2. V11 서버를 정상 종료하고 DB 단독 접근 가능 여부를 확인한다.
3. 적용 직전 V10 전체 백업의 모든 파일 hash와 cold fingerprint를 다시 검사한다. 도구는 검증 전에 복구 대상 DB를 바꾸지 않는다.
4. 아래 도구는 현재 V11 DB/JAR/receipt를 별도 evidence에 보존한 후, 지정한 DB 파일 하나를 V10 백업과 바이트 단위로 같게 복구하고 cold 검증한다.

```powershell
# 실제 운영 rollback에 대한 승인 범위 안에서만 실행한다.
& $taskPython -B $taskTool rollback --java $taskJava --rc $taskRc --rc-sha256 $taskRcHash --run-dir $taskRun --authorize-original
```

5. DB만 되돌리고 V11 JAR을 실행하면 안 된다. 보존한 **승인 V10 JAR + 당시 외부 UI + 시작 설정 + V10 receipt/run + 도구**로 실행을 전환한다. 이번 설계는 기존 묶음을 덮어쓰지 않으므로 원래 경로의 V10 `start-ui.ps1`을 재사용할 수 있다. 파일을 복원할 때는 확인한 개별 절대 경로만 대상으로 한다.
6. 원래 DB 경로로 복구하면 당시 V10 receipt를 그대로 사용한다. 격리 사본처럼 경로가 달라졌다면 승인 V10 도구의 `relocate-plan`/`relocate-approve`로 읽기 전용 V10 relocation receipt를 발급한다. receipt의 경로를 수동 편집하지 않는다.
7. V10 정상 기동·로그인 화면·공개 API·UI 파일을 확인하고 정상 종료한다. cold 검사에서 schema V10과 기존 모든 fingerprint를 확인한 후 원래 운영 구성을 다시 켠다. 그때까지 쓰기를 재개하지 않는다.

## 중단 기준

해시/계획/경로 불일치, 만료, 열린 DB, V1~V10 checksum 차이, V11 외 pending, migration 실패, 기존 데이터 차이, receipt 누락, 정상 종료 증거 누락, 롤백 실패 중 하나라도 발생하면 FAIL로 처리한다. 실제 승격 여부는 [리허설 결과](V11_REHEARSAL_RESULTS.md)를 검토한 뒤 별도로 결정한다.
