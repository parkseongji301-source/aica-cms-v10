# AICA 3C-3 맛집 연결 결과

2026-09-27. 기존 posts/ContentEditor에 RESTAURANT 주소를 1:1 확장했다. 설계와 영향 범위를 먼저 제시하고, 별도 V6 사본의 V7 migration을 검증한 후 앱 코드를 구현했다. 설계는 `REACT_PHASE3C3_DESIGN.md`에 있다.

## 환경과 기준점

- 맛집 검증 URL: http://127.0.0.1:8085/admin-next/posts?view=structure&restaurantSection=all&typeCodes=RESTAURANT
- DB: `.cache/react-phase3b2a-data/aica-phase3c3.mv.db`, 실행 파일: `.cache/react-phase3c3-test.jar`.
- 재실행: `scripts/run-restaurant-copy.ps1`(기본 8085). 최신 빌드 JAR로 지정된 사본만 연다.
- 원본 `.local-data/aica-local.mv.db`는 V3 그대로다. V4~V7을 적용하지 않았다. 원본 해시와 기존 migration/격리된 예전 V4/보호 설정을 비교했다.
- 기존 후기 8083, FAQ 8084는 별도 DB/JAR로 보존했다. FAQ 서버는 정상 종료→콜드 복제→같은 JAR/DB 재시작으로 기준점을 확보했다.
- 기준점 `.cache/checkpoints/20260927-132558-react-phase3c3/`: 작업 전 수정·미추적 포함 소스 218개, Git 이력/patch, V6 DB와 대응 JAR. 복구 절차는 같은 폴더의 RESTORE.md.

## 1. 구조화 데이터와 migration

| 테이블 | PK 및 FK | 값 |
| --- | --- | --- |
| post_restaurant_details | post_id → posts.id, 삭제 시 CASCADE | address VARCHAR(500), NOT NULL, 기본 빈 문자열 |
| post_publication_restaurant_details | post_id → post_publications.post_id, 삭제 시 CASCADE | address VARCHAR(500), NOT NULL |

V7은 빈 테이블 두 개만 추가한다. posts 공통 컬럼, 콘텐츠 ID, category_id, 분류·미디어 연결, 메뉴, 페이지/블록, 기존 발행 행은 수정하지 않는다. 새 주소 테이블에 독립 ID나 기존 콘텐츠 복사본을 만들지 않는다. 기존 주소 행이 없는 RESTAURANT은 빈 주소로 조회하며 읽기만으로 행을 생성하지 않는다.

주소는 선택 입력이며 최대 500자, 앞뒤 공백 제거다. 별도 주소 검색·지도 검증은 없다. RESTAURANT 타입에만 주소를 허용하는 조건은 Spring 서비스가, 1:1 관계와 참조 무결성은 DB PK/FK가 보장한다.

- 먼저 정지된 FAQ V6 사본에서 V7 단독 적용 1회, 재실행 0회, 완전 재접속 후 이전 24개 테이블의 모든 컬럼/이전 Flyway 이력 불변, 새 테이블 0행을 확인했다.
- 실제 실행 사본에서도 동일 검사를 통과한 후 앱을 시작했다.
- 자동 테스트는 별도 V3 사본의 V3→V6 검사와 별도 V6 사본의 V6→V7 검사를 모두 실행한다. 앱/메모리 DB는 V1~V7 전체 경로를 검증한다.
- Flyway 경로는 기존 `classpath:db/migration/h2`다. 배포 JAR에는 V1~V7만 포함하며, V4~V6 파일은 변경하지 않는다. 미완성 예전 V4 격리를 유지한다.
- 새 migration에 사전/IA/검증 글 seed를 넣지 않았다.

## 2. 공통 ContentEditor와 API 계약

| 화면 의미 | 내부 저장 |
| --- | --- |
| 식당명 | posts.title / title |
| 소개 | posts.content 및 posts.rich_content / content, richContent |
| 이미지·첨부 | 기존 RichEditor, RichTextService, MediaService 및 미디어 연결 |
| 주소 | post_restaurant_details.address / restaurant.address |

콘텐츠 유형 RESTAURANT에서만 주소 입력을 표시한다. 기수는 생성 시 선택 없음, 별도 맛집 주제는 등록하지 않았다. 기존 분류 UI/검증, 서식·이미지 삽입, 저장, 권한, CSRF를 재사용한다. 새로운 맛집 작성기나 저장소는 없다.

주소는 기존 dirty fingerprint, 자동저장(기존 정책 유지), 미리보기 요청에 포함된다. 주소만 수정해도 posts revision이 증가하고 기존 충돌 검사로 오래된 저장을 막는다. 미리보기는 입력 주소를 렌더링하고 DB에는 쓰지 않는다. 저장 후 서버의 정규화된 주소로 동기화한다.

**새 URL은 없고 기존 API DTO를 확장했다.**

- POST `/api/admin/next/posts`: 기존 초안 생성, RESTAURANT 기본값을 같은 대화상자에서 지정.
- GET/PUT `/api/admin/next/posts/{id}`: 조회/초안 저장에 `restaurant` 추가.
- GET/POST `/api/admin/next/posts/{id}/preview`: 저장된 주소 또는 입력 중 주소.
- GET `/api/admin/next/posts/{id}/publication`: 독립 발행 snapshot의 주소.

```json
{"restaurant":{"address":"주소 문자열"}}
```

요청에서 필드 생략은 기존 주소 보존이다. `{ "address": "" }`는 명시적 비우기다. `restaurant: null`, 숫자/잘못된 구조/알 수 없는 추가 필드는 400이다. 다른 유형의 조회 응답은 `restaurant: null`이다. React는 이 null을 저장 요청에 보내지 않고 필드를 생략한다.

다른 유형으로 전환했는데 기존 주소가 남으면 자동/수동 저장과 미리보기를 대기한다. 운영자가 주소를 비우거나 유형을 되돌려야 한다. 명시적으로 비우고 다른 유형으로 저장하면 초안 확장 행을 제거한다. 기존 발행본 주소는 재발행 전까지 남는다.

## 3. 사이트 구조와 같은 원본

```text
인사교 Real Life
└ 인사교 꿀팁
  └ 근처 맛집 → 기존 콘텐츠 목록(typeCodes=RESTAURANT)
```

`contentNavigation.ts`의 탐색 설정을 기존 ContentTree/목록/ContentPanel로 연결했다. `restaurantSection=all`은 탐색 문맥이며 DB 분류가 아니다. 이 경로에서는 타입을 RESTAURANT으로 고정하고 주제 조건은 사용하지 않는다. 검색·상태·기수·기존 category 추가 필터는 기존 목록 API/EXISTS 조건을 쓴다.

개별 식당은 오른쪽 목록에만 표시한다. 홈페이지 메뉴나 새 페이지, 위치 이름의 topic을 생성하지 않는다. 두 모드에서 `/posts/{id}/edit`의 같은 editor key와 API ID를 사용한다. 보기 전환은 사이드바/URL 문맥만 변경하며 ContentEditor의 주소·본문 입력 상태를 유지한다.

## 4. 초안/발행본 및 Thymeleaf

주소 검증·초안 저장·미디어·분류 저장은 기존 PostService 트랜잭션 안에서 함께 처리한다. 발행 시 기존 현재 발행본 교체 흐름에 주소 snapshot 복사를 추가했다. 별도의 발행 정책/승인 단계를 만들지 않았다.

초안 A를 발행한 후 초안 주소 B를 저장해도 발행본은 A다. 재발행 후 B가 된다. 다른 유형으로 바뀐 초안은 주소를 조회/표시하지 않지만 이전 RESTAURANT 발행본 주소는 보존한다. 다른 유형으로 재발행한 뒤에는 발행 주소 행도 없어진다.

React 우측에는 현재 초안 주소와 현재 발행 주소를 별도로 표시한다. 기존 발행본 확인 링크는 발행 snapshot의 주소를 보여준다. 현재 초안의 주소와 공개 주소가 같다고 오해하지 않도록 재발행 안내를 둔다.

Thymeleaf에는 주소 편집 기능을 중복 구현하지 않았다. 현재 초안 주소 요약과 React 편집 링크만 추가했다. HTML 저장·자동저장/save-json·발행 요청은 주소 필드가 없어도 기존 주소를 보존한다. 발행은 최신 초안 주소를 snapshot에 복사한다. 기존 발행본 미리보기에도 snapshot 주소를 표시한다.

## 5. 변경 범위

- 새 Java/Mapper: `RestaurantDetailsService`, `RestaurantMapper`, `RestaurantMapper.xml`.
- 기존 Java: PostService 저장/발행/삭제/미리보기, NextPostApi 선택적 주소 DTO, PostController 읽기 전용 표시.
- 새 DB: `V7__restaurant_details.sql`만 추가.
- React: types/api/contentDocument/restaurantFields, ContentEditor 및 표시 명칭, 공통 탐색/목록/생성 대화상자, 스타일.
- 기존 템플릿: posts/form, cms/preview에 주소 요약.
- 테스트: RestaurantMigrationTest/RestaurantWorkflowIntegrationTest/restaurant.test.ts. 기존 schema 목록 기대값에 V7을 명시하고 V3→V6 테스트의 target=6을 유지했다.
- 실행/기록: run-restaurant-copy, 설계/결과 및 위험 문서.

## 6. 검증 결과

- Spring 최종 `clean verify`: **85개 통과, 실패/오류/제외 0**. 원본 V3 사본 검사와 FAQ V6 실제 사본 검사도 제외 없이 실행했다.
- 프런트: **20개 통과**, TypeScript/Vite 빌드 성공. 기존 번들 크기/CSS 경로 안내는 유지한다.
- 신규 자동 검사는 주소 저장/재조회/미리보기, 주소만 변경한 revision, 미디어 재사용, 빈 주소, 500자 제한/잘못된 JSON 거부, 권한/CSRF/충돌, 유형 전환과 명시 비우기, 삭제, 발행 실패 시 전체 트랜잭션 롤백을 포함한다.
- 첫 전체 검사 3건은 기존 기대 migration 목록이 V6까지만 고정되어 실패했다. V7을 명시한 뒤 새 사본으로 전체 85개를 재실행했다. 기존 데이터 비교나 검사를 제외하지 않았다.
- 브라우저에서 식당명·소개·주소·보관함 이미지 삽입→저장→새로고침/재조회, 입력 중 보기 전환, 두 목록에서 동일 ID 105, 유형 전환 경고, 기존 관리자 저장·발행·재발행을 확인했다.
- 주소만 A→B 변경 시 revision **3→4**, 제목/소개/이미지/분류/category는 동일, 발행본 A 전체 응답은 불변. Thymeleaf 임시저장 후 초안 B·발행 A 유지. 재발행 후 초안/발행 revision **6/6**, 주소 B로 일치했다.
- 최종 사본은 기존 17건 + 검증 식당 ID **105** 1건, 총 18건이다. 이미지 ID **65**는 실제 식당 사진이 아닌 검증 표식 이미지다. 주소/소개에도 검증용임을 표시했다. 기수·주제 모두 없음이며 실제 맛집 사전/데이터를 등록하지 않았다.
- API 및 브라우저 목록: 맛집 **1**, 후기 **4**, FAQ **4**. REVIEW_LIFE **1**, FAQ_LIFE **1**, 교차 유형 주제 **0**. total과 고유 ID/실제 행 수가 일치한다.
- 기존 17개 콘텐츠의 전체 문서, 발행본 97/101, 페이지 65/블록, 메뉴/category/권한 응답은 작업 전과 동일하다. 다른 유형 응답에 추가된 `restaurant:null`만 비교 시 제외했다. FAQ 질문/답변과 주소 입력 비노출, 후기 목록도 브라우저에서 확인했다.
- 완료 DB를 정상 종료 상태로 백업하고 같은 JAR/DB로 재시작했다. 전체 18개와 ID 105의 초안/발행본 응답이 동일하다. 백업의 별도 읽기 전용 사본에서도 V1~V7 이력, 주소 초안/발행 각 1행, 유형 혼입 0, 맛집 허용 주제 0을 확인했다.
- 완료 JAR의 V1~V7 SQL은 소스와 동일하며, 원본 V3 SHA-256은 작업 전후 동일하다.

주요 증거: `.cache/react-phase3c3-final-verify2.log`, `react-phase3c3-runtime-migration.json`, `react-phase3c3-pre-edit-api-audit.json`, `react-phase3c3-draft-b-published-a.json`, `react-phase3c3-legacy-save.json`, `react-phase3c3-republished-b.json`, `react-phase3c3-final-api-audit.json`, `react-phase3c3-restart-audit.json`, `react-phase3c3-completed-db-audit.json`, `react-phase3c3-package-audit.json`.

화면: `.cache/react-phase3c3-screenshots/draft-b-published-a.png`, `type-change-warning.png`, `completed-editor.png`.

![주소 초안 B와 발행본 A를 분리해 표시한 검증 화면](../.cache/react-phase3c3-screenshots/draft-b-published-a.png)

## 7. 향후 확장 의견

지도에 단순 링크만 필요하면 현재 주소로 링크를 구성할 수 있다. 지도 핀·거리 검색이 필요해지면 주소의 표시값과 좌표(lat/lng), 좌표 획득 방식/갱신 정책을 분리해 **동일한 확장 테이블과 발행 snapshot**에 추가하는 방식이 자연스럽다. 지도 API 공급자·키·주소 변경 시 재확인 여부도 그때 결정한다. 이번에는 추가하지 않았다.

인터뷰도 제목/본문/이미지와 INTERVIEW 유형만으로 글 형태를 관리할 수 있다. 단순 영상 링크를 본문에 넣는 수준이면 새 컬럼은 필요하지 않다. 목록의 공통 영상 카드·영상별 검증/재사용이 요구되면 영상 URL/provider ID 같은 최소 구조화 필드를 별도로 설계하고 초안/발행 snapshot을 함께 적용하는 것이 좋다. 인터뷰이 프로필·소속·연도 등의 필드는 요구 확인 전 확정하지 않는다.

파일 DB 연결 수명과 H2/Flyway 호환 경고는 `DB_MIGRATION_RISKS.md`에 별도로 유지한다. 사본 기능 성공이 원본 migration 승인이나 위험 해소를 의미하지 않는다.
