# V16 콘텐츠 작업 하위 항목 (2026-10-01)

최종 검수 전에 마무리하는 보완 범위: **관리자가 콘텐츠 작업의 하위 항목을 직접 구성하는 기능**. 드래그, 전체 디자인 재개편, 주제 관리, 새로운 홈페이지 기능은 범위 밖이다.

## 1. 문제와 결정

V14~V15의 콘텐츠 작업 사이드바는 글 종류를 연결한 페이지 아래에 **그 종류에 허용된 활성 주제 전부**를 하위 항목으로 자동으로 붙였다(저장된 데이터가 아니라 화면이 사전을 읽어 그린 것). 사용자가 8095에서 직접 써 본 결과, 연결만 했는데 만들지 않은 하위 항목이 생기는 것이 혼란의 핵심이었다.

사용자 결정(2026-10-01):

- 유형 연결만으로 하위 항목을 자동 생성하지 않는다. 연결하면 그 페이지 항목 하나만 생긴다.
- 하위 항목은 관리자가 추가하고, 이름·기존 주제 선택·순서 변경·제거를 직접 한다.
- 항목에서 글 목록을 보고 새 글을 쓴다. 항목이 없어도 페이지에서 글을 쓰고 관리한다.
- 기수는 목록의 검색 필터로 유지한다.
- 항목을 제거해도 글·주제 원본은 유지한다. 주제 사전과 사이드바 항목을 동일시하지 않는다.
- "한 유형당 대표 영역 하나" 규칙은 페이지 연결에만 적용하고, 같은 유형을 좁혀 보는 하위 탐색을 막지 않는다.
- 관리자용 하위 탐색과 실제 홈페이지 하위 페이지를 화면에서 혼동하지 않게 구분한다.

## 2. 구조

| 항목 | 내용 |
|---|---|
| DB | `V16__content_work_nodes.sql`: 새 표 `content_work_nodes(id, page_id → site_pages ON DELETE CASCADE, name VARCHAR(80), topic_id → topics, sort_order, created_at, updated_at)` + 인덱스 `(page_id, sort_order)`. 기존 표·열·행 변경 없음. 주제 FK는 삭제 연쇄가 없어 항목이 주제를 지우지 못하고, 항목 제거는 이 행 삭제뿐이다. "연결된 PAGE에만", "그 종류에 허용된 활성 주제만", "페이지당 20개"는 서비스 규칙 |
| 적용 도구 | `V16PromotionTool` plan/migrate: 사본만, 표 `CONTENT_WORK_NODES` 하나만 추가(비어 있음), 기존 표·열·데이터 지문 불변. receipt `MIGRATED_V16`. `CURRENT_VERSION` 16. `AICA_V16_PROMOTION_ENABLED`로는 웹 서버 시작 불가 |
| 서비스 | `ContentNodeService`: 추가·변경·순서·제거(MANAGE_SITE). 규칙: 묶음 거부, 글 종류가 연결되지 않은 페이지 거부, 주제 없음·모르는 주제·사용 중지 주제·다른 종류의 주제 거부, 같은 이름·같은 주제 중복 거부. 활동 이력 "콘텐츠 작업 하위 항목 추가/변경/순서 변경/제거". 페이지 문서·revision은 건드리지 않음 |
| 연결 변경 | `PageService.compose`: 글 종류를 바꾸거나 해제하면 그 페이지의 항목을 함께 제거하고 활동 이력에 "하위 항목 N개 제거(글·주제 유지)"를 남긴다. 메뉴 노출·표시명만 바꾸면 항목은 그대로 |
| 페이지 삭제 | DB 연쇄로 항목도 삭제. 삭제 영향 문구에 "콘텐츠 작업 하위 항목도 함께 삭제됩니다(글과 주제는 유지)" |
| API | `GET/POST /api/admin/next/pages/{id}/content-nodes`, `PUT /api/admin/next/pages/{id}/content-node-order`, `PUT/DELETE /api/admin/next/content-nodes/{id}`. 쓰기는 MANAGE_SITE(SUPER_ADMIN), GET은 `pages/**`와 같이 ALL_POSTS. 모든 응답은 그 페이지의 항목 목록(저장 순서) |
| bootstrap | `contentAreas[].nodes = [{id, name, topicId}]` — 모든 역할에 같은 목록 |
| 사이드바 | 연결된 페이지 + 저장된 항목만. 주소 `/posts?area=<pageId>&node=<id>`(+`typeCodes`, `topicIds`). 항목 주제가 사용 중지되거나 허용에서 빠지면 그 항목은 비활성·안내(전체로 넓히지 않음). 없어진 항목 주소는 "전체 콘텐츠 / 현재 구성" 안내 |
| 목록·작성 | 항목에서 목록은 종류+주제로 좁혀지고, "＋ 새 <항목 이름> 작성"은 종류·주제를 기본값으로 넣는다. 기수는 목록 검색 필터 그대로 |
| 구성 대화상자 | "콘텐츠 작업 연결" 아래 **관리자 콘텐츠 작업 하위 항목** 구역: 설명(왼쪽 콘텐츠 작업 메뉴에 보이는 항목이며 홈페이지 하위 페이지와 별개, 홈페이지에 나타나지 않음, 항목 없이도 글 관리 가능, 제거해도 글·주제 유지), 항목 목록(이름·주제·↑↓·수정·제거), 추가/수정 폼(이름 + 허용 주제 선택, 다른 항목이 쓰는 주제는 비활성). 연결을 새로 저장하면 대화상자를 닫지 않고 바로 항목을 추가할 수 있다. 종류를 바꾸거나 해제하면 "하위 항목 N개도 함께 제거" 경고. 목록 행 메타에 "관리자 하위 항목 N개" |
| 공개 API·구성 게시 | 무관. 항목은 관리자 탐색 전용이며 `/menus`·`/structure`·페이지 응답을 바꾸지 않는다 |
| 스크립트 | `promote-v16-runtime.ps1`(V15 원본 → V16 사본, 한 단계), `select-v12-runtime.ps1`·`start-current-ui.ps1`·`swap-v12-jar.ps1`·`start-v12-runtime.ps1`이 `MIGRATED_V16`·`v16` 수용 |

## 3. 검증

코드 commit `9c4c649`. JAR `target/backoffice-0.0.1-SNAPSHOT.jar` sha256 `0fee384ed76e92087d01625a1b59f8263c4b1e48548958f979ea8c29d9dedb89`(테스트 전체 통과 뒤 같은 소스로 `-DskipTests package`).

- 서버 전체 테스트 233개 실행, 실패 0, 환경 조건 제외 6
  - `ContentWorkNodesMigrationTest`(2): V15 파일 DB 사본 plan/migrate, 원본 불변, 기존 행·주제·허용 관계·게시본 표 불변, 새 표 비어 있음, FK(없는 페이지·주제 거부, 주제 삭제 거부, 페이지 삭제 연쇄), 바뀐 사본·V14 DB 거부, V15 receipt 거부
  - `ContentWorkNodeIntegrationTest`(2): 연결만으로 항목 0(세 역할 동일) / 추가·순서·이름·주제 변경이 모든 역할의 bootstrap과 GET에 반영 / 규칙 거부(미연결 페이지·묶음·다른 종류 주제·사용 중지 주제·없는 주제·주제 없음·같은 이름·같은 주제·빈 이름·바뀐 목록·없는 항목 404) / 페이지 revision·버전 불변 / 항목 아래 쓴 글은 항목 제거 뒤에도 주제 유지, 사전 불변 / 활동 이력 5건 / ADMIN·SUPPORTER 쓰기 403, SUPPORTER GET 403 / 연결 해제·종류 변경 시 항목 제거와 이력 "하위 항목 2개 제거", 메뉴 설정만 바꾸면 유지 / 공개 `/menus`·`/structure`·페이지 응답 바이트 동일 / 삭제 영향 문구, 페이지 삭제 시 연쇄
  - 기존 시험: Flyway 이력 기대값에 16 추가(3개), `ClassificationMigrationTest` 승인 migration 목록에 V16 추가, `StructureMembershipMigrationTest`는 V15 schema 기준으로 고정, `SiteCompositionIntegrationTest`의 contentAreas 기대값에 `nodes:[]`
- 프런트 테스트 79개(`contentNavigation.test.ts` 재작성: 저장된 항목만·순서·없는 항목·사용 불가 주제·주소 `node=`), 타입 검사·빌드 통과

### 사본 검증 (8095 멈추지 않음)

원본: `.cache/category-rehearsal-20261001/v15/runtime`(카테고리 전환 리허설을 마친 V15 사본, 정상 정지 `stopped-1790783241711.json`과 DB 해시 일치 = 현재 8095와 같은 운영 데이터 상태). 결과: `.cache/v16-rehearsal-20261001/runtime`(8097, JAR `0fee384e…`).

| 항목 | 결과 |
|---|---|
| `promote-v16-runtime.ps1` | 통과. `MIGRATED_V16`, 이력 16, JAR가 더한 migration은 V16 하나. 원본 V15 DB 불변 |
| 임시 SUPER_ADMIN | 사본 DB에 직접 삽입 #39(v16-rehearsal-root@example.com). 비밀번호는 문서에 남기지 않는다. 사본 전용 |
| 사전 | REVIEW 주제가 프로젝트 하나뿐이라 `REVIEW_LIFE` "생활"을 추가 API로 넣음(사본만) |
| 연결 | 새 페이지 #98 "[V16 리허설] 후기 전체"(초안)에 후기 연결 → bootstrap contentAreas 1개, `nodes: []` |
| 항목 | 추가 2개 → 순서 바꾸기 → 이름 변경, bootstrap 반영. 거부: 다른 종류 주제·같은 이름·같은 주제·주제 없음·미연결 페이지(#65) 모두 400 |
| 목록·작성 | 항목(프로젝트) 목록 수 +1: 항목에서 쓴 글 #137이 항목 목록에 나타남 |
| 제거 | 항목 제거 뒤 글 #137의 주제 유지, 주제 수 불변 |
| 연결 해제 | 항목 모두 제거, contentAreas 비어 있음. 다시 연결 후 항목 추가 |
| 화면(브라우저) | 전체 페이지 현황 행 메타 "콘텐츠 작업: 후기 · 관리자 하위 항목 1개", 구성 대화상자의 "관리자 콘텐츠 작업 하위 항목" 구역에서 "생활 이야기"(주제 생활) 추가 → 콘텐츠 작업 사이드바에 "후기 전체 › 프로젝트 후기 · 생활 이야기", 항목 클릭 시 목록이 주제로 좁혀지고 "＋ 새 생활 이야기 작성" |
| 세 역할 | ADMIN #41·SUPPORTER #42(임시 발급, 확인 후 사용 중지): 사이드바 동일, 항목 추가·변경·순서·제거 403, SUPPORTER GET 403·ADMIN 200. (#40은 첫 시도에서 발급만 되고 실패해 사용 중지) |
| 공개 API | `/menus`·`/structure`·`/pages/1`·`/pages/65`·`/posts`·`/posts?categoryId=1` 항목 작업 전후 동일 |
| 정상 정지 → 재시작 | 항목 2개·글 #137 유지, 다시 정상 정지 |

## 4. 8095 적용 (별도 승인 필요)

1. 8095(V15 RC2, DB는 `V15-RC1-20260930/runtime/db`) 정상 종료(STOP.cmd) → 백업(`.cache/v12-release/backups/aica-local.before-V16-…`).
2. 새 RC 폴더 `.cache/v16-release/V16-RC1-20261001/runtime`(JAR `0fee384e…`, `graceful-stop.jar`·assets 복사, `runtime.json`에 `database` 없음) → `promote-v16-runtime.ps1 -Source .cache/v15-release/V15-RC2-20261001/runtime -Target … -AuthorizeMigration`. 사본에만 적용되고 RC1 DB는 그대로.
3. `select-v12-runtime.ps1 … -AuthorizeSelection` → START → 세 역할·공개 API 확인(적용 직후 항목은 0개, 공개 응답 동일).
4. 되돌리기: V16 RC 폴더를 버리고 V15 RC2를 다시 선택(DB 불변). V16 이후 운영 변경은 잃는다.
