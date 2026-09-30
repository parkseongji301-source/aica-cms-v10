# V12 JAR 교체 (같은 DB, JAR별 receipt)

스키마 변경 없이 V12 실행본의 JAR만 바꾸는 절차다. 첫 사용은 React 전환 5단계의 RC1 → RC2 교체다([5단계](REACT_ADMIN_STEP5.md)). DB migration·schema 변경·DB 쓰기는 하지 않는다. 서버 코드와 `FileDatabaseSafety` 검사는 바꾸지 않았다.

## 방식

서버는 시작할 때 receipt의 상태(`MIGRATED_V12`), DB 실제 경로, 실행 JAR 해시, `AUTO_COMPACT_FILL_RATE=0`을 확인한다. 그래서 RC1 receipt로는 RC2 JAR을 시작할 수 없다. RC2 실행본 폴더에 **RC2 전용 receipt**를 따로 발급하고, RC2는 `runtime.json`의 `database`로 **RC1 폴더의 같은 DB 파일**을 쓴다. RC1 receipt는 그대로 남으므로 RC1로 되돌려도 같은 DB에서 바로 시작할 수 있고, RC2에서 작성한 내용도 잃지 않는다.

필수 조건:

- **두 runtime 동시 실행 금지**: 실행 스크립트는 같은 DB 경로를 명령줄에 가진 Java 프로세스가 있으면 시작을 거부한다. H2 파일 잠금과 독점 열기 검사도 그대로 있다.
- **V1~V12 checksum 동일**: 두 JAR의 `db/migration` 항목(SQL 12개와 V8 Java migration 클래스)이 이름과 바이트까지 같아야 한다. 이어서 RC2 JAR로 읽기 전용 Flyway `validate`를 실행해 현재 V12, 대기 migration 0을 확인한다.
- **schema·데이터 호환성**: DB 파일 해시가 마지막 cold 검사(정상 종료 `stopped-*.json` 또는 이관·교체 검사)와 같아야 하고, RC2 JAR의 읽기 전용 검사 결과(migration 이력, 테이블·열, 표별 행 수·지문)가 그 cold 검사와 같아야 한다. 검사 전후 DB 바이트도 같아야 한다.

## 도구

| 파일 | 역할 |
|---|---|
| `scripts/start-v12-runtime.ps1` | `runtime.json`의 `database`(없으면 기존처럼 `db/aica-local.mv.db`)를 쓴다. 같은 DB를 쓰는 Java 프로세스가 있으면 시작을 거부한다 |
| `scripts/swap-v12-jar.ps1 -Source <RC1 runtime> -Target <RC2 runtime> -AuthorizeJarSwap` | 위 조건을 모두 검사한 뒤 RC2 receipt를 발급한다. RC1 receipt는 바꾸지 않는다. 같은 receipt가 이미 있으면 그대로 두고, 다른 receipt가 있으면 거부한다 |
| `scripts/select-v12-runtime.ps1 -Runtime <runtime> -AuthorizeSelection` | `START.cmd`/`STOP.cmd`가 쓰는 `.cache/current-ui.json`을 바꾼다. 현재·선택 runtime이 실행 중이거나 어떤 Java 프로세스가 DB를 쓰면 거부한다. 이전 파일은 `current-ui.before-<시각>.json`으로 보관한다 |

RC2 receipt에는 교체 대상(RC1 폴더·JAR 해시·receipt 해시), 사용한 cold 검사 파일과 해시, RC2 검사 파일과 해시, migration 항목별 SHA-256, 검사 결과(`after`)를 남긴다.

`relocate-v12-runtime.ps1`은 DB를 가진 runtime만 옮긴다. `database`를 쓰는 runtime은 거부한다(DB를 가진 runtime을 옮긴 뒤 교체를 다시 발급한다).

**검사 비교 수정**: V12 검사 파일의 표별 지문은 Java `Map.of`로 출력되어 JVM 실행마다 키 순서(`rows`, `sha256`)가 바뀐다. `relocate-v12-runtime.ps1`은 JSON 문자열을 그대로 비교해서, 값이 같아도 키 순서가 다르면 거부할 수 있었다(리허설에서 실제로 발생). 두 스크립트 모두 객체 키를 정렬한 뒤 비교하도록 고쳤다. 배열 순서(migration 이력 순서)는 그대로 비교한다.

## 실제 적용 절차 (8095)

1. `STOP.cmd`로 RC1을 정상 종료한다. 종료 시 `stopped-<시각>.json`이 남는다.
2. 백업: `.cache/v12-release/V12-RC1-20260930/runtime/db/aica-local.mv.db`를 날짜 붙은 이름으로 복사하고 해시가 방금 종료 기록과 같은지 확인한다.
3. 교체 receipt 발급:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\swap-v12-jar.ps1 -Source .cache\v12-release\V12-RC1-20260930\runtime -Target .cache\v12-release\V12-RC2-20260930\runtime -AuthorizeJarSwap
   ```
4. 선택과 시작:
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\select-v12-runtime.ps1 -Runtime .cache\v12-release\V12-RC2-20260930\runtime -AuthorizeSelection
   ```
   그 다음 `START.cmd`.
5. 확인: 로그인 → `/admin`이 React, 3개 역할 업무, `STOP.cmd`/`START.cmd` 재시작.

**rollback**: `STOP.cmd` → `select-v12-runtime.ps1 -Runtime .cache\v12-release\V12-RC1-20260930\runtime -AuthorizeSelection` → `START.cmd`. DB와 두 receipt는 그대로다.

## 8095 적용 결과 (2026-09-30, PASS)

RC1(`3ba3a701…`) → RC2(`90718f0a…`). 자세한 결과와 3개 역할 검증은 [5단계 결과](REACT_ADMIN_STEP5.md)의 "8095 적용 결과"에 있다.

| 검사 | 결과 |
|---|---|
| RC1 정상 종료, DB = 마지막 종료 기록(`stopped-1790765329895.json`, `3ea4fe5b…0c32`) | 통과 |
| 백업 `.cache/v12-release/backups/aica-local.before-RC2-20260930-stopped-1790765329895.mv.db` | 해시 동일 |
| `db/migration` 항목 15개 바이트 동일, RC2 JAR Flyway validate 12개, 검사 결과 = 종료 기록 | 통과, receipt 발급 |
| RC1 receipt | 변경 없음(`edc6fa7b…`), rollback 가능 |
| RC2 선택·START → 정상 종료 → 재시작 | 통과. 종료 검사에서 migration 이력 동일, 37개 표의 행 수·지문 변화 0 |
| 3개 역할 검증 | PASS. 검증용 임시 계정 ADMIN #33(`rc2-check-admin@example.com`), SUPPORTER #34(`rc2-check-supporter@example.com`)은 확인 후 사용 중지 |

## 리허설 결과 (2026-09-30, PASS)

8095(RC1)는 멈추지 않았다. 보관 중인 V12 개발 실행본(`.cache/runtime-v12-writing-templates`, 백업 전용)을 **복사**해 `.cache/v12-rehearsal-RC2-20260930/`에서 진행했다. 원본 DB 해시(`5301809b…19b1`)는 끝까지 같았다.

| 순서 | 결과 |
|---|---|
| 복사본 이관(`relocate-v12-runtime.ps1`) | 처음에는 지문 키 순서 때문에 거부 → 비교 수정 후 통과 |
| 교체 #1: 개발 JAR(`8e18dbb5…`) → RC1 JAR(`3ba3a701…`) | 통과. Flyway 12개 validate, receipt 발급 |
| 리허설 계정 3개(SUPER_ADMIN·ADMIN·SUPPORTER)를 복사본 DB에 추가한 뒤 교체 #2 시도 | **거부**: "Database changed after the last normal stop" (의도된 동작) |
| RC1 JAR로 시작·정상 종료(8096) → 교체 #2: RC1 JAR → RC2 JAR(`90718f0a…`) | 통과. migration 항목 15개 바이트 동일, Flyway 12개 validate |
| RC2 실행 중 RC1 시작 시도 | **거부**: "Another process is using this database" |
| RC2 실행 중 runtime 선택 시도 | **거부** |
| RC2(8097) 3개 역할 HTTP 확인 | 역할마다 React 주소 18개 200(세 역할의 화면 파일 1종), 기존 화면 11개·API 18개가 기대 권한과 일치, `/admin-next` 302(쿼리 보존), `/admin/posts/new` 302, 실패 0 |
| RC2에서 쓰기 | ADMIN React API 초안 저장 201(#138), SUPPORTER 기존 폼 저장 → `/admin/legacy/posts/139` |
| RC2 정상 종료 → 재시작 → 재확인 → 정상 종료 | 통과. 실패 0, 글 수 21 → 23 유지 |
| rollback: RC1 선택 → RC1 시작(8096) | RC1이 #138·#139를 읽음(DRAFT), 글 수 23. 정상 종료 |
| 교체 재실행 | "already binds", 변경 없음 |
| RC2 다시 선택 → 시작 → 정상 종료 | 통과 |
