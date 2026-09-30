# V13 페이지 계층

페이지 아래에 하위 페이지를 둘 수 있게 한다. 예: "인사교 소개" 아래 "후기", "FAQ". 목적은 트리 편집기가 아니라 **어떤 페이지가 어느 페이지의 하위인지 알 수 있게** 하는 것이다. 페이지 계층과 메뉴 계층은 서로 다른 개념으로 따로 관리한다.

기준점은 태그 `react-admin-step5-20260930`(`246b76a`, 8095 = V12 RC2)이다. 전자정부 표준프레임워크 RTE 4.3.0, Spring Boot 3.4.5, Spring Framework 6.2.6, Spring Security 6.4.5, Tomcat 10.1.40, Java 17과 기존 Service·Mapper·MyBatis·Flyway·세션·CSRF·권한·게시·버전·휴지통 규칙을 유지한다.

**V13 범위는 페이지 계층까지다.** 기존 카테고리 이관, CATEGORY 메뉴 변환, 기존 Thymeleaf 화면 제거는 별도 후속 단계로 남긴다.

## 시작 시점의 사실 (2026-09-30)

- 운영 페이지 3개: 홈 #1(첫 화면, 게시), 인사교 소개 #65(게시, 메뉴 2개), dd #97(임시보관). 계층 없음.
- `site_pages`에 계층 열이 없고 목록은 최신순(`id DESC`)이다. 페이지 주소(slug)는 사이트 전체에서 유일하고 메뉴와 독립이다.
- 버전 스냅샷은 제목·주소·블록만 담는다(`snapshot_schema_version` 1). 되돌리기는 주소와 계층을 바꾸지 않는다.
- 페이지 생성·주소 변경은 MANAGE_SITE(SUPER_ADMIN), 페이지 편집은 ALL_POSTS(ADMIN 이상)이다.

## 1. DB (V13 migration)

```sql
ALTER TABLE site_pages ADD COLUMN parent_id BIGINT;
ALTER TABLE site_pages ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE site_pages ADD CONSTRAINT site_pages_parent_fk FOREIGN KEY(parent_id) REFERENCES site_pages(id);
ALTER TABLE site_pages ADD CONSTRAINT site_pages_not_own_parent CHECK(parent_id IS NULL OR parent_id <> id);
CREATE INDEX site_pages_parent_order ON site_pages(parent_id, sort_order, id);
```

- `parent_id` NULL은 최상위다. 자기 참조 FK와 자기 자신 금지는 모든 계층에 맞는 일반 무결성 규칙이다.
- FK에 cascade가 없으므로 하위 페이지가 있는 페이지는 DB 차원에서도 삭제되지 않는다(파괴적 연쇄 삭제 금지).
- **최대 깊이, 홈 규칙 같은 운영 제한은 DB에 넣지 않고 서비스·UI에서만 지킨다.** 정보 구조가 바뀌어 규칙을 풀 때 migration이 필요 없게 하기 위해서다.
- 기존 행은 모두 `parent_id` NULL, `sort_order` 0이 된다. 데이터 backfill은 없다.
- 계층은 메뉴처럼 즉시 적용되는 구조 정보다. 발행본(`page_publications`)과 버전 스냅샷에는 넣지 않는다.

## 2. 서비스 규칙

모든 변경은 기존 CMS 잠금(`store.lock()`) 안에서 순서대로 처리한다.

| 규칙 | 내용 |
|---|---|
| 권한 | 위치·순서 변경과 상위를 지정한 생성은 MANAGE_SITE(SUPER_ADMIN). ADMIN은 계층을 조회만 한다. 서비스와 Spring Security 양쪽에서 검사한다 |
| 깊이 | 서비스 상수 `MAX_DEPTH = 2`. `새 상위의 깊이 + 1 + 옮길 페이지 아래 하위의 높이 ≤ 2`. 현재는 하위 페이지를 가진 페이지를 다른 페이지 아래로 옮길 수 없고, 상위가 될 수 있는 것은 최상위 페이지뿐이다 |
| 순환 | 새 상위의 조상을 끝까지 따라가 자기 자신이 나오면 거부한다. 깊이 제한을 풀 때를 대비한 일반 검사다 |
| 홈 | 첫 화면(`homePageId`) 페이지는 **최상위 고정이고 하위 페이지를 가질 수 없다**(현재 운영 규칙, 서비스·UI 규칙). 위치 변경에서 홈을 옮기거나 홈 아래로 옮기는 요청을 거부하고, 첫 화면 설정 저장도 최상위이면서 하위 페이지가 없는 페이지만 허용한다 |
| 동시 변경 | 위치 변경 요청은 화면이 본 현재 상위(`expectedParentId`)를 함께 보낸다. 다르면 "다른 사용자가 먼저 위치를 바꿨습니다"로 거부한다(409 `REVISION_CONFLICT`) |
| 문서 버전과 분리 | 위치·순서 변경은 페이지 `revision`을 올리지 않는다. 편집기를 열어 둔 사용자의 저장과 충돌하지 않는다 |
| 정렬 | 형제 사이 정렬은 **`sort_order ASC → id ASC`로 고정**하고 서버 SQL 한 곳에서만 정한다. 화면은 서버 순서를 그대로 묶기만 한다 |
| 순서 정규화 | 순서 저장은 현재 형제 목록과 정확히 같은 집합이어야 하고, 저장하면 형제를 0부터 연속값으로 다시 매긴다. 위치 변경 때도 원래 형제와 새 형제 양쪽을 연속값으로 정리하고, 옮긴 페이지는 새 형제의 맨 끝에 둔다 |
| 생성 | 새 페이지 생성 창에서 상위를 고를 수 있고 기본값은 "상위 없음"이다. 새 페이지는 같은 상위 아래 맨 끝(`현재 최대 + 1`)에 둔다. 상위 검증은 위치 변경과 같다 |
| 삭제 | 하위 페이지가 있으면 "하위 페이지 n개를 먼저 옮기거나 삭제하세요"로 거부한다. 삭제 영향(사용처)에 "하위 페이지 · 제목"이 나온다. 선택 삭제에서 상위와 하위를 함께 고르면 하위부터 삭제한다(사용자가 직접 고른 것만, 연쇄 삭제 아님) |
| 주소·메뉴 | 위치를 바꿔도 페이지 주소와 메뉴는 자동으로 바뀌지 않는다 |
| 활동 이력 | "페이지 위치 변경"(이전 상위 → 새 상위), "페이지 순서 변경"을 기록한다 |

### 공개 상태 정책

상위와 하위의 게시 상태는 서로 영향을 주지 않는다.

- 상위 페이지를 비공개로 바꿔도 게시된 하위 페이지를 **자동으로 비공개로 바꾸지 않는다.** 상위를 공개 중단할 때 "게시된 하위 페이지 n개는 계속 공개됩니다"라고 확인하고, 목록에서 경고만 표시한다.
- 상위가 게시되지 않은 상태에서 하위 페이지를 게시하는 것도 막지 않고 경고만 한다.
- 공개 API에는 계층이 없으므로 게시된 하위 페이지는 상위 상태와 관계없이 자기 주소로 열린다. 공개 API에 `parentId`는 실제 홈페이지가 필요로 할 때 추가한다.

### 기존 경로와의 호환

- 기존 화면(`/admin/legacy`)의 페이지 저장은 `parent_id`·`sort_order`를 건드리지 않아 계층이 유지된다. 기존 화면에서 만든 페이지는 최상위 맨 끝이다.
- 페이지 목록 SQL은 기존 화면과 함께 쓰므로 기존 화면의 목록도 최신순에서 구조 순서(`sort_order`, `id`)로 바뀐다(비교·복구용 화면).
- 버전 되돌리기는 계층을 바꾸지 않는다.

## 3. API (관리자 내부 API)

| 요청 | 권한 | 내용 |
|---|---|---|
| `GET /api/admin/next/pages`, bootstrap `pages` | 기존과 같음 | 행에 `parentId`, `sortOrder` 추가 |
| `PUT /api/admin/next/pages/{id}/placement` `{parentId, expectedParentId}` | MANAGE_SITE | 위치 변경, 갱신된 목록 반환 |
| `PUT /api/admin/next/page-order` `{parentId, pageIds}` | MANAGE_SITE | 형제 순서 저장, 갱신된 목록 반환 |
| `POST /admin/pages/save-json` 생성 | 기존과 같음 | 선택 입력 `parentId` 추가. 주소는 그대로 |

공개 API(`/api/public/v1/**`)는 바꾸지 않는다.

## 4. React 화면 (2단계)

- 전체 페이지 현황: 최상위 페이지 아래에 하위 페이지를 들여쓰기(`↳`)로 보여 주고, 상위에는 "하위 n개", 경고 대상 하위에는 "상위 페이지 비공개"를 표시한다. 검색·상태 필터 중에는 평평한 목록에 "상위: 제목"을 표시한다.
- SUPER_ADMIN의 "위치": 상위 선택(갈 수 없는 곳은 이유와 함께 비활성)과 형제 안 ↑↓ 순서. ADMIN은 조회만 한다.
- 페이지 편집기: 제목 위에 "인사교 소개 › 후기". 위치 변경은 목록 한 곳에서만 한다.
- 새 페이지 생성 창: 상위 페이지 선택, 기본 "상위 없음".
- 공개 중단 확인: 게시된 하위 페이지 수 안내.
- 메뉴 관리: 페이지 선택지에 "인사교 소개 › 후기"처럼 표시만 한다.

## 5. 적용과 rollback

- `V13PromotionTool`: 정상 종료한 V12 DB의 **사본**에만 적용한다.
  - plan: 사본이 원본과 바이트 동일, schema V12, V1~V12 checksum 동일, 대기 migration은 V13 하나
  - migrate: `SITE_PAGES`에 `PARENT_ID`·`SORT_ORDER`만 추가되고 다른 표·열 불변, 기존 열 기준 데이터 지문 불변, 새 값은 모두 NULL/0, 원본 불변
  - receipt `MIGRATED_V13`
- 서버의 `CURRENT_VERSION`을 13으로 올린다. 새 실행본 `V13-RC1`은 migration한 사본 DB를 쓴다. JAR 교체·선택 스크립트가 `MIGRATED_V13`도 받도록 고친다.
- **운영 승격 전 필수**: 최신 V12 정상 종료 백업과 그 사본으로 하는 리허설(3개 역할, 정상 종료·재시작, rollback).
- **rollback 조건(수용됨)**: V13 DB는 V12 JAR로 열 수 없으므로 rollback은 RC2와 적용 전 V12 DB로 돌아간다. V13 적용 후 작성한 내용은 빠질 수 있다. 안정화 기간에는 활동 이력에서 적용 시각 이후 작업 목록을 뽑아 되돌린 뒤 다시 입력할 수 있게 한다.

## 6. 진행 단계

1. 서버: migration, `V13PromotionTool`, 서비스 규칙, API, 권한, 테스트
2. React 화면
3. RC 빌드 → 최신 V12 백업 사본으로 리허설 → 보고 → 8095 적용(사용자 승인 후)

각 단계의 결과는 이 문서에 추가한다.

## 1단계 결과: 서버 (2026-09-30)

| 파일 | 내용 |
|---|---|
| `db/migration/h2/V13__page_hierarchy.sql` | 1절의 SQL. 데이터 변경 없음 |
| `FileDatabaseSafety.CURRENT_VERSION` | `"13"`. 서버는 V13 DB와 `MIGRATED_V13` receipt만 받는다. V12 DB·receipt는 거부 |
| `V13PromotionTool` | plan/migrate(5절). 웹 서버는 `AICA_V13_PROMOTION_ENABLED`로 시작할 수 없다 |
| `V12PromotionTool` | V11→V12 전용으로 manifest·검사를 V12까지로 한정(V13이 JAR에 있어도 동작). `inspect`는 현재 schema의 읽기 전용 검사로 계속 실행 스크립트가 쓴다 |
| `PageHierarchy` | 깊이·순환·홈 규칙(`MAX_DEPTH = 2`), 형제 정렬 |
| `PageService` | `place`(위치 변경, 양쪽 형제 정규화), `reorder`(형제 순서, 연속값), 상위를 지정한 생성, 하위가 있으면 삭제 거부 |
| `SiteService` | 첫 화면은 최상위이면서 하위 페이지가 없는 페이지만 |
| `UsageService`·`DeletionImpactService` | 삭제 영향에 "하위 페이지 · 제목" |
| `CmsMapper.xml` | 목록 `ORDER BY sort_order, id`, 생성 시 형제 맨 끝, `placePage`·`pageSortOrder`(revision 불변) |
| `NextWorkspaceApi`·`PageController`·`SecurityConfiguration` | 3절의 API. 새 PUT 2개는 MANAGE_SITE |

API 목록 행은 `sort_order, id` 순서의 평평한 목록이다(상위와 하위가 섞여 나온다). 화면은 이 순서를 유지한 채 상위 아래로 묶는다.

검증: 서버 전체 테스트 202개 실행, 실패 0, 환경 조건 제외 6. 프런트 테스트 70개(변경 없음).

- `PageHierarchyMigrationTest`(2):
  - V12 파일 DB 사본에서 plan/migrate가 성공하고 원본 해시 불변, 기존 페이지·메뉴 행 불변, 새 열 NULL/0
  - 자기 참조와 하위가 있는 상위 삭제는 DB가 거부하지만, 3단계 삽입은 DB가 막지 않는다(운영 제한은 서비스에만 있음을 확인)
  - V12 schema·V12 receipt는 V13 런타임에서 거부
  - 계획 뒤 바뀐 사본, V11 DB는 거부
- `PageHierarchyIntegrationTest`(8):
  - 위치 변경과 양쪽 형제 정규화, revision 불변, 활동 이력
  - 2단계·자기 하위·자기 자신·홈 양방향·없는 상위 거부, 오래된 화면은 409
  - 형제 순서 저장과 집합 불일치 거부
  - ADMIN·SUPPORTER 403, CSRF 없음 403, 비로그인 조회 401
  - 상위를 지정한 생성(맨 끝), 홈·하위 페이지 아래 생성 거부, 기존 페이지에 상위 지정 거부
  - 하위가 있으면 삭제 거부·삭제 영향 표시, 하위를 먼저 지우면 상위 삭제 가능
  - 첫 화면 설정 규칙
  - 문서 저장·되돌리기가 계층 유지, 상위 공개 중단 후에도 하위는 게시 유지, 공개 API에 계층 필드 없음
- 기존 테스트는 migration 이력 기대값에 13 추가, 검토된 migration 목록에 V13 추가, V11→V12 도구 테스트가 schema 12를 명시적으로 확인하도록 고쳤다.

아직 하지 않은 것: React 화면(2단계), 실행 스크립트의 `MIGRATED_V13` 지원과 RC 빌드·리허설(3단계). 8095(V12 RC2)와 운영 DB는 바꾸지 않았다.
