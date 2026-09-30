# V12 실행본 다른 PC·경로로 옮기기

2026-09-30 사용자 요청으로 추가했다. V12 실행본(`.cache/v12-release/V12-RC1-20260930/runtime`)은 receipt의 `databasePath`에 DB 절대경로를 고정하고, 서버는 시작할 때 실제 경로와 다르면 `DB safety STOP: receipt database path mismatch`로 거부한다. 파일만 복사하면 다른 경로에서 열리지 않는다.

V11은 이관 묶음의 `transfer_v11.py`가 새 위치에서 DB를 검사하고 receipt를 다시 발급했다. 그 도구는 V11(이력 1~11, `MIGRATED_V11`, V11 폴더 구조) 전용이라 V12에는 쓸 수 없다. `scripts/relocate-v12-runtime.ps1`이 같은 원칙을 V12 구조에 적용한다. 서버 코드와 `FileDatabaseSafety` 검사는 바꾸지 않았다.

## 절차

원래 PC:

1. `STOP.cmd`로 정상 종료한다. 종료 시 `V12PromotionTool inspect`가 `stopped-<시각>.json`을 남긴다.
2. 다음을 경로 그대로 복사한다. 실제 DB와 계정이 들어 있으므로 Git에 올리지 않는다.
   - `.cache/v12-release/V12-RC1-20260930/` 전체
   - `.cache/current-ui.json`
   - JDK 17이 없는 PC라면 JDK 폴더(예: `.cache/jdk17/`)

새 PC(저장소 루트):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\relocate-v12-runtime.ps1 -Runtime .cache\v12-release\V12-RC1-20260930\runtime -Java .cache\jdk17\bin\java.exe -AuthorizeRelocation
```

그 다음 `START.cmd`. 폴더를 다시 옮기면 같은 명령을 다시 실행한다. 이미 맞는 경로면 receipt를 바꾸지 않는다.

## 검사 내용

receipt를 다시 쓰기 전에 모두 통과해야 한다.

- JAR·정상 종료 도구 해시가 `runtime.json`과 같다. receipt가 `MIGRATED_V12`, 같은 JAR, `AUTO_COMPACT_FILL_RATE=0`이다.
- 서버가 실행 중이 아니고 DB를 독점으로 열 수 있다. 경로 불일치로 거부된 시작은 DB를 열지 않으므로 `active.json`이 RUNNING이어도 해당 프로세스가 없으면 진행한다.
- DB 파일 SHA-256이 마지막 정상 종료 검사(`stopped-*.json`)와 같다. 즉 정상 종료 뒤 바뀌지 않았고 복사가 온전하다.
- 그 검사의 migration 이력·테이블 구조가 receipt의 V12 migration 결과와 같다.
- 새 경로에서 다시 `inspect`한 이력·구조·행 fingerprint가 정상 종료 검사와 같고, 검사 전후 DB 바이트가 같다.

통과하면 이전 receipt를 `migration-receipt.before-relocation-<시각>.json`으로 보관하고, `databasePath`만 새 경로로 바꾸며 `relocations`에 이전 경로·검사 파일·해시를 남긴다. `-Java`를 주면 `runtime.json`의 `java`를 바꾸고 이전 파일을 보관한다. DB는 수정하거나 migration하지 않는다. 경로에 junction/symlink가 있으면 거부한다(Java `toRealPath`와 같은 문자열을 보장하기 위해).

## 검사 비교 수정 (2026-09-30)

검사 파일의 표별 지문은 Java `Map.of`로 출력되어 JVM 실행마다 키 순서가 바뀐다. 이전 스크립트는 JSON 문자열을 그대로 비교해서, DB가 같아도 키 순서가 다르면 "Relocated database differs from the normal-stop inspection: fingerprints"로 거부할 수 있었다. 객체 키를 정렬한 뒤 비교하도록 고쳤다(배열 순서는 그대로 비교). V12 JAR 교체 리허설에서 발견했다.

## 검증 (2026-09-30)

회사 PC에서 정상 종료한 RC1을 ZIP으로 묶어 다른 경로(scratchpad)에 풀고 포트만 8098로 바꿔 확인했다. 원본 8095 실행본은 건드리지 않았다.

1. 이관 전 시작: 경로 불일치로 거부 (의도된 동작)
2. 이관: receipt 재발급, java 경로 갱신
3. 재실행: "이미 맞는 경로", 변경 없음
4. `START`: `/login` 200, `/api/public/v1/menus` 200, 관리자 화면 302(로그인 이동)
5. `STOP`: 정상 종료, cold 검사 통과, `active.json` STOPPED
