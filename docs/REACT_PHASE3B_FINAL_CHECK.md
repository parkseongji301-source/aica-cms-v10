# AICA 3B 최종 적용 점검

2026-09-27. **원본은 V3를 유지한다. 원본 V4~V6 적용과 3C 구현은 하지 않았다.** 최신 원본 백업에서 새 사본을 만들어 migration, 데이터 비교, DB/JAR 복구를 검증했다. 업무 코드·UI·SQL migration은 변경하지 않았다.

## 1. 최신 기준점과 실행 환경

기준점: `.cache/checkpoints/20260927-011656-react-phase3b-final/`

위치는 `.cache/react-phase3b-final-checkpoint.txt`에도 기록했다. DB를 정상 종료한 뒤 **2026-09-27 01:18:00 KST**에 백업했다. Spring의 요청 종료와 Hikari 연결 종료 로그를 확인하고 파일을 복사했다. 복사 직후 원본과 백업의 SHA-256이 같았다.

| 파일 | 용도 |
| --- | --- |
| `original-v3.mv.db` | 원본의 최신 V3 백업. migration/서버 실행에 직접 사용하지 않은 보존본 |
| `original-v3-runtime.jar` | 위 DB와 짝이 맞는 기존 3A 실행 파일 |
| `candidate-v6-runtime.jar` | 3B-2B 완료 당시 실행 파일 보존 |
| `verified-v6-runtime.jar` | 이번 전체 테스트 후 `clean verify`로 생성한 실행 파일 |
| `source.zip`, `source-manifest.json` | 작업 중 수정·미추적 파일을 포함한 기존 소스 196개 |
| `history.bundle`, `working-tree.patch`, `git-status.txt`, `head.txt` | 기존 Git 이력과 작업 상태 |
| `backup.json`, `runtime-inventory.json`, `verified-artifact.json` | DB 경로, 캡처 시각, 실행 파일·migration 목록과 해시 |
| `RESTORE.md`, `checksums.json` | 복구 절차와 보존 파일 무결성 기준 |

V3 DB SHA-256: `6b0026ad9940ea7cea029e906d5f6f11a61dfbaf3303956f90ad9f4a862c1b8a`. 기계 검증에는 `backup.json`의 `sha256` 또는 `checksums.json`을 사용한다.

기존 8081 서버는 같은 3A 실행 파일로 재시작했다. 시작 로그의 현재 버전은 **3**, 추가 migration은 **0**이며 `/login`은 200이었다. 8082의 기존 3B-2B 검증 환경은 변경하지 않았다. 이번 재기동 검사에 잠깐 사용한 8083 서버는 모두 정상 종료했다.

## 2. Migration 최종 검증 결과

같은 최신 백업에서 서로 다른 새 사본을 만들었다. 파일명은 모두 `.cache/react-phase3b2a-data/20260927-011656-react-phase3b-final-`로 시작한다.

| 사본 | 검증 | 결과 |
| --- | --- | --- |
| `local-test.mv.db` | `LocalCopyClassificationMigrationTest`를 명시 활성화한 전체 `clean verify` | **69개 통과, 실패 0, 오류 0, 제외 0** |
| `stepwise.mv.db` | V3→V4, V4→V5, V5→V6를 따로 실행 | 각 단계 1개 적용, 매 단계 기존 16개 테이블의 모든 기존 컬럼 값·행 수 동일 |
| 같은 V6 사본 | Flyway validate 및 migrate 재실행 | validate 통과, 추가 적용 0 |
| `restore-check.mv.db` | V3 보존본을 다시 복사하고 `original-v3-runtime.jar`로 실행 | 로그인·콘텐츠·페이지·메뉴 조회 성공, V3 유지 |
| 같은 V6 사본 | `verified-v6-runtime.jar`로 실행 | 로그인·조회 성공, 유형 GENERAL + 기수/주제 비어 있음 |
| 위 세 사본 | 서버 종료 후 읽기 전용 재비교 | 기존 16개 테이블 모두 최초 백업과 동일 |

이번 실제 실행에는 다음 속성이 전달됐다. 경로는 **원본이 아닌 새 V3 사본**이다.

```powershell
.\scripts\mvn-local.ps1 -B -ntp -Pegov43-probe `
  '-Daica.validationCopy=C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/react-phase3b2a-data/20260927-011656-react-phase3b-final-local-test.mv.db' `
  clean verify
```

이 사본은 이미 V6이므로 같은 경로로 migration 테스트를 반복하면 V3 전제 검사에서 실패한다. 재검증할 때는 보존된 V3 DB에서 **새 이름의 사본**을 다시 만들고 그 경로를 전달한다. 보존본이나 원본을 테스트 경로로 지정하지 않는다.

현재 UI 코드는 변경하지 않았으므로 React/브라우저 검증을 다시 수행했다고 보고하지 않는다. 이번 검증은 전체 Spring 회귀 테스트와 실제 JAR의 HTTP 조회, DB 비교에 집중했다. 3B-2B의 React 검증 결과는 해당 단계 문서에 남아 있다.

### 기존 데이터 비교

각 테이블에서 migration 전부터 존재하던 모든 컬럼을 읽어 행 순서와 무관하게 직렬화하고 SHA-256과 행 수를 비교했다. ID만 비교한 것이 아니며 본문·JSON·계정·권한·revision·날짜 및 바이너리/텍스트 값도 포함한다. 추가된 분류 컬럼은 별도 초기화 규칙으로 확인했다.

| 대상 | migration 전→후 | 확인 범위 |
| --- | --- | --- |
| 콘텐츠 `posts` | 9→9 | 기존 ID, `category_id`, 본문, 상태, revision 모두 동일 |
| 기존 분류 `categories` | 3→3 | ID·이름·순서 동일, 신규 주제로 추론 변환하지 않음 |
| 방문자 메뉴 `site_menus` | 2→2 | ID·연결 대상·노출·순서 동일 |
| 페이지 `site_pages` | 2→2 | ID, `sections_json`의 블록 내용 및 참조, 상태 동일 |
| 페이지 발행본 `page_publications` | 2→2 | 기존 발행 JSON과 revision 포함 동일 |
| 콘텐츠 발행본 `post_publications` | 0→0 | 임의 발행본 생성 없음 |
| 운영 계정 `users` | 2→2 | 계정·비밀번호 해시·역할·상태 동일 |
| 나머지 기존 테이블 | 모두 동일 | 활동 이력 38건, 설정 12건, 미디어/연결 및 guard 테이블 포함 |

**실제 최신 원본에는 콘텐츠 발행본이 없다.** 따라서 “실제 공개 콘텐츠를 옮겼다”는 근거는 만들 수 없다. 대신 이번에 실행한 `ClassificationMigrationTest.migrationPreservesEveryLegacyColumnAndDoesNotInferTerms`에서 초안과 발행본의 제목·본문·category·revision이 서로 다른 V3 데이터를 만들어 보존을 확인했다. `ClassificationIntegrationTest`의 초안/발행본 분리, 구화면 저장/발행, 충돌/롤백 검사도 모두 통과했다. 실제 원본의 페이지 발행본 2건은 별도로 보존 확인했다.

새 JAR의 실제 API에서도 ID 33의 기존 콘텐츠 필드, ID 65의 페이지 문서, 전체 콘텐츠 목록, 메뉴·category·권한 응답이 V3 복구 서버의 응답과 같았다. 콘텐츠 응답에 추가된 분류만 GENERAL/빈 배열이었다.

### Flyway 이력·검색 경로

실제 검색 경로는 `classpath:db/migration/h2`다. `application-local.yml`, `application-dev.yml`, `application-design-preview.yml`, `application-test.yml`에서 확인했다. Stage 0의 별도 호환성 테스트는 `classpath:stage0/db`를 사용하며 업무 migration과 구분된다.

| 버전 | SQL | 확인된 Flyway checksum |
| --- | --- | --- |
| V1 | `V1__backoffice.sql` | 379266581 |
| V2 | `V2__cms.sql` | -925664058 |
| V3 | `V3__rich_editor.sql` | -1610808639 |
| V4 | `V4__content_classification_schema.sql` | 1414704998 |
| V5 | `V5__content_classification_baseline.sql` | 1744004732 |
| V6 | `V6__content_classification_constraints.sql` | -285908275 |

원본 백업의 이력은 V1~V3이며 모두 성공이다. 사본에는 V4~V6가 순서대로 한 번씩 성공 기록됐고, V1~V3의 기존 이력 행 자체도 변경되지 않았다. checksum은 Flyway의 값이며 파일 SHA-256과는 다른 값이다.

원본 JAR에는 V1~V3 SQL만, 새 JAR에는 위 V1~V6 SQL만 들어 있다. 미완성 `V4__navigation`은 `workbench/navigation-draft/src/main/java/db/migration/h2/V4__navigation.java.txt`에 그대로 있고 두 JAR 어디에도 포함되지 않았다. 검증 fixture도 JAR에 없다. 실제 Flyway 검색 목록 검사도 통과했다.

`clean verify` 결과 JAR는 3B-2B JAR와 업무 클래스·사용 리소스·라이브러리가 동일하다. 차이는 HTML에서 참조하지 않는 이전 번들 `index-DCjYh0ea.js`가 clean 빌드에서 제외된 것뿐이다. 배포 후보는 이번 `verified-v6-runtime.jar`로 고정해 보존했다.

## 3. 검증용 데이터와 실제 초기 분류 추천안

### 이번에 실제 적용한 범위

새 V3→V6 사본에는 migration의 기술적 기본값만 적용했다.

- 개발자 등록 유형 GENERAL / REVIEW / RESTAURANT / INTERVIEW / FAQ.
- 모든 기존 콘텐츠와 발행본의 유형 GENERAL.
- `cohorts`, `topics`, `content_type_topics`와 모든 신규 연결 테이블은 빈 상태.
- 기존 `category_id`와 categories는 그대로 유지.

3B-2B의 `B2B_C6`, `B2B_C7`, `B2B_REVIEW_*`, `B2B_FAQ_*`는 **8082 검증 DB의 fixture**다. 이름이 실제 추천안과 같아도 운영 데이터로 승격하지 않는다. `scripts/seed-classification-copy.ps1`과 `workbench/classification-validation/phase3b2b-fixture.sql`은 수동 검증 전용이며 시작 시 실행되지 않는다. 그 DB를 원본 위에 복사하지 않는다.

### 추천: 최소 사전으로 시작하고 IA 전체를 분류로 옮기지 않기

**아래는 미확정 제안이다. 이번 점검에서 등록하지 않았다.** 기존 콘텐츠의 실제 의미를 확인한 뒤 담당자가 선택하며 category 이름으로 자동 매핑하지 않는다.

| 기준 | 초기 추천값 | 이유 / 경계 |
| --- | --- | --- |
| 콘텐츠 유형 | 기존 5개 등록 유형 유지 | 콘텐츠 형식을 나타낸다. 화면 위치나 대분류 메뉴를 유형으로 만들지 않음 |
| 기수 | 6기(`COHORT_06`), 7기(`COHORT_07`) | 받은 IA에 개별 기수로 명시됨. 기수와 무관한 글은 선택 없음 |
| REVIEW 주제 | 생활(`REVIEW_LIFE`), 수업(`REVIEW_CLASS`), 프로젝트(`REVIEW_PROJECT`) | IA의 후기 주요 묶음. 우선 3개로 시작하고 특강·기업탐방의 독립 필터가 필요할 때 세분화 검토 |
| FAQ 주제 | 준비사항(`FAQ_PREPARATION`), 지원·선발(`FAQ_APPLICATION`), 수업(`FAQ_CLASS`), 생활(`FAQ_LIFE`), 취업(`FAQ_EMPLOYMENT`), 지원금(`FAQ_ALLOWANCE`), 프로젝트(`FAQ_PROJECT`) | 받은 FAQ의 관리 주제 7개에 대응. 개별 질문을 주제 항목으로 등록하지 않음 |
| GENERAL / RESTAURANT / INTERVIEW 주제 | 우선 빈 사전으로 시작 | 유형·현재 category로 관리하면서 실제 교차 검색 필요를 확인. 맛집이라는 이유로 다시 “맛집” 주제를 붙이지 않음 |

이 안은 기수 2개, 주제 10개다. 실제 추가 시 ID는 DB에서 발급받고 code와 ID를 따로 관리한다. 검증 DB의 숫자 ID를 재사용하지 않는다.

- `REVIEW_LIFE`와 `FAQ_LIFE`는 표시 이름이 모두 “생활”이어도 **다른 topic ID**다. 허용 관계는 각각 REVIEW와 FAQ에만 연결한다. 수업·프로젝트도 같은 원칙이다. 현재 schema의 unique code와 `content_type_topics`로 표현하므로 namespace 컬럼을 추가할 필요는 없다.
- 인터뷰를 서면/영상으로 나누는 것은 향후 형식/필드 문제다. 이번 추천에서 새 콘텐츠 유형·전용 필드를 추가하지 않는다.
- IA의 “3~5기”는 화면에서 묶어 보여주는 표현으로 두고, 실제 콘텐츠를 운영할 때 3기·4기·5기가 필요한지 확인한다. “3~5기”를 하나의 실제 기수로 등록하는 것은 추천하지 않는다. “2027 인사교”의 연도도 기수 번호로 자동 변환하지 않는다.
- “인사교 알아보기”, “기수별 스토리”, “Real Life”, “지원 전 Check!!”는 페이지/메뉴의 탐색 위치다. 콘텐츠 분류로 복제하지 않는다. **FAQ의 홈페이지 위치는 `지원 전 Check!!` 하위**이며, FAQ라는 유형이 배치를 자동 결정하지 않는다.
- 인사교 소개·시설·오시는 길처럼 기존 페이지인 대상은 페이지로 유지한다. 주거·문화/여가 안내를 독립 콘텐츠로 운영할지는 기존 페이지 관계와 3C 작업 범위를 확인한 뒤 정한다.
- 기수/주제의 빈 선택을 유지하고, 6→7기 비교 글처럼 필요한 경우에만 복수 기수를 선택한다.

운영용 사전 등록은 schema migration과 별도 절차로 한다. 추천안 승인 후 code·표시명·정렬·활성 여부·유형별 허용 관계를 명시한 데이터 파일을 만들고, 별도 사본에서 검증한 뒤 적용하는 방식이 적절하다. 이미 검증한 V4~V6 SQL을 편집해 IA나 초기 주제를 끼워 넣지 않는다.

## 4. 원본 적용 절차 — 아직 실행하지 않음

1. **적용 경로를 먼저 준비한다.** 현재 `ClassificationMigrationConfiguration.requireValidationDatabase`는 `local` 프로필과 `.local-data` 경로를 차단한다. `copy-validation=true`만으로 원본을 적용할 수 없다. 이번에는 이 장치를 변경하지 않았다. 원본 적용 결정 후에만 정확한 대상 DB를 검증하는 별도 배포 설정/절차를 검토하고 사본에서 재시험해야 한다. 경로 이름을 바꾸거나 guard를 포괄적으로 끄는 방식으로 우회하지 않는다.
2. 적용 시점에 쓰기를 중단하고 실행 프로세스·DB 경로·JAR를 확인한다. 이번 백업 이후 원본이 사용됐다면 **그 시점의 최신 DB와 실행 파일을 다시 함께 백업**한다. 체크포인트 해시를 확인하고 복구 사본으로 정상 시작 가능한지 확인한다.
3. 최신 사본에서 현재처럼 V3 전제, V1~V3 checksum, V4~V6 목록, 기존 데이터 fingerprint를 확인한다. 실제 발행본이 늘었다면 그 발행본도 포함해 재비교한다.
4. 검증 완료한 배포 JAR와 V4~V6의 checksum을 고정한다. 검증 fixture, 미완성 navigation V4, 운영용 사전 파일을 schema 적용 경로와 분리한다. 승인되지 않은 기수·주제는 비워 둔다.
5. 유지보수 중 승인된 원본 경로에만 V4→V5→V6를 한 번 적용한다. 성공 이력·순서·checksum과 기존 컬럼 해시를 확인한다. GENERAL 초기화와 빈 연결 상태를 확인하고 validate/재실행 0건을 검사한다.
6. **V6용 새 서버와 그 서버에 포함된 React를 함께** 실행한다. 구 3A 서버가 같은 V6 DB에 접속하지 않도록 한다. 조회, 메뉴/페이지 연결, 로그인·권한·CSRF, 초안 저장/발행본 보존을 확인한 뒤 쓰기를 재개한다.
7. 실제 기수·주제 사전은 승인된 별도 데이터 작업으로 추가한다. 기존 글의 분류 변경 역시 초안→발행 절차를 거친다. `category_id`를 제거하거나 공개 페이지 배치를 자동 변경하지 않는다.

현재 파일 DB/H2는 로컬 개발 환경이다. 운영 DB 종류가 달라지는 배포는 이 H2 검증으로 대체하지 않는다.

## 5. 실패 시 복구 절차

실제 복구 리허설은 **새 경로의 V3 백업 사본 + 보존된 3A JAR**로 수행했다. 로그인과 ID 33·65 조회가 성공했고, 정상 종료 후 원래 16개 테이블의 데이터가 백업과 같았다. 원본 파일을 덮어쓰는 복구는 수행하지 않았다.

1. 실패 시 쓰기를 계속 막고 해당 DB에 접근하는 모든 서버를 정상 종료한다. 포트만이 아니라 실제 프로세스 명령과 datasource 경로를 확인한다.
2. 실패한 DB·로그·Flyway 이력을 별도 복구 조사 경로에 보존한다. 실패 DB를 즉시 삭제하거나 보존본 위에 복사하지 않는다.
3. 적용 직전 V3 보존본과 **동일 시점의 구 실행 파일** 해시를 확인한다. 새 복구 경로로 복사해 V3 이력과 데이터 fingerprint를 먼저 확인한다.
4. 격리된 loopback 포트에서 그 복구 DB와 구 JAR의 조합으로 시작해 로그인·계정·콘텐츠·페이지·메뉴·발행본 조회를 확인한다. 리허설에서는 `dev` + 명시적 사본 URL + `IFEXISTS=TRUE` + bootstrap 비활성으로 실행해 `local` 초기화기를 사용하지 않았다.
5. 격리 검사가 끝나면 정상 종료한다. 모든 원본 접근 프로세스가 멈춰 있는 상태에서 원본의 실패 파일을 안전한 위치에 보존하고 검증한 V3 DB를 원래 위치로 복원한다. 구 JAR로만 시작한다. 현재 원래 위치는 `.local-data/aica-local.mv.db`, 현재 구 실행은 `scripts/run-local.ps1`이 선택하는 보존 3A JAR이다. 스크립트가 선택하는 JAR 해시가 복구 기준점과 같은지도 확인한다.
6. V3 이력·기존 데이터·로그인·연결을 확인한 뒤 쓰기를 재개한다. 원인을 해결한 이후 **다시 만든 V3 사본**에서 migration을 처음부터 검증한다.

H2 DDL의 부분 적용을 한 번에 되돌릴 수 있다고 가정하지 않는다. 중간 실패 DB에 `repair`, 버전 행 삭제, 수동 역순 DROP을 바로 수행하지 않는다. **V6 DB에 구 JAR만 덮어씌우는 복구도 하지 않는다.** 구 발행기는 V6의 새 NOT NULL 발행 컬럼을 채우지 못한다.

쓰기 재개 후 문제가 발견됐다면 V3 백업 복원만으로 이후 편집 내용은 보존되지 않는다. 먼저 V6 DB를 별도로 보존하고 추가 변경분을 추출·조정할 계획이 필요하다. 따라서 적용 당일에는 모든 확인이 끝날 때까지 쓰기 중단을 유지한다.

## 6. 3C 전에 남은 위험과 결정 사항

| 항목 | 현재 판단 / 다음 확인 |
| --- | --- |
| 원본 migration 실행 장치 | 아직 원본 차단 상태. 이번 검증 통과가 원본 실행 승인이나 guard 해제를 뜻하지 않음 |
| 실제 기수·주제 사전 | 위 2개 기수/10개 주제는 추천. 운영자 확인 전 등록하지 않음. 검증 ID/fixture를 가져오지 않음 |
| 실제 콘텐츠 발행본 표본 | 최신 원본에는 0건. 별도 fixture 검사 통과; 적용 시 원본에 발행본이 생기면 새 사본으로 다시 검사 |
| H2/Flyway 호환 경고 | 현 버전에서 H2 2.3.232가 Flyway 검증 지원 범위보다 새롭다는 경고가 계속 발생함. 이번 실제 사본 검증은 통과했지만 무경고 호환 보장을 뜻하지 않음. 의존성 조정은 별도 사본 검증으로 처리 |
| 백업 보관 | 이번 기준점은 로컬 `.cache/checkpoints`에 있음. 실제 적용 전 캐시 정리·작업 폴더 삭제와 분리해 DB/JAR/설정/해시를 한 묶음으로 보관해야 함 |
| 페이지 노출과 분류 | 기존 category 기반 메뉴/POSTS 블록 유지. 새 유형·기수·주제를 고른다고 사이트 위치가 자동 변경되지 않음 |
| 3C 범위 | 유형별 필드·검증·입력폼의 구체 범위가 미정. 공통 편집기와 저장·발행 snapshot 처리를 재사용해야 함 |
| 운영 정책 | SUPPORTER 발행 권한, 승인/반려, 페이지 생성, 자동저장 정책 변경 없음. 새 정책으로 간주하지 않음 |

## 증거와 재현 위치

기준점 안의 `stepwise-migration.json`에는 단계별 전체 비교와 전후 Flyway 이력이 있다. `test-reports/TEST-egovframework.backoffice.integration.LocalCopyClassificationMigrationTest.xml`에 실제 사본 테스트 1개 통과·제외 0이 기록됐다.

추가 근거: `test-summary.json`, `verify.log`, `local-copy-migration.txt`, `runtime-inventory.json`, `verified-artifact.json`, `restore-runtime-check.json`, `v6-runtime-check.json`, `local-test-readback.json`, `v6-runtime-readback.json`, `restored-v3-readback.json`, `final-audit.json`.

단계별 보조 검사 소스는 기준점의 `audit-tools/`에 보존한다. 애플리케이션이나 Flyway 자동 실행 경로에는 추가하지 않았다. 이번 저장소의 새 변경은 이 결과 문서뿐이며, 기존 코드와 작업 중 변경은 기준점 해시로 보존 여부를 확인했다.
