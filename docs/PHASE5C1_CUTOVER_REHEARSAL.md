# 5C-1 최종 전환 리허설 결과 — 2026-09-27

**판정: 사본에서 전환·기능·V3 롤백 검증을 수행했다. 5C-2 원본 전환은 보류한다.**
정상 경로의 V3→V10 및 복원은 통과했지만, 별도 연결 수명 시험에서 닫기/재연결 후 변경 이력이 유지되지 않는 현상을 다시 재현했다. 이를 성공 결과에 합쳐 숨기지 않는다.

원본 `.local-data/aica-local.mv.db`는 V3 그대로이며, JDBC로 원본을 열지 않았다. 원본 두 파일의 작업 전후 SHA-256이 같다. 애플리케이션 Java/React/Mapper/SQL migration/의존성은 변경하지 않았다. 이번 추가물은 `workbench/cutover-rehearsal` 도구와 이 보고서·runbook이다.

## 1. 동결한 release candidate

기준점 디렉터리: `.cache/phase5c1/20260927-205122` (이하 `B`). `.cache/phase5c1-current.txt`에도 절대 경로를 기록했다.

| 항목 | 고정값 |
|---|---|
| Git HEAD | `9cc2b3f1710cc0cae9249b1c2e76619c94eb6945` |
| Working tree | 이전 단계의 수정·미추적 항목 유지. 시작 시 status 151개 항목을 기록. HEAD만으로 현재 구현을 재현할 수 없음 |
| 소스 | `B/release/source.zip`, 파일 318개 SHA manifest, working.patch, git-status.txt |
| V3 runtime | 기존 `20260927-000241-react-phase3b2a/phase3a-runtime.jar`의 동일 복사본, V1~V3만 포함 |
| V10 runtime | 5B-2B-2 완료 JAR의 동일 복사본. 재빌드하지 않음 |
| V10 JAR SHA-256 | `240f377fb0e86665d8de4668352e2de7cf9d83e3df267ab5d8648cd376294306` |
| V3 JAR SHA-256 | `c20f0f4d92e1867fbdf326c30b67949ceedf71aa3c0a2d597dc4432e1201cf6d` |
| source.zip SHA-256 | `075cfb5d35f286002b28d5b671fd219a53060f58ae51e0e59e2a69217713eef9` |
| Java / Spring | Temurin 17.0.20.1+1 / Boot 3.4.5 / Framework 6.2.6 |
| DB / migration / pool | H2 2.3.232 / Flyway 10.20.1 / HikariCP 5.1.0 |
| 프런트 진입 파일 | `static/next-app/index.html` → `index-CoqNfsMs.js`, `index-D03pTr5T.css` |
| migration 검색 경로 | JAR의 `classpath:db/migration/h2` |

`release/jar-entries.json`에 JAR 내부 migration·React 자산·설정·라이브러리 해시를 기록했다. 과거 빌드 자산도 JAR에 남아 있으며 삭제/정리하지 않았다. 실제 진입 파일이 가리키는 JS/CSS를 확인했다. `tools/runtime`은 이 JAR에서 추출한 파일이므로 현재 `target/classes`나 수정 가능한 소스 경로로 migration을 실행하지 않았다.

실행은 모두 `dev`, `127.0.0.1`, 명시적인 **사본** JDBC 경로, `IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE`, bootstrap=false를 사용했다. V10에만 copy-validation=true. V3 원본용 local initializer의 원본 경로 접근을 피하기 위해 사본 시험에서는 local profile을 사용하지 않았다. 설정과 명령 인수는 `evidence/*-arguments.json`에 보존했다. 검증용 preview=true는 운영 설정으로 확정한 것이 아니다.

## 2. H2/Flyway 경고 판단

실제 경고는 “H2 2.3.232가 이 Flyway의 테스트 범위보다 최신이며, 최신 지원 기준은 2.2.224”라는 내용이다. 고정 JAR의 `H2Database.ensureSupported` 바이트코드에서도 `recommendFlywayUpgradeIfNecessary("2.2.224")`를 확인했다 (`evidence/flyway-h2-supported-bytecode.txt`).

- 주 사본: V4~V10 각각 적용 1건, validate 성공, 같은 단계 재실행 0건. 완전히 닫은 후 독립 JVM에서도 V10 확인.
- 실제 V10 서버: 시작 시 validate 성공/추가 migration 0, 정상 종료·재시작 후 API와 버전·발행본 동일.
- 별도 연결 수명 사례에서는 재연결 뒤 V3로 돌아와 validate가 pending V4~V10으로 실패했다. **경고가 원인이라고 입증된 것은 아니다.**

경고 자체가 migration을 거부하지는 않지만, 현재 조합이 해당 Flyway의 검증 상한 밖이라는 사실은 남는다. 이 결과만으로 의존성 업그레이드가 반드시 문제를 해결한다고 판단할 수 없어 버전을 바꾸지 않았다. 원본 전환 전 별도 사본에서 H2/파일 환경/연결 수명 재현 원인을 좁혀야 한다. 업그레이드가 필요해지면 원본 전환과 분리해 검증한다.

공식 참고: [Redgate H2 지원 문서](https://documentation.red-gate.com/flyway/reference/database-driver-reference/h2), [H2 연결 수명·파일 잠금·백업 문서](https://h2database.github.io/html/features.html). 현재 공식 지원 표를 과거 10.20.1 바이너리의 지원 상한으로 대신 사용하지 않았다.

## 3. 파일 DB 연결 수명: 통과와 실패를 분리

| 시험 | 결과 / 증거 |
|---|---|
| 주 사본: 처음부터 연결 유지, 단계별 migration, 닫기/재연결, 별도 JVM | 통과. `migration-v3-v10.json`, `cutover-v10-independent.json` |
| 연결을 유지하지 않는 V3→V6 반복, 새 사본 3개 × 12회 | 재현 안 됨. 각각 V6/재적용 0 및 독립 JVM V6. `lifetime-1..3*.json` |
| 같은 JVM에서 V3를 열어 page 65 revision을 변경하고 닫은 뒤, 다시 열어 V4~V10 적용 | **실패 재현.** 재연결한 준비 revision부터 4로 되돌아갔고, migration 동안 V10이던 이력도 마지막 재연결과 독립 JVM에서 V3. `revision-fixture.log/json`, `revision-fixture-independent.json` |
| 위 상황에 명시적 `SHUTDOWN` 추가 | **동일 실패.** `shutdown-1.log/json`, `shutdown-1-independent.json`. 해결책으로 채택하지 않음 |
| revision 준비를 별도 JVM에서 완료·종료 → 독립 검사 → 별도 JVM migration | 통과. 준비 상태 draft revision 5 / publication 4, 최종 독립 JVM V10. `different-revision-pre.json`, `different-revision-separate.json`, `different-revision-post.json` |
| V3/V10 서버 실행 중 다른 JVM 접속 | H2 90020 파일 잠금으로 차단 |
| V3/V10 정상 종료 후 | Hikari 종료 완료, PID 없음, OS 독점 읽기 가능. 콜드 복사 가능 |

실패한 것은 이번 독립 JDBC/Flyway 도구의 **한 JVM 안에서의 여러 연결 수명** 시험이다. 실제 Hikari 서버의 정상 종료/재시작에서는 재현하지 않았다. 정확한 내부 원인을 Hikari, Flyway, H2 또는 OneDrive로 단정하지 않는다. 이번 경로가 OneDrive 아래라는 환경 사실도 기록한다.

`DB_CLOSE_ON_EXIT=FALSE`와 `DB_CLOSE_DELAY`는 다른 설정이다. 후자를 -1로 변경하거나 파일 잠금을 해제하는 우회는 하지 않았다. 정상 종료/백업/단일 프로세스/단계 후 독립 JVM 검증은 실패 감지·피해 제한 절차이지, 원인 제거의 증거가 아니다. **현재로서는 이 절차만으로 원본 전환을 승인할 만큼 충분하다고 판단하지 않는다.**

## 4. 전체 migration와 데이터 무결성

주 경로: 현재 원본 콜드 복사 → V3 JAR 8095 실행/로그인 → 정상 종료 → `backup/pre-cutover-v3.mv.db` → `data/cutover-v10.mv.db`에서 V4→V10 → 후보 사전 → V10 8096 → baseline → 정상 종료.

| 버전 | migration | Flyway checksum | 이번 적용 / 재실행 |
|---|---|---:|---|
| 1 | backoffice SQL | 379266581 | 기존 이력 그대로 |
| 2 | cms SQL | -925664058 | 기존 이력 그대로 |
| 3 | rich_editor SQL | -1610808639 | 기존 이력 그대로 |
| 4 | content_classification_schema | 1414704998 | 1 / 0 |
| 5 | content_classification_baseline | 1744004732 | 1 / 0 |
| 6 | content_classification_constraints | -285908275 | 1 / 0 |
| 7 | restaurant_details | 908107551 | 1 / 0 |
| 8 | page_block_identity (Java) | 804202609 | 1 / 0 |
| 9 | page_templates | -148566281 | 1 / 0 |
| 10 | document_versions | -1567156257 | 1 / 0 |

미완성 navigation V4는 workbench 밖의 runtime 검색 경로에 포함되지 않는다. V4 번호를 건너뛰지 않았고, 기존 migration checksum을 수정하지 않았다.

원본 업무 테이블 **16개**의 모든 기존 컬럼 값을 정렬·직렬화한 SHA-256과 행 수를 단계마다 비교했다. 미디어 binary도 해시에 포함한다. Flyway 기존 행 전체도 별도 비교했다.

| 기존 테이블 | 원본 행 수 |
|---|---:|
| posts / post_publications | 9 / 0 |
| site_pages / page_publications | 2 / 2 |
| categories / site_menus | 3 / 2 |
| users / activity_log | 2 / 38 |
| site_settings / site_links | 12 / 0 |
| media / post_media / post_publication_media / page_media | 각 0 |
| account_guard / cms_guard | 각 1 |

ID, 본문, category_id, 메뉴 대상, 계정/역할/비밀번호 해시, revision, 설정, 시각 등 기존 값은 그대로였다. 이번 최신 원본에는 콘텐츠 publication과 미디어가 0건이라, 해당 기존 데이터 migration 비교는 빈 테이블 보존 검증이다. 이후 비어 있지 않은 발행본·미디어 기능은 **별도 smoke fixture**로 확인했다. 이를 원래 운영 데이터가 있었다고 표현하지 않는다.

예외는 V8의 `sections_json` 메타데이터 추가뿐이다. id/schemaVersion/variation 세 필드만 제거한 JSON은 원본과 완전히 같다. 페이지 1의 2개 블록, 65의 1개 블록과 두 publication의 내용/순서/visibility/category 설정을 보존했다.

## 5. V8 상세

- 원본의 같은 revision·같은 snapshot인 초안/발행본은 같은 UUID block ID를 공유한다.
- 다른 revision 시험은 **별도 프로세스로 준비한 검증 사본**에서 수행했다. page 65 초안 revision 5 / 발행본 4가 실제로 남은 것을 확인한 뒤 변환했다. 본문이 같아도 서로 다른 ID를 받고 발행본에만 남은 ID는 retired 기록이 됐다.
- UUID v4 형태, 중복 없음, schemaVersion=2, variation=default 확인.
- V8 변환 함수를 다시 호출해도 전체 테이블 fingerprint 불변. Flyway 재실행도 0건.
- 주 사본의 block ID는 서버 재시작 이후에도 동일.

연결 수명 실패 사본은 서로 다른 revision의 성공 근거로 쓰지 않았다. 해당 준비 변경 자체가 유지되지 않았기 때문이다.

## 6. 운영 사전 최종 추천 — 아직 원본 승인 아님

| 기준 | 추천 코드 / 이름 |
|---|---|
| 등록형 콘텐츠 유형 | GENERAL / 일반, REVIEW / 후기, RESTAURANT / 맛집, INTERVIEW / 인터뷰, FAQ / FAQ |
| 기수 | COHORT_06 / 6기, COHORT_07 / 7기 |
| REVIEW 주제 | REVIEW_LIFE / 생활, REVIEW_CLASS / 수업, REVIEW_PROJECT / 프로젝트 |
| FAQ 주제 | FAQ_PREPARATION / 준비사항, FAQ_APPLICATION / 지원·선발, FAQ_CLASS / 수업, FAQ_LIFE / 생활, FAQ_EMPLOYMENT / 취업, FAQ_ALLOWANCE / 지원금, FAQ_PROJECT / 프로젝트 |

현재 확인된 IA와 3C 편집 흐름을 충족하는 최소 목록이다. 신규 맛집/인터뷰 주제나 전체 기수·전체 IA는 등록하지 않는 안을 추천한다. 5개 유형과 GENERAL 초기화는 기존 V5의 개발자 등록 데이터다. 기수 2개·주제 10개·허용 관계 10개는 별도 `CandidateDictionary.java`로 등록했다.

이 도구는 코드로 찾아 없는 항목만 추가하며, 숫자 ID를 입력받지 않는다. 이름/활성 상태/다른 유형 허용 관계가 충돌하면 덮어쓰지 않고 트랜잭션을 취소한다. 두 번째 실행의 전체 DB snapshot이 첫 실행과 같았다. REVIEW_LIFE와 FAQ_LIFE는 다른 자동 발급 ID다. 기존 category를 추론 변환하지 않는다. `선배들의 SSUL`/`지원 전 Check!!` 등의 위치 이름도 topic으로 만들지 않았다.

원본 등록은 위 목록의 운영 승인 후에만 한다. 도구는 현재 `.cache/phase5c1` 사본 외의 경로를 거부한다.

## 7. 최초 version baseline

V10 schema migration 완료 직후에는 version/baseline 테이블이 비어 있음을 확인했다. 후보 사전 등록 후 SUPER_ADMIN의 별도 `POST /api/admin/next/version-baseline {confirmed:true}`로 생성했다.

| 대상 | 개수 / 원본 ID |
|---|---|
| 콘텐츠 초안 | 9: 1, 2, 3, 4, 5, 6, 7, 8, 33 |
| 콘텐츠 publication | 0: 원본에 없음 |
| 페이지 초안 | 2: 1, 65 |
| 페이지 publication | 2: 1, 65 |
| 공용 템플릿 | 0: 원본에 없음 |
| 합계 / 재실행 | **13 / 0** |

콘텐츠 version ID 1~9, 페이지 version ID 1~4의 대상·reason·revision은 `clean-v10-baseline.json`에 기록했다. 도입 당시 상태를 보관한 것이며 과거 이력을 복원한 것이 아니다. baseline은 정상적인 활동 기록을 추가하므로 그 이후 activity_log 증가는 schema migration의 데이터 변조와 구분한다.

## 8. V10 runtime·공개 API·브라우저 검증

운영 후보 기준점 `backup/clean-v10-with-baseline.mv.db`에는 시험 콘텐츠/계정/템플릿이 없다. 이를 복제한 **data/smoke-v10.mv.db만** 기능시험 fixture를 가진다. 원본 ID를 fixture ID로 재활용하지 않았으며 새 API 생성 결과를 사용했다(이번 사본의 REVIEW 97, FAQ 98, RESTAURANT 99 등).

| 검증 | 결과 |
|---|---|
| 로그인 및 Thymeleaf/React 접근 | 성공 |
| SUPPORTER | 본인 생성/수정/저장/이력조회 허용; 발행/중단/영구삭제/복구/타인 글/페이지 차단 |
| ADMIN | 콘텐츠 발행/중단, 기존 페이지 편집/발행 허용; 생성/slug/영구삭제/템플릿/전역설정/계정/활동 이력 차단 |
| SUPER_ADMIN | 계정 발급, 템플릿, 전역 메뉴/설정·이력 조회, 시험 콘텐츠 영구삭제 성공 |
| revision | 오래된 저장 409 및 데이터 불변. revision 없는 삭제 400. Thymeleaf 공개 중단은 오류 안내 redirect 후 데이터 불변(최종 HTTP 200만으로 성공 판정하지 않음) |
| REVIEW/FAQ/RESTAURANT | 생성·재조회·분류·복수 기수·주소 저장/발행 성공 |
| POSTS category/query/manual | 세 방식 결과·count, AND/OR, FAQ/REVIEW 생활 격리, 수동 순서/미발행·없는 ID 제외 성공 |
| 콘텐츠 발행 경계 | 제목·분류·주소 초안 변경 시 공개 API 동일 → 재발행 후 변경 |
| 페이지 발행 경계 | query/manual 초안 설정 변경 시 공개 API 동일 → 재발행 후 새 조건·순서 |
| 자동/명시 저장 및 복구 | AUTOSAVE version 미생성, MANUAL_DRAFT 생성, RESTORE_BACKUP+RESTORE, 공개본 불변 |
| page 65 baseline 복구 | 새 초안 복구 및 기존 공개본 유지 |
| 템플릿 | POSTS 세 모드·숨김·centered 보존, 적용 시 새 ID, 페이지와 독립, 비활성·이력·복구 준비 |
| 미디어 보호 | 템플릿 및 version 사용처 표시, 삭제 차단 |
| 기존 메뉴·페이지 | 원래 메뉴 동일, page 1·65 미리보기와 기존 블록 유지 |
| V10 정상 재시작 | 8개 관리자 document/history 경로 및 6개 공개 경로 응답 완전 동일 |

브라우저에서 사이트 관리/사이트 구조 사이 동일 페이지 ID와 block 링크 유지, 새 URL로 열었을 때 선택 복원, 숨김 HERO 접근과 centered 표시, 버전 기준점/현재 비교 화면, FAQ 목록과 질문·답변 편집기, 동일 FAQ ID로 보기 전환을 확인했다. 이미지 증거는 `evidence/version-comparison.jpg`, `evidence/faq-editor.jpg`다. 이번 브라우저 검증은 기존 UI smoke이며 4차 전체 상호작용 회귀를 전부 다시 실행했다고 주장하지 않는다.

자동 검사: migration/재실행/무결성 Java 도구, dictionary idempotency, Python HTTP 기능·권한 검사, restart 비교, rollback fingerprint. 고정 RC의 기존 검증 기록(5B-2B-2)은 서버 155개/프런트 39개 통과이며, 이번에 그 전체 테스트를 재실행한 수치가 아니다. 이번 HTTP 시험 중 검사 스크립트의 이유 코드(`RESTORE_BACKUP`), 공개중단 상태(`PRIVATE`), 오류 redirect 기대값을 실제 계약에 맞춰 수정했다. CMS 코드는 수정하지 않았다.

**실제 공개 홈페이지가 없으므로 홈페이지 렌더링 E2E는 미완료이며 5D 검수 항목으로 유지한다.** 공개 API 검증을 홈페이지 연결 완료로 표현하지 않는다.

## 9. 실제 V3 rollback rehearsal

1. V10 runtime 정상 종료, Hikari shutdown/PID 종료/독점 읽기 확인.
2. `backup/v10-before-rollback.mv.db`, 로그, Flyway 및 전체 snapshot 보존.
3. `backup/pre-cutover-v3.mv.db`를 `data/rollback-v3.mv.db`로 복원. 실행 전 byte hash 동일 확인.
4. 대응 **V3 JAR**를 격리 포트 8097에서 실행. 시작 로그 V3/추가 migration 0.
5. 로그인, 관리자, 콘텐츠/페이지/메뉴, React 두 진입 화면 및 page 1·65 조회 성공.
6. 정상 종료 후 전체 16개 업무 테이블/기존 컬럼/페이지 JSON/계정/메뉴/활동/Flyway 이력 fingerprint가 적용 전과 완전히 같음.

V10 DB에 V3 JAR를 연결하지 않았다. 테스트가 끝난 V3 서버 8095/8097은 종료했다. 결과 확인용 V10 fixture 서버는 8096에 남겼으며 원본이 아니다. 기존 다른 단계 서버는 종료하지 않았다.

## 10. 원본 보호 및 산출물

원본 SHA-256 전후 동일:

- `aica-local.mv.db`: `55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`
- `backoffice.mv.db`: `2a9ef304a1407a663f903e8924b1e7326c241033bd9e4c002a30b3f95c9b8a90`

`evidence/final-integrity.json`, `bundle-manifest.json` 및 `RESTORE.md`를 기준점에 보관한다. 코드 변경 범위는 새 검증 도구와 문서뿐이다. 기존 WIP를 삭제·reset·commit하지 않았다. 백업에는 계정 해시 및 본문이 포함되므로 공개 배포물로 쓰지 않는다.

## 11. 5C-2를 막는 항목과 다음 순서

1. **연결 수명 재연결 후 값/이력 손실 재현 — 차단.** 준비/적용을 별도 JVM으로 분리한 경로는 통과했지만 원인은 미확정이다. 비동기 종료·파일 저장 환경·라이브러리 조합을 별도 사본으로 좁히고, 독립 프로세스 영속성 및 반복 재현 검증으로 해소해야 한다. 명시적 SHUTDOWN만으로 해결되지 않았다.
2. **원본 보호 guard — 의도된 차단.** `ClassificationMigrationConfiguration`은 local profile 또는 `/.local-data/` 경로를 거부한다. copy-validation=true를 줘도 원본은 거부한다. 현재 RC는 원본 전환용으로 바로 실행할 수 없다. 5C-2 승인 시 정확한 원본 경로와 전환 작업만 허용하는 좁은 설정/guard 변경을 준비하고 새 RC와 전체 사본 리허설을 다시 고정해야 한다. Flyway 비활성화나 경로 위장으로 우회하지 않는다.
3. **운영 사전 확정 — 미승인.** 위 후보 목록 승인 전에는 원본에 등록하지 않는다.
4. **배포 환경 — 미확정.** 현재는 로컬 H2/dev 검증 환경이다. 실제 프로세스 관리자·외부 쓰기 차단·백업 보관 위치·재개 책임자를 확정해야 한다. OneDrive 경로를 장기 운영 저장소로 승인한 것은 아니다. 개발용 로그인/preview 설정도 운영 전 별도 확인해야 한다.
5. H2/Flyway 지원 상한 경고를 조건 없이 수용하지 않는다. 업그레이드는 원인 검증 결과에 따라 별도 작업으로 결정한다.
6. 실제 홈페이지 렌더링 E2E는 5D에 남긴다. 이는 로컬 API 리허설 실패와는 별도 항목이다.

명령 순서와 STOP 지점은 [5C-2 runbook](PHASE5C2_CUTOVER_RUNBOOK.md)에 작성했다. **이번 결과는 원본 전환 승인이나 5C-2 실행 완료가 아니다.**
