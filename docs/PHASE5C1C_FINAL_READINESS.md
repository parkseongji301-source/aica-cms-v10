# 5C-1C 최종 전환 준비 및 동일 조건 리허설 결과

2026-09-27. **사본 검증 완료. 실제 원본 V3는 변경하지 않았고, 5C-2는 실행하지 않았다.** 새 CMS 기능·UI·migration·의존성 변경은 없다.

최종 실행물은 `.cache/phase5c1c/20260927-223140/release-final/`에 보존했다. [실행·STOP·복구 절차](PHASE5C2_FINAL_RUNBOOK.md)를 사용한다. 이전 5C-1의 copy-promote 검토 문서는 현재 실행 절차가 아니다.

## 1. H2 회피 설정

H2 **2.3.232 유지**, Flyway **10.20.1 유지**. 기존 RC와 비교해 두 dependency JAR 및 V1~V10 migration의 SQL/Java class 바이트가 동일함을 확인했다.

`AUTO_COMPACT_FILL_RATE=0` 적용 위치:

- RC 내 one-shot `CutoverTool`의 V3→V10 쓰기 연결.
- 정상 V10 서버: `FileRuntimeConfiguration`에서 실제 Hikari URL을 첫 연결 전에 검사.
- `run-*-copy.ps1`, 분류/후기/FAQ seed 사본 스크립트, dev 기본 URL.
- 최종 `run`/`serve`, rollback 후 검증, smoke 사본 유지보수 도구.
- file DB migration/재시작 자동 테스트와 이전 사본 audit 도구.

`run-local.ps1`과 그 V3 실행 JAR는 그대로다. 최종 rollback은 5C-1B에서 검증한 V3 JAR와 그 당시의 명시적 URL 설정을 사용한다. 5C-1B의 default-compaction 재현 도구는 격리 사본 전용 조사 자료이며 운영 실행 경로에서 제외했다. 진단용 H2 패치는 RC에 포함하지 않았다.

## 2. 누락과 우회 차단

파일 DB는 절대 경로, `IFEXISTS=TRUE`, `DB_CLOSE_ON_EXIT=FALSE`, `AUTO_COMPACT_FILL_RATE=0`을 요구한다. 누락·잘못된 값·중복 옵션·미허용 옵션은 연결 전 거부한다.

Hikari override가 datasource URL과 다르거나, 별도 Flyway URL·다른 driver/dataSourceProperties·압축 설정을 바꾸는 init SQL·파일 DB 초기화 SQL·다른 migration 경로가 있으면 거부한다. 비밀번호는 로그/승인 문서에 넣지 않는다.

정상 파일 runtime은 V10을 읽기 전용으로 확인한 후 pool을 연다. **정상 서버는 validate-only**이며 V3를 자동 migration하지 않는다. 메모리 테스트 DB만 기존 migrate 동작을 유지한다.

## 3. 원본 승인 guard와 수명

원본은 기본 차단한다. 명시적 전환 도구에서만 다음을 모두 확인한다.

| 조건 | 확인 방식 |
|---|---|
| 일회성 cutover flag | migration 자식 프로세스에만 `AICA_CUTOVER_ENABLED=true` |
| 승인 문서 | 별도로 지정한 승인 JSON SHA-256, 24시간 만료, `.spent` 재사용 거부 |
| 정확한 DB | 정규화 절대 `.mv.db` 경로와 JDBC base 일치 |
| 적용 직전 상태 | 파일 SHA-256 + 모든 기존 테이블 fingerprint + writer 연결 직후 재확인 |
| V3 기준점 | 현재 V3 및 V1~V3 script/checksum 일치 |
| 실행 RC | 실제 single-JAR classpath의 SHA-256 일치 |
| migration | classpath 검색 위치와 V1~V10 목록/checksum 일치 |
| 파일 DB 회피 설정 | 실제 쓰기 URL의 `AUTO_COMPACT_FILL_RATE=0` |

승인은 성공 여부와 무관하게 한 번만 소비된다. migration 완료 후 전환 프로세스가 종료되고, 정상 서버에는 flag를 전달하지 않는다. 원본 정상 실행에는 경로·RC SHA에 묶인 성공 receipt와 V10 schema 검증이 필요하다. receipt가 남아 있어도 V3로 복원된 DB에는 V10 서버가 시작되지 않는다.

## 4. 실제 사용할 실행 경로

`scripts/cutover/cutover.py`의 `plan → run → serve / rollback`이다. 실제 원본에서는 별도 사용자 승인 후 `--authorize-original-cutover`를 추가해야 하며 나머지 guard는 동일하다.

최종 사본에서 **리허설 자동복구 옵션 없이 `run`, `serve`, `rollback`을 각각 실행**했다. 대상 경로에 대한 승인값 외에 migration을 우회하는 테스트 flag는 사용하지 않았다.

실행 순서: DB 독점 접근 → 최신 V3 DB/대응 JAR 백업 → hash/fingerprint → 동일 백업 last-copy migration → 대상 재확인 및 guarded migration → cold V10 검사 → 사전 DEFER 확인 → 정상 서버/baseline → 저장/종료/readonly/재시작 → 별도 smoke 사본 → 정상 서버 진입 확인 → rollback.

운영 사전은 **DEFER**다. 기존 콘텐츠 GENERAL 초기화 외의 기수·주제는 대상 DB에 등록하지 않았다. 사전 fixture는 smoke 사본에만 등록했다. 운영 사전 확정이 필요하면 별도 작업으로 검증하며 이번 실행물을 임의로 수정하지 않는다.

## 5. V3→V10 동일 조건 결과

- 첫 구현 리허설 후 복원 전 백업 검사 순서를 강화했다. **최종 스크립트로 새 V3 사본 2개에서 다시 전체 흐름을 완료**했다.
- 각 사본에서 last-copy와 대상 모두 V4→V5→V6→V7→V8→V9→V10을 같은 RC/도구로 적용했다.
- 각 migration마다 기존 테이블의 원래 컬럼 값 및 ID/category/계정/메뉴/발행본 값을 확인했다. 페이지 JSON은 V8의 ID/schemaVersion/variation 추가 외 기존 내용을 보존했다.
- baseline: 콘텐츠 **9개 snapshot**, 페이지 **4개 snapshot**, 템플릿 **0개**. 재실행은 전부 **0개**.
- 최종 대상의 운영 기수/주제는 **0개**. fixture가 대상에 유입되지 않았다.

## 6. 반복 종료·재연결

최종 두 사본에서 각각 **동일 내용 명시적 저장 4회 + 서버 기동 5회**, 합계 저장 8회/기동 10회를 수행했다. 명시적 저장은 page 65의 내용을 바꾸지 않고 실제 version 행을 추가한다.

| 확인 시점 | page 65 version 수 | Flyway | 공개본 |
|---|---:|---|---|
| baseline 후 첫 저장/종료 | 3 | V10 | 동일 |
| 두 번째 저장/종료 | 4 | V10 | 동일 |
| 세 번째 저장/종료 | 5 | V10 | 동일 |
| 네 번째 저장/종료 | 6 | V10 | 동일 |
| 다섯 번째 기동·재조회·종료 | 6 | V10 | 동일 |

매번 같은 절대 경로·RC SHA·설정·PID를 기록하고 Spring/Hikari 정상 종료를 확인했다. 종료 후 Windows 독점 파일 접근이 성공했고, 다른 JVM의 readonly 검사에서 V10과 최신 테이블 fingerprint를 확인했다. 서버 종료 시 hash와 readonly 검사 hash가 같았다.

증거: `final-rehearsal/evidence/cycle-*-{process,api,closed,cold}.json`, `production-path-rehearsal/evidence/` 및 `evidence/final-verification.json`.

## 7. CMS·공개 API smoke와 rollback

같은 RC의 별도 smoke 사본에서 다음을 검증했다.

- 실제 HTTP 로그인/세션/CSRF 및 SUPER_ADMIN/ADMIN/SUPPORTER 권한 허용·거부.
- 콘텐츠/페이지 초안 저장·발행·충돌 검사, 자동저장 version 미생성, 명시 저장 version 생성.
- 블록 ID/variation/visible, POSTS category/query/manual 및 직접 순서.
- REVIEW/FAQ 생활 주제 격리, 복수 분류, RESTAURANT 주소 snapshot.
- 콘텐츠와 페이지의 초안 변경은 공개 API에 반영되지 않고 재발행 후 반영.
- 템플릿의 설정 복사/새 ID/페이지 독립성, 버전 복구, 템플릿·version 미디어 삭제 보호.
- smoke 서버 종료·재시작 후 편집 데이터/버전/공개 결과 동일.

rollback은 V10 파일과 RC 보존 → **복원할 백업을 먼저 검증** → V3 DB 및 대응 V3 JAR 복원 → 로그인/조회 → 정상 종료 → V3 이력/모든 기존 테이블 fingerprint 일치를 확인했다. 자동 리허설 rollback과 개별 `rollback` 명령 모두 통과했다.

추가 실패 시험:

- 손상된 V3 백업: 대상 파일 교체 전에 거부, 대상 V10 hash 불변.
- V3 복원 후 기존 V10 receipt로 `serve`: 시작 거부, V3 hash 불변.

실제 공개 홈페이지는 아직 없어 **홈페이지 렌더링 E2E는 미완료**다. CMS/공개 API smoke를 홈페이지 E2E로 표시하지 않는다.

## 8. 테스트와 보존 근거

| 검증 | 결과 |
|---|---|
| 전체 Java 회귀 | 160개 중 일반 실행 155개 통과, 조건부 5개 별도 실행 |
| 조건부 사본 migration | 11개 통과/실패 0/skip 0 — 위 조건부 5개 전부 포함 |
| 최신 원본 V3 사본 → V6 | 전체 회귀에 포함하여 실행/통과 |
| V3 사본 → V10 전체 chain | 조건부 테스트에서 실행/통과 |
| 실제 RC guard 거부 | 18개 조건 통과, V3 파일 바이트 불변 |
| rollback 실패 조건 | 손상 백업, V3에 V10 재연결 차단 통과 |
| React | 39개 통과, TypeScript/production build 통과 |
| 검증 서버 | 최종 Java 프로세스 0개 |

따라서 전체 160개 Java 테스트 항목을 실제 실행했다. 조건부 테스트가 생략된 상태를 전체 통과로 계산하지 않았다.

원본은 이번 단계에서 JDBC로 열지 않았다. 새 V3 백업의 이력을 검사했고, 해당 백업과 원본의 byte hash가 일치한다. 시작/종료 후 원본 hash:

```text
.local-data/aica-local.mv.db
55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe

.local-data/backoffice.mv.db
2a9ef304a1407a663f903e8924b1e7326c241033bd9e4c002a30b3f95c9b8a90

V10 RC
606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e

V3 rollback runtime
c20f0f4d92e1867fbdf326c30b67949ceedf71aa3c0a2d597dc4432e1201cf6d
```

기존 수정·미추적 파일은 유지했고, 작업 전 source/checkpoint 및 최종 source/runtime/script manifest를 함께 보존했다.

주요 변경: `FileDatabaseSafety`, `CutoverTool`, `FileRuntimeConfiguration`, 기존 `ClassificationMigrationConfiguration`, 파일 DB 실행/seed 스크립트, cutover 도구·runbook, guard/재시작 테스트. 업무 서비스·권한·CMS UI와 기존 migration은 변경하지 않았다.

## 9. 잔여 위험과 5C-2 판단

**현재 검증 범위의 기술 blocker는 남아 있지 않다.** 원본을 아직 V3로 유지하는 조건을 지켰다. 검증한 RC·옵션·사전 DEFER·도구를 그대로 사용하고, 실제 전환 시점의 최신 백업과 새 승인을 만들면 5C-2 실행 준비가 되어 있다. 이번 결과 자체가 원본 실행 승인은 아니다.

- H2 자동 종료 압축을 끈 회피책이므로 파일 크기 증가를 고려한다. 공식 수정 H2 업그레이드와 workaround 제거 판단은 별도 작업이다.
- Flyway의 H2 2.3.232 지원 경고(최종 지원 검증 버전 2.2.224)는 여전히 출력된다. migrate/validate 반복 성공과 지원 경고를 함께 기록하며, persistence bug와 동일 원인으로 취급하지 않는다.
- 운영 사전 등록, 실제 홈페이지 렌더링 E2E, 외부 서비스 공개·OneDrive 장기 운영/백업 정책은 별도 결정·검증 사항이다.
- 실제 운영 쓰기를 재개한 뒤 rollback하면 그 이후 V10 작업은 V3 백업에 자동 포함되지 않는다. 보존한 V10 데이터에 대한 별도 판단이 필요하다.
