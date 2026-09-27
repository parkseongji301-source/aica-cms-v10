# AICA 3B-2A: 분류 DB 기반과 저장·발행 처리

2026-09-27. 기존 `category_id`를 유지하면서 콘텐츠 유형 1개, 기수 0개 이상, 주제 0개 이상을 저장하도록 추가했다. **원본 DB는 V3 그대로이며, 신규 V4~V6는 복사 DB에만 적용했다.** React·Thymeleaf 화면은 기존 UI를 유지한다. React 분류 편집은 3B-2B 범위다.

## 실행 위치와 복구 기준점

- 원본: `http://127.0.0.1:8081/admin-next/posts?view=manage`, 보존한 3A JAR + `.local-data/aica-local.mv.db`(V3).
- 검증: `http://127.0.0.1:8082/admin-next/posts?view=manage`, 신규 JAR + `.cache/react-phase3b2a-data/aica-phase3b2a.mv.db`(V6).
- 기존 로그인·권한·CSRF를 그대로 사용한다. 두 포트의 데이터는 별도 사본이며, 검증 사본의 수정은 원본에 반영되지 않는다.
- 기준점: `.cache/checkpoints/20260927-000241-react-phase3b2a/`. 현재 수정·미추적 소스 175개, Git 이력 bundle/patch/status, 정지 상태 DB, 기존 실행 JAR, SHA-256 및 복구 안내를 보존했다. 포인터는 `.cache/react-phase3b2a-checkpoint.txt`.
- 원본 DB는 검증 완료 후 기존 JAR로 재시작하기 직전까지 기준점과 SHA-256이 동일했다. 이후 원본 서버의 정상 H2 파일 쓰기와 검증 DB 변경은 구분한다.
- 원본 실행은 `scripts/run-local.ps1`이 보존한 3A JAR를 선택한다. 신규 빌드로 원본을 자동 업그레이드하지 않는다.
- 검증 실행: `scripts/run-classification-copy.ps1`. 이미 존재하는 `.cache/react-phase3b2a-data/*.mv.db`만 허용하고 `dev`, loopback, `IFEXISTS=TRUE`, 명시적인 copy-validation 옵션을 사용한다. DB를 생성하거나 덮어쓰지 않는다.

신규 `ClassificationMigrationConfiguration`은 기본적으로 파일 DB 마이그레이션을 차단한다. `local` 프로필 및 `.local-data` 경로는 명시 옵션이 있어도 차단한다. 원본 적용은 이번 작업에서 수행하지 않았으며 별도 배포 결정이 필요하다.

## Flyway V4 처리

사용자는 로컬·검증 DB만 사용했다고 확인했다. 현재 DB, 이전 검증 DB, 체크포인트 사본을 합쳐 14개 파일을 읽기 전용으로 검사했고 V4 적용 이력은 없었다. 기존 JAR 7개의 migration 목록도 V1~V3만 포함했다. 검색 경로는 일반 실행·테스트 모두 `classpath:db/migration/h2`이며 Stage 0는 DB 자동 구성을 제외한다.

미완성 `V4__navigation.java.txt`는 `workbench/navigation-draft/`에 그대로 보존했다. Java 소스 확장자와 classpath 밖에 있어 실행되지 않는다. 파일 내용은 변경하지 않았다. 이번 V4는 이력을 확인한 뒤 새로 작성한 **분류 전용 SQL**이며, 기존 V4를 적용하거나 V5만 추가해 건너뛴 것이 아니다.

| 단계 | 파일 | 처리 |
| --- | --- | --- |
| V4 | `V4__content_classification_schema.sql` | 테이블·관계·인덱스 및 nullable 유형 컬럼 추가 |
| V5 | `V5__content_classification_baseline.sql` | 개발자 등록 유형 5개, 기존 초안/발행본 각각 GENERAL 초기화 |
| V6 | `V6__content_classification_constraints.sql` | 유형 NOT NULL, 초안 기본값 GENERAL, 발행본 이름 NOT NULL |

IA 입력, 메뉴 트리 변경, 기존 category 추론 변환, 기존 초안을 새로 발행하는 작업은 없다. 테스트는 실제 검색 결과가 V1~V6의 정확한 SQL 목록인지, `V4__navigation` 클래스가 없는지, `site_menus.parent_id`와 GROUP 메뉴가 추가되지 않았는지도 확인한다. `clean verify` 후 생성 JAR로 실행했다.

H2 DDL은 전체 migration 묶음의 원자적 롤백을 보장하지 않는다. 실패 시 중간 적용 DB에 `repair`를 임의 실행하지 않고 검증 서버를 종료한 뒤 보존 사본에서 **새 검증 DB**를 만들어 원인을 수정하고 재검증한다. 기존 기준점을 덮어쓰지 않는다. 새 분류를 작성한 V6 DB를 구버전 JAR로 운영하는 것은 지원하지 않는다.

## 실제 DB 구조

| 대상 | 저장 위치 / 규칙 |
| --- | --- |
| 유형 사전 | `content_types(code PK, name, active, sort_order)`; GENERAL / REVIEW / RESTAURANT / INTERVIEW / FAQ |
| 기수 사전 | `cohorts(id PK, code UNIQUE, name, active, sort_order)` |
| 주제 사전 | `topics(id PK, code UNIQUE, name, description, active, sort_order)`; 이름 중복 허용 |
| 유형별 허용 주제 | `content_type_topics(type_code, topic_id)` 복합 PK |
| 초안 유형 | `posts.content_type_code`; 기존 `id`, `category_id`, 본문 등 유지 |
| 초안 복수 기수/주제 | `post_cohorts(post_id, cohort_id)`, `post_topics(post_id, topic_id)` 복합 PK |
| 발행본 유형 | `post_publications.content_type_code`, `type_name_snapshot`; 기존 `post_id`, `category_id` 유지 |
| 발행본 복수 기수/주제 | `post_publication_cohorts`, `post_publication_topics`; 각 복합 PK와 `name_snapshot` |
| 홈페이지 노출 위치 | 기존 페이지/블록/메뉴 관계 유지; 신규 분류에 위치 정보를 넣지 않음 |

모든 관계는 FK로 참조하며 같은 항목의 중복 연결을 PK로 막는다. 초안 및 발행본 부모가 삭제되면 해당 연결만 cascade 삭제한다. 참조 중인 사전 항목의 물리 삭제는 FK로 제한한다. 기수/주제 사전과 허용 연결은 migration 직후 비어 있다. 테스트의 `6기`, `7기`, `생활`, `프로젝트` 등은 테스트 전용 fixture이며 실제 초기 데이터가 아니다.

같은 이름의 `생활` 주제를 후기용과 FAQ용의 서로 다른 ID/code로 관리할 수 있다. 정말 공통인 주제는 하나의 ID에 복수 유형 허용 관계를 등록한다. 이름으로 매칭하거나 자동 공유하지 않는다. 유형 추가·사전 편집용 운영자 API는 만들지 않았다.

## Java / Mapper 변경과 기존 로직 재사용

| 파일/영역 | 변경 |
| --- | --- |
| `PostService` | 선택 분류를 받는 save/saveDocument/preview 오버로드, 권한 검사 후 분류 조회, 발행본 조회, 기존 트랜잭션 내부 분류 저장·복사·삭제 |
| `ClassificationModels` | Selection, Classification, Type/Term, AllowedTopic, Catalog DTO |
| `ClassificationService` | 누락/명시 입력 구분, 유형·ID·중복·허용 주제·비활성 검증, 초안/발행본 읽기, 기존 트랜잭션 참여 |
| `ClassificationMapper` / XML | 신규 테이블 조회·저장·발행 시 이름 복사. EgovAbstractMapper 재사용 |
| `CmsMapper.xml` | 기존 publishPost에 유형 및 이름 저장. publicPosts/count의 선택적 분류 필터는 발행본 관계만 사용 |
| `NextPostApi` | 기존 콘텐츠 DTO·저장·미리보기에 classification 확장, 인증된 발행본 조회 |
| `NextClassificationApi` | 읽기 전용 분류 사전 조회 |
| `NextApiErrors`, `SecurityConfiguration` | 기존 오류 응답 및 인증 경로를 신규 조회 API에 적용 |
| `ClassificationMigrationConfiguration` | 원본 자동 적용 방지 |
| 테스트 3개 신규 | 분류 HTTP/업무 검증, migration 데이터 보존·격리, 실제 로컬 복사 DB 검증 |
| 기존 테스트 4개 수정 | NextAdmin/NextWorkspace/NextPost의 최신 migration 이력 검사, PersistenceRestart에 복사 검증 옵션 및 분류 영속성 검사 |
| 실행 스크립트/문서 | 복사 DB 전용 실행, 원본의 보존 JAR 선택, 격리 설명 및 이 문서 |

기존 PostController, PostMapper, category 처리, 메뉴·페이지 서비스, Thymeleaf 템플릿, React 소스는 수정하지 않았다. 기존 `cms.lock()`, `CurrentAccount`, `AccessPolicy`, `CmsRules.revision`, `RichTextService`, `MediaService`, 활동 이력을 사용한다.

분류 변경도 `PostService.save`를 거쳐 기존 revision이 증가한다. 저장 전 권한·revision·분류 검증을 수행하고, 본문·미디어·category·신규 분류·발행본을 한 트랜잭션에서 처리한다. 분류 쓰기 메서드는 `Propagation.MANDATORY`로 독립 트랜잭션 저장을 금지한다. 분류용 별도 콘텐츠 저장소나 발행기를 만들지 않았다.

발행 시 기존 현재 발행본을 교체하는 흐름 안에서 유형과 기수·주제 ID 및 이름을 복사한다. 초안 저장은 발행본을 건드리지 않는다. 이름 변경이나 사전 비활성화도 발행된 이름/노출 조건을 바꾸지 않는다. API의 term `active` 값은 현재 사전 상태 안내이고, 발행 여부나 노출 판정이 아니다. 발행본은 기존처럼 콘텐츠당 현재 snapshot 1개이며 전체 버전 이력 저장소로 확대하지 않았다.

## category 및 구화면 호환

- 기존 `category_id`는 초안/발행본 모두 유지한다. 기존 category 필터, CATEGORY 메뉴, 페이지 POSTS `categoryId`, 구조 탐색을 그대로 사용한다.
- 기존 Thymeleaf 폼·save-json·3A React 요청처럼 `classification` 필드가 **없으면** 기존 신규 분류를 보존한다. 구화면에서 발행하면 보존된 현재 분류를 함께 발행한다.
- 기존 신규 콘텐츠 생성은 GENERAL + 빈 기수/주제로 시작한다.
- 사용 중인 비활성 선택은 같은 유형에서 유지할 수 있다. 새로 비활성 항목을 선택할 수 없다. 유형 변경 후 허용되지 않은 주제를 보내면 오류로 처리하고 자동 삭제하지 않는다.
- `publicPosts`/count는 category 조건을 그대로 두고, 분류 조건이 주어진 경우에만 발행본 연결을 조회한다. 같은 축의 복수 값은 OR, 서로 다른 축은 AND, EXISTS로 중복 행을 막는다. 이를 사용하는 신규 페이지 배치나 필터 UI는 이번에 만들지 않았다.
- SUPPORTER 발행 권한, 승인/반려, 자동저장, 페이지 생성 범위는 기존 정책 그대로다.

## 3B-2B에서 사용할 API 계약

| 요청 | 응답 / 처리 |
| --- | --- |
| `GET /api/admin/next/classifications` | `{types, cohorts, topics, allowedTopics}`. 전체 사전(비활성 포함), 허용 조합, 쓰기 API 없음 |
| `GET /api/admin/next/posts/{id}` | 기존 PostDocument + `classification`(초안) |
| `PUT /api/admin/next/posts/{id}` | 기존 제목/본문/category/media/revision + 선택적인 `classification`; 초안 저장만 수행 |
| `GET /api/admin/next/posts/{id}/preview` | 저장된 초안 미리보기 + 초안 classification |
| `POST /api/admin/next/posts/{id}/preview` | 저장 전 입력과 선택 분류 검증·미리보기; DB 쓰기 없음 |
| `GET /api/admin/next/posts/{id}/publication` | `{post, classification}` 현재 공개 발행본 snapshot. 미발행/비공개 404, 타인 접근은 기존 권한 검사 |

기존 콘텐츠 ID 및 편집 경로 그대로다. `view=manage` / `view=structure`는 API 분류·저장 처리와 무관하다. 새 유형 전용 편집 API나 신규 발행 API는 추가하지 않았다.

```json
{
  "revision": 8,
  "title": "기존 제목",
  "content": "기존 본문",
  "richContent": null,
  "categoryId": 11,
  "mediaIds": [],
  "classification": {
    "typeCode": "REVIEW",
    "cohortIds": [101, 102],
    "topicIds": [201, 202]
  }
}
```

위 숫자는 계약 예시이고 실제 서버에서 사전 조회로 받은 ID를 사용해야 한다. 현재 복사 DB 사전은 기수/주제가 비어 있다. 응답 classification에는 `typeCode`, `typeName`, `cohortIds`, `topicIds`, `cohorts`, `topics`가 포함된다. term은 `{id, code, name, active}`이고 ID 배열은 정렬하여 반환한다.

- classification 생략: 보존. `null` 또는 세 하위 필드가 빠진 부분 객체: 400.
- 명시적 `{typeCode, cohortIds:[], topicIds:[]}`: 기수·주제 해제. 유형은 항상 1개 필수.
- 중복 ID, 존재하지 않는 ID, 허용되지 않은 유형/주제: 400 `VALIDATION_ERROR`.
- 오래된 revision: 409 `REVISION_CONFLICT`, 기존 초안·발행본 모두 그대로.
- 기존 세션 및 CSRF 헤더 사용. 인증 실패 401, 권한/CSRF 403. 응답 no-store.

3B-2B에서는 React `types.ts`, API payload, `postFingerprint`, 편집 상태, 미리보기 입력에 이 선택값을 연결해야 한다. 현재 fingerprint는 기존 필드만 비교하므로 분류 UI만 추가하고 fingerprint를 빠뜨리면 분류만 수정했을 때 저장되지 않는다. 유형 변경 시 부적합 선택을 자동 삭제하지 않고 재선택하게 하며 기존 category는 계속 별도로 보존한다. 발행은 기존 UI와 PostService 경로를 계속 사용한다.

## 검증 결과

`mvn-local.ps1 -B -ntp -Pegov43-probe clean verify`: 65개 중 64개 통과, 실패/오류 0, 실제 복사 DB 지정이 필요한 1개는 기본 실행에서 제외. 제외된 `LocalCopyClassificationMigrationTest`는 경로를 명시해 별도 실행했고 통과했다. 전체 로그는 `.cache/react-phase3b2a-verify.log`, 최종 보강 후 재검증 로그는 `.cache/react-phase3b2a-final-verify.log`, 별도 복사 검사 결과는 `.cache/react-phase3b2a-audit/local-copy-migration-test.xml`이다.

| 요구 검증 | 결과 / 근거 |
| --- | --- |
| A/B 기존 기능·category 유지 | 기존 전체 회귀 테스트 + category 메뉴/POSTS 페이지/양쪽 편집 경로 HTTP 검사 통과 |
| C/D 저장 재조회·복수 선택 | 유형 및 복수 기수/주제 저장 후 동일 ID 조회, 정렬된 값 일치 |
| E 중복 방지 | API 중복 요청 400 + DB 복합 PK 위반 검사 |
| F 허용 주제 | 후기용 생활/FAQ용 생활을 다른 ID로 분리, 다른 유형 선택 차단 |
| G/H 초안/발행본 | 생활 발행 → 프로젝트 초안 저장 → 공개 생활 유지 → 구화면 재발행 후 공개 프로젝트 반영 |
| I 구화면 보존 | HTML 폼 저장/발행, save-json 저장/발행, 3A React 요청 누락 보존 |
| J 충돌 | 분류만 수정해도 revision 증가, 오래된 저장/발행은 원본 덮어쓰기 차단 |
| 트랜잭션 실패 | 발행 연결 INSERT를 DB 제약으로 강제 실패시켜 초안·발행본·분류·revision·활동 이력 모두 롤백 확인 |
| 미리보기 | 입력 분류 검증 및 미리보기 후 초안·발행본 불변 |
| 권한·CSRF | 비로그인, 타인 콘텐츠, CSRF 누락 차단 및 기존 SUPPORTER 동작 유지 |
| 이름/비활성 | 발행 이름 고정, 비활성화로 기존 공개 목록이 사라지지 않음 |
| 영속성 | 파일 DB 서버 종료·재시작 후 유형/기수/주제 및 발행 snapshot 유지 |
| 실제 원본 사본 | V3→V6, 재실행 migration 0, 기존 16개 테이블의 모든 기존 컬럼 해시·행 수 동일 |

실제 사본에는 posts 9개, pages 2개, categories 3개, menus 2개, users 2개가 있었고 모두 보존됐다. 그 사본의 콘텐츠 발행본은 0개였다. 따라서 발행본 초기화/유지 검사는 기존 발행본과 초안의 값이 다른 별도 V3 fixture 및 서비스 통합 테스트에서도 확인했다. 검증을 위해 원본 콘텐츠를 임의 발행하지 않았다.

브라우저에서는 8082 복사 DB의 기존 계정 로그인, React 콘텐츠 목록 9개, ID 33 편집, 사이트 관리↔사이트 구조 전환 중 입력 유지, 자동 초안 저장, 다시 조회, 옆 미리보기 갱신을 확인했다. 같은 ID의 기존 Thymeleaf 편집기에도 저장 결과가 표시되며 기존 category 3개와 메뉴 2개가 정상 표시됐다. 시험 제목은 다시 원래 `아아`로 저장했다. 이 편집 이력과 revision 증가는 검증 사본에만 남는다.

원본 8081은 보존한 3A JAR로, 검증 8082는 새 JAR로 실행한다. frontend 소스와 미완성 V4 원본 파일은 기준점 해시와 같고, 기존 파일이 삭제되지 않았음을 확인했다. 변경 파일의 기준점 대비 목록과 JAR migration 목록은 `.cache/react-phase3b2a-audit/change-inventory.json`에 기록했다.

## 남은 범위

원본 DB 적용, 실제 기수·주제 및 허용 관계의 확정/등록 절차, React 분류 UI는 이후 범위다. 테스트용 사전 데이터를 원본에 복사하지 않는다. 맛집·FAQ·인터뷰·후기 전용 필드, 전체 IA, 블록 ID/저장 방식, 템플릿 및 승인/반려는 추가하지 않았다.

실행 중 Flyway는 현재 H2 2.3.232보다 검증된 지원 버전이 낮다는 기존 경고를 출력한다. 실제 복사 DB, 파일 재시작 및 전체 회귀 검증은 통과했으며 이번 단계에서는 기존 H2/Flyway 의존성 버전을 변경하지 않았다.
