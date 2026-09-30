# 공개 홈페이지 연결 계약 · 5A

2026-09-27. 사용자가 실제 홈페이지가 아직 없다고 확인했다. 이 문서는 미래 홈페이지와 연결할 API 계약이며, 별도 공개 사이트를 만들거나 배포한 결과가 아니다.

## 연결 범위와 환경

- 기존 Spring Boot 3.4.5 / Java 17 / MyBatis / H2 검증 환경을 사용한다. React + TypeScript는 관리자이며 공개 홈페이지가 아니다. Thymeleaf `/admin/**`도 관리자·내부 미리보기다.
- 홈페이지의 소스·기술·정적/API 방식·라우팅·배포·동일/별도 서버 구성은 아직 없다. 앱에 등록된 다른 저장소를 실제 홈페이지로 간주하지 않았다.
- 5A 서버는 `127.0.0.1:8092`, 최신 4D V9 사본에서 시작한다. 원본 `.local-data/aica-local.mv.db`는 V3 그대로 유지한다. 새로운 migration은 없다.
- 우선 동일 출처 프록시로 `/api/public/v1/**`를 연결하는 계약이다. 별도 출처 브라우저 CORS는 열지 않았다. 실제 홈페이지 origin이 정해지면 허용 목록을 정해야 한다. 서버 간 조회는 관리자 쿠키 없이 가능하다.
- `/`를 공개 홈페이지로 바꾸지 않았다. 기존 `/admin`, `/admin-next`, 로그인·세션·CSRF·권한은 그대로다.

## HTTP 계약

모든 엔드포인트는 익명 GET/HEAD 전용이다. POST/PUT/PATCH/DELETE는 403. 공개용 필터 체인은 관리자 세션을 읽거나 생성하지 않는다. JSON/파일은 `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`를 사용한다. 철회된 발행본이 캐시에 남지 않도록 5A에서는 CDN 캐시를 설정하지 않는다.

| GET 경로 | 응답 | 규칙 |
|---|---|---|
| `/api/public/v1/menus` | `PublicMenu[]` | 첫 사이트 구성 게시 전: visible 메뉴(site_menus)만, 페이지는 현재 공개 상태와 발행본이 모두 필요. 첫 구성 게시 후: 게시된 구성의 메뉴 노출 영역 + 외부 링크(최상위 끝). V14 |
| `/api/public/v1/structure` | `PublicStructure` | 게시된 사이트 구성 전체(메뉴에서 숨긴 공개 페이지 포함). 게시 전에는 `publishedAt:null, items:[]`. V14 |
| `/api/public/v1/pages/{pageId}` | `PublicPage` | 페이지 발행 snapshot만 |
| `/api/public/v1/pages/by-slug/{slug}` | `PublicPage` | 발행 당시 slug로 조회. `home`, `about` 등 영문·숫자·하이픈 |
| `/api/public/v1/pages/{pageId}/blocks/{blockId}/posts` | `PublicPosts` | 그 페이지 **발행본**의 표시 중 POSTS 블록만. 요청자가 query/manual 설정을 넘기지 않음 |
| `/api/public/v1/posts/{postId}` | `PublicPost` | 공개 상태·미삭제·발행본 존재 필요 |
| `/api/public/v1/posts?categoryId=11&page=1&limit=6` | `PublicPosts` | 기존 category 메뉴용. categoryId 생략 시 전체 발행 콘텐츠. page 1~10000, limit 1~20, 최신순 |
| `/api/public/v1/media/{mediaId}/file` | 파일 bytes | 현재 공개 페이지/콘텐츠 발행본에서 참조하는 파일만 |

전체 타입은 `docs/public-api-v1.ts`와 Java `PublicDocuments`에 있다. `/pages` 전체 목록, 초안, 계정, 이력, 사전 관리, 설정, 템플릿 관리 API를 공개하지 않는다.

- 없는 대상·미발행·비공개·삭제 대상은 모두 `404 {"code":"NOT_FOUND"}`. 존재 여부에 따른 초안 정보가 새지 않는다.
- 잘못된 파라미터는 `400 {"code":"INVALID_REQUEST"}`. 지원하지 않는 저장 snapshot 등 내부 오류는 일반화된 `503 {"code":"PUBLICATION_UNAVAILABLE"}`와 서버 로그로 분리한다.
- 5B-2A부터 `publishedAt` 등 LocalDateTime API 값은 `backoffice.time-zone`(기본 Asia/Seoul)으로 해석하여 `2026-09-27T17:46:00+09:00` 형식으로 반환한다. 기존 DB timestamp 값은 변환하지 않았다. 과거 값의 실제 시간대가 다르다면 원본 전환 전에 별도 검토가 필요하다.
- `menus.apiHref`는 **데이터 조회 주소**다. 방문자용 화면 경로가 아니다. 홈페이지가 pageId/slug/categoryId를 자기 라우터에 연결해야 한다. LINK 메뉴만 `url`을 직접 사용한다.
- 메뉴 순서·노출·외부 링크와 기존 category 이름은 현 구조에서 별도 발행 snapshot이 없는 즉시 설정이다. 페이지 메뉴의 이름과 slug는 페이지 발행본에서만 읽는다. 전체 메뉴 발행 정책은 추가하지 않았다.

## 사이트 구성과 메뉴 (V14, 2026-09-30)

- **구성 게시.** 관리자 전체 페이지 현황의 구성(영역·묶음·상하위·순서·메뉴 노출·표시명·콘텐츠 작업 연결)은 작업 중 구성이고, "구성 게시"를 해야 공개 API에 반영된다. 게시본은 영역을 pageId로만 참조하고 주소·페이지 제목은 저장하지 않는다.
- **fallback.** 구성을 한 번도 게시하지 않았으면 `/menus`는 위 기존 규칙(site_menus) 그대로다. 첫 구성 게시 뒤부터 `/menus`는 게시된 구성에서 만든다. 기존 CATEGORY 메뉴는 그때부터 나오지 않는다(카테고리 이관은 별도 단계).
- **읽을 때 해석.** PAGE 항목의 링크·기본 이름은 요청 시점의 페이지 발행본(`status=PUBLISHED`)으로 정한다. 페이지를 다시 발행하면 구성을 다시 게시하지 않아도 새 slug·제목이 나온다. 공개되지 않은 페이지는 공개된 하위가 있으면 `kind:'GROUP'`(링크 없는 이름), 없으면 빠진다. 묶음(GROUP)도 공개된 하위가 없으면 빠진다.
- **메뉴 트리.** `/menus` 항목은 `key`·`parentKey`로 트리를 이룬다(최대 3단계). 메뉴에서 숨긴 영역은 그 하위와 함께 메뉴에서 빠지지만, 게시된 페이지는 `/pages/by-slug`로 계속 열린다. `/structure`에는 남는다.
- `id`는 구성 항목이면 영역 id(=pageId), LINK면 메뉴 id다. 응답 안에서 고유한 값은 `key`다(`menu:<id>`, `area:<id>`).
- 공개 응답에는 작업 중 구성, 게시 이력, 게시자, 초안 제목을 내보내지 않는다. 단, 공개되지 않은 페이지가 이름만 나올 때는 그 페이지의 마지막 발행 제목(없으면 현재 제목)이나 게시된 메뉴 표시명을 쓴다.

## 발행본과 응답 경계

페이지 응답은 `{apiVersion:1,id,title,slug,publishedAt,blocks:[...]}`이다. 각 블록은 `{id,schemaVersion,type,variation,visible:true,data}`다. 저장용 평면 sections_json을 그대로 내보내지 않고 공개 표시 DTO로 투영한다. 원래 block ID와 순서는 유지하며 hidden 블록 자체를 제외한다.

`data`에는 heading/body/bodyHtml/image/link/label과 POSTS의 sourceMode/조회 결과가 있다. query 설정·비활성 설정·manual의 미발행/삭제 ID·템플릿 출처·작성자는 응답하지 않는다. 원본 snapshot의 설정은 DB에 그대로 남는다.

콘텐츠 응답의 title/content/richContent 렌더 결과는 `post_publications`에서 가져온다. 유형 이름·기수/주제 이름은 publication snapshot, 기수/주제 ID는 publication 연결 테이블을 사용한다. 코드 값은 기존 등록 사전의 식별 코드이며 이름을 기준으로 재매핑하지 않는다. 맛집 address는 `post_publication_restaurant_details`만 사용한다. 다른 유형의 restaurant는 null이다. 기존 categoryId는 발행 당시 값이며 현재 category 이름을 콘텐츠 snapshot처럼 반환하지 않는다.

상태 확인에만 원본 posts/site_pages를 조인한다. PUBLISHED가 아니거나 삭제된 콘텐츠는 발행본이 남아 있어도 공개하지 않는다. 반복 조회 한 응답의 일관성은 읽기 전용 REPEATABLE_READ 트랜잭션으로 유지한다.

## 렌더링 계약

공개 프런트엔드는 아직 없으므로 실제 화면은 구현하지 않았다. 향후 renderer는 다음 규칙을 따른다.

| type | 표시 | 등록 variation |
|---|---|---|
| HERO | heading, bodyHtml, link/label | default / centered |
| TEXT | heading, bodyHtml | default |
| IMAGE | heading, image.url/alt, bodyHtml | default |
| POSTS | heading, data.posts.items 순서대로, total | default |
| CTA | heading, bodyHtml, link/label | default |

- 블록 key/앵커는 `block.id`를 사용한다. 배열 위치를 ID로 재생성하지 않는다.
- 서버의 `PageComponentRegistry`가 type/version/variation을 검증한다. 임의 CSS/컴포넌트는 지원하지 않는다.
- 기존 관리자와 동일한 RichTextService가 텍스트를 이스케이프하고 허용된 태그만 조립한다. 공개 버전은 미디어 URL만 공개 endpoint로 바꾼다. 사용자 원문을 직접 innerHTML로 쓰지 않는다. `bodyHtml`은 이 서버가 만든 결과만 허용한다.
- centered 표현 규칙은 기존 `static/css/page-blocks.css`와 같고, 서식 클래스 `rt-*`는 `static/css/editor.css`에 있다. 향후 홈페이지 디자인 적용 시 이 규칙과 스크린샷을 대조해야 한다. API 테스트가 화면의 시각적 동일성을 증명하지는 않는다.
- URL은 API origin 기준 상대 경로다. 별도 서버에서는 미디어 URL 및 bodyHtml 내부 미디어 URL을 API origin으로 해석하거나 동일 출처 프록시를 제공해야 한다.
- `posts.items=[]`면 정상 0건이다. ‘조건에 맞는 발행 콘텐츠가 없습니다’ 또는 사이트의 빈 목록 표현을 사용한다.

## POSTS와 두 발행 경계

`PublishedPostQueryService.find`와 기존 CmsMapper publicPosts/publicPostCount를 그대로 재사용한다. 새 목록 필터 SQL을 만들지 않았다.

- category: 발행본 categoryId, 기존 최신순/6개. sourceMode 누락 시 기존 category 호환.
- query: 페이지 **발행본**의 type/cohort/topic/limit 설정. 콘텐츠 **발행본** 분류를 EXISTS 조건으로 조회한다. 서로 다른 기준 AND, 같은 기준 OR. 최신순(published_at DESC, id DESC), 1~20개. total은 limit 적용 전 일치 건수다.
- manual: 페이지 발행본 postIds의 순서. 현재 공개 가능한 발행 콘텐츠만 제외 없이 순서 복원(비공개/삭제/미발행은 결과에서 제외). 설정 postIds 자체는 변경하지 않는다. total은 공개 가능한 선택 개수다.
- 페이지 초안 설정을 바꿔도 공개 결과는 유지된다. 페이지 재발행 후 새 설정을 사용한다.
- 콘텐츠 초안 본문/분류/주소를 바꿔도 공개 결과는 유지된다. 콘텐츠 재발행 시 페이지를 재발행하지 않아도 해당 원본의 최신 발행본이 결과에 반영된다.
- REVIEW_LIFE/FAQ_LIFE는 서로 다른 ID 조건으로 조회하며 표시명 ‘생활’로 합치지 않는다.

## 파일 공개

미디어 bytes는 DB BLOB에 있다. URL에 OS 경로를 받지 않고 숫자 ID만 받으므로 디렉터리 탐색을 수행하지 않는다. 파일 이름도 basename만 반환한다. JPG/PNG만 inline이고 PDF/TXT/Office 등은 attachment다. HTML/SVG를 inline 허용하지 않는다.

공개 허용 근거는 현재 PUBLISHED 콘텐츠의 post_publication_media 또는 현재 PUBLISHED 페이지의 published=true page_media와 page_publications 존재다. 페이지 발행 시 visible 블록의 미디어만 page_media에 연결하는 기존 처리를 재사용한다. 초안, 숨김 블록, 템플릿, logoId 설정만 있는 파일은 공개 근거가 아니다. 템플릿을 페이지에 적용하고 발행해야 공개할 수 있다. 같은 파일이 다른 공개본에 참조되어 있으면 계속 공개 가능하다.

비공개 전환 후 다음 요청부터 404다. 이미 다운로드한 파일까지 회수할 수는 없다. 기존 미디어 이름/alt는 공용 메타데이터이며 별도 발행 버전이 없다. 바이트 교체 기능은 현재 없다. 운영 파일 검역·다운로드 기록·용량·CDN·로고 발행 정책은 5B에서 결정해야 한다.

## 관리자 미리보기와의 차이

관리자 미리보기는 작성 중/저장된 초안 블록을 볼 수 있다. 공개 API는 발행본 블록만 본다. POSTS 조회와 rich text HTML 조립기는 공통이다. 관리자 파일 URL은 세션 권한을 검사하고, 공개 파일 URL은 현재 발행 참조를 검사한다. 관리자 미리보기는 CTA를 편집 확인용으로 보여주며, 실제 홈페이지 링크 동작·반응형 렌더링 검증은 홈페이지가 생긴 뒤 수행한다.

## 연결 이후 실제 E2E 순서

1. 실제 홈페이지 코드·도메인·프록시·페이지 라우트를 확인한다.
2. 첫 대상은 page 65 / about을 권장한다. 지금은 단일 HERO여서 HOME의 콘텐츠 피드보다 영향 범위가 작다. 이는 API 검증 대상을 정한 것이며 실제 홈페이지 라우팅을 가정한 선택이 아니다.
3. 공개 페이지 GET → 관리자 초안 저장 → 공개 JSON 불변 / 관리자 미리보기 변경 → 기존 발행 처리 → 공개 JSON 변경을 확인한다.
4. 같은 흐름을 실제 홈페이지 renderer에서 재현한다. page 1의 POSTS와 V9 템플릿 복사 블록도 화면으로 대조한다.
5. CORS/프록시, 외부 링크와 관리자용 테스트 링크, 모바일 표시, 미디어 다운로드를 실제 주소에서 검수한다.

실제 홈페이지 브라우저 E2E는 현재 **미실행**이다. 관리자 브라우저와 익명 API 검증 결과는 `PHASE5A_RESULTS.md`에서 구분한다.
