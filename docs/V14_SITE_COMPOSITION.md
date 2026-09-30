# V14 사이트 구성

전체 페이지 현황을 **사이트 구성을 관리하는 중앙 작업표**로 만든다. 관리자가 영역을 추가·제거하고 이름·상하위·순서·연결 콘텐츠·메뉴 노출을 정하면, 콘텐츠 작업의 항목과 작업 화면이 그 구성을 따르고, 실제 홈페이지의 메뉴·페이지 구성도 **구성 게시** 절차를 거쳐 같은 구성을 쓴다.

기준점은 태그 `v13-page-hierarchy-20260930`(`9dbabda`, 8095 = V13 RC1)이다. 전자정부 표준프레임워크 RTE 4.3.0, Spring Boot 3.4.5, Spring Framework 6.2.6, Spring Security 6.4.5, Tomcat 10.1.40, Java 17과 기존 Service·Mapper·MyBatis·Flyway·세션·CSRF·권한·게시·버전·휴지통 규칙을 유지한다. 페이지·메뉴·콘텐츠·분류를 한 테이블로 합치지 않고, 관리 화면 한곳에서 구성하되 기존 데이터와 편집 기능을 재사용한다.

## 결정 (2026-09-30)

1. **영역 = 페이지**가 기본 원칙이다.
2. 내부 PAGE 메뉴는 수동 관리 대신 사이트 구성에서 파생한다. 외부 링크는 별도로 유지하고, 우선 **최상위 마지막**에만 둔다.
3. 사이트 구조 변경에는 **구성 게시** 절차를 둔다.
4. 콘텐츠 작업 연결 단위는 **콘텐츠 유형**까지다. 주제는 `content_type_topics`를 읽어 하위 탐색으로 자동 구성한다.
5. 현재 IA를 위해 서비스 `MAX_DEPTH`를 **3**으로 올린다(DB 제약 아님).
6. V13 기준점 태그를 먼저 남긴다. legacy category 실제 이관은 사이트 구성·공개 구조가 준비된 뒤 별도 단계로 한다.
7. 메뉴에서 숨긴 게시 페이지는 **URL 직접 접근을 계속 허용**한다(탐색과 공개는 분리). 접근까지 막으려면 페이지를 비공개로 바꾼다.

## 1. 개념

| 개념 | 정의 | 저장 |
|---|---|---|
| 영역 | 사이트 구성의 한 항목. 페이지가 기본이다 | `site_pages` |
| 영역 종류 | `PAGE`: 실제 화면이 있는 영역(내용 편집·게시 대상). `GROUP`: 화면 없는 **구조 노드**(이름·위치·메뉴 노출만 있다) | `site_pages.area_kind` |
| 대표 작업 영역 | 콘텐츠 유형의 글을 작성·관리하는 자리. 그 유형의 글이 **다른 페이지의 POSTS 블록에 노출되는 것은 제한하지 않는다** | `site_pages.content_type_code` |
| 하위 탐색 | 대표 작업 영역 아래 자동으로 나오는 주제. 저장하지 않는다 | `content_type_topics` 읽기 |
| 메뉴 | 게시된 구성에서 파생(메뉴 노출 영역 + 외부 링크) | `menu_visible`, `menu_label`(선택). 외부 링크는 `site_menus`의 LINK |
| 구성 게시본 | 구조를 홈페이지에 반영하는 스냅샷. 영역을 **pageId로** 참조한다 | `site_structure_publications` |

실제 화면이 없는 단순 묶음은 빈 PAGE로 강제하지 않고 GROUP으로 둔다. IA의 상위 영역이 실제 landing page로 쓰이면 PAGE, 탐색 묶음일 뿐이면 GROUP이다.

`menu_label`: 지금 PAGE 메뉴의 표시명은 항상 페이지 제목이다(메뉴 SQL이 `p.title`을 쓴다). 그래서 IA의 "인사교 알아보기"처럼 제목과 다른 표시명을 둘 방법이 없다. 비워 두면 게시된 페이지 제목(GROUP은 묶음 이름)을 쓴다.

## 2. 규칙 (서비스 규칙, DB 제약 아님)

- 한 페이지에 연결 유형은 하나, **한 유형의 대표 작업 영역은 하나**. 현재 운영 규칙이며 DB에는 고정하지 않는다.
- 연결은 PAGE에만. 비활성 유형은 연결할 수 없다.
- 최대 깊이 3. 홈은 최상위 고정·하위 없음(V13 규칙 유지).
- 영역 종류는 만들 때 정하고 바꾸지 않는다(PAGE ↔ GROUP 전환 없음).
- 연결·메뉴 노출·표시명·GROUP 이름 변경은 SUPER_ADMIN(MANAGE_SITE)만 하고, 페이지 `revision`을 올리지 않는다(V13 위치 변경과 같다).
- 연결은 POSTS 블록과 별도다. 대표 작업 영역에 그 유형의 목록 블록이 없으면 **경고만** 한다.

### GROUP은 구조 노드로만

모든 경로에서 검증한다.

| 경로 | GROUP 처리 |
|---|---|
| 페이지 저장·게시(React API, `save-json`, 기존 화면 폼, 주소 변경, 버전 되돌리기) | 거부(서비스의 공통 저장 경로) |
| 공개 중단 | 거부 |
| 미리보기(React API) | 거부 |
| 페이지 템플릿 적용 | 적용 결과는 페이지 저장으로만 들어가므로 저장 거부로 막힌다 |
| 버전 이력 기준본(baseline) 생성 | 대상에서 제외 |
| 공개 page API(`/pages/{id}`, `/pages/by-slug`) | GROUP은 게시본이 생길 수 없고, 조회 SQL도 `PAGE`만 대상으로 한다 |
| 첫 화면(`homePageId`) 지정 | 거부 |
| 기존 메뉴(`site_menus`)의 PAGE 대상 | 거부 |
| 콘텐츠 작업 연결 | 거부 |
| 블록 탐색(`page-structure`) | 제외 |
| 삭제 | 페이지와 같은 규칙(하위·사용처·게시된 구성 참조가 있으면 거부) |

## 3. 구성 게시와 공개 반영 (2단계)

**게시본은 pageId를 참조하고 주소는 읽을 때 정한다.**

- 스냅샷 항목: `{areaId(pageId), kind, parentId, sortOrder, menuVisible, menuLabel, groupName, contentTypeCode}`. 공개 URL(slug)이나 페이지 제목 문자열은 저장하지 않는다.
- 공개 메뉴·구조를 만들 때 PAGE 항목의 href와 기본 표시명은 **현재 게시된 페이지**(`page_publications` + `status='PUBLISHED'`)의 slug·제목으로 정한다.
- 현재 게시되지 않은 PAGE는 링크를 만들지 않는다. 게시된 하위가 있으면 링크 없는 이름으로, 없으면 메뉴에서 뺀다. 그래서 페이지 게시와 구성 게시의 시점이 달라도 오래된 주소나 아직 공개되지 않은 주소로 메뉴가 깨지지 않는다.
- GROUP은 링크 없는 이름이다. 게시된 하위가 하나도 없으면 뺀다.
- 외부 링크(`site_menus` LINK)는 최상위 마지막에 붙인다.

**게시 절차**

- 관리자가 구성 게시를 누르면 검증을 거친다.
  - 거부: 순환, 깊이 초과, 비활성 유형, 없는 페이지
  - 경고: 메뉴 노출 0개, 게시되지 않은 PAGE, 빈 GROUP
- 화면이 본 구성과 다르면 409로 거부한다.
- 통과하면 스냅샷을 저장하고 활동 이력에 남긴다.
- 되돌리기는 "이전 구성 다시 게시"다. 이미 삭제된 페이지를 참조하면 그 항목을 빼고 알린다.
- 공개 API v1에 `GET /structure`를 추가하고, `GET /menus`의 출처를 스냅샷 + LINK로 바꾼다. `pages/by-slug`·`posts`는 그대로다. 페이지 공개 여부는 계속 페이지 게시가 정하고, 구성은 탐색만 정한다.

**삭제 보호.** 현재 게시된 구성 스냅샷이 참조하는 페이지는 영구 삭제하지 않는다. 구조에서 제거하거나 숨긴 뒤 구성을 다시 게시해 공개 구성의 참조가 사라져야 삭제할 수 있다. 참조 목록은 `site_structure_publication_pages(publication_id, page_id)`에 두고, 가장 최근 게시본만 검사한다. 이 표에는 `site_pages` 외래키를 두지 않는다. 지난 게시본이 삭제를 영원히 막지 않게 하기 위해서다.

**반영 흐름**

| 작업 | 콘텐츠 작업(관리자) | 홈페이지 |
|---|---|---|
| 영역 추가·이동·이름·표시명·연결·메뉴 노출 | 즉시 | 구성 게시 후 |
| 페이지 내용 편집 | — | 페이지 게시 후 |
| 글 작성 | 즉시 | 글 게시 후 |
| 연결 해제 | 사이드바에서 빠짐, 글은 그대로 | 구성 게시 후 |
| 메뉴 숨김 | 계속 보임 | 구성 게시 후(URL 접근은 유지) |
| 페이지 비공개 | 계속 보임 | 즉시(링크 없는 이름 또는 제외) |
| 페이지 영구 삭제 | 기존 규칙 + 게시된 구성 참조 보호. 글은 어떤 경우에도 함께 지우지 않는다 | — |

영역 제거(구조에서 빼기·숨김·연결 해제)와 콘텐츠 원본 삭제는 항상 별개다.

## 4. 단계

### 0단계: V13 기준점

`9dbabda`에 태그 `v13-page-hierarchy-20260930`을 붙이고 push한다. V13 문서의 "인사교 소개 아래 후기" 예시는 실제 IA와 다르다(후기는 인사교 소개와 같은 등급). 이 문서에서 바로잡는다.

### 1단계: schema + 구성 화면 + 콘텐츠 작업 데이터화 (관리자 안에서만 변화)

**DB(V14 migration, 기존 데이터 변경 없음)**

```sql
ALTER TABLE site_pages ADD COLUMN area_kind VARCHAR(16) NOT NULL DEFAULT 'PAGE';
ALTER TABLE site_pages ADD CONSTRAINT site_pages_area_kind CHECK(area_kind IN ('PAGE','GROUP'));
ALTER TABLE site_pages ADD COLUMN content_type_code VARCHAR(32);
ALTER TABLE site_pages ADD CONSTRAINT site_pages_content_type_fk FOREIGN KEY(content_type_code) REFERENCES content_types(code);
ALTER TABLE site_pages ADD COLUMN menu_visible BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE site_pages ADD COLUMN menu_label VARCHAR(80);
CREATE TABLE site_structure_publications (...);        -- 스냅샷(2단계부터 사용)
CREATE TABLE site_structure_publication_pages (...);   -- 게시본별 참조 pageId(외래키 없음)
```

- `menu_visible` 기본값이 FALSE인 이유: 기존 메뉴를 migration이 몰래 옮기지 않기 위해서다. 2단계에서 "현재 메뉴에서 가져오기"로 확인한 뒤 반영한다.
- `V14PromotionTool`로 정상 종료한 V13 DB의 사본에만 적용한다. 허용하는 변화는 위 네 열과 두 표뿐이다. 기존 열·데이터 지문이 그대로이고 새 값이 기본값인지 확인한다.
- receipt는 `MIGRATED_V14`, 서버 `CURRENT_VERSION`은 14다.

**서버**

- `MAX_DEPTH` 3
- `POST /api/admin/next/page-groups` `{name, parentId}`: 묶음 만들기(주소 자동, 내용·게시·버전 없음)
- `PUT /api/admin/next/pages/{id}/composition` `{contentTypeCode, menuVisible, menuLabel, name}`: 연결·메뉴 노출·표시명, GROUP 이름. `name`은 GROUP에만 쓴다
- 둘 다 MANAGE_SITE이고 활동 이력을 남긴다.
- 페이지 목록·bootstrap 행에 `areaKind`, `contentTypeCode`, `menuVisible`, `menuLabel` 추가
- 2절의 GROUP 규칙과 3절의 **삭제 보호**(게시본 참조 검사)를 1단계에 넣는다. 게시본이 아직 없으므로 지금 막히는 삭제는 없다.

**화면**

- 전체 페이지 현황 → **사이트 구성**
  - "묶음 추가", 영역 종류 표시, 3단계 들여쓰기
  - SUPER_ADMIN의 "구성" 대화상자: 콘텐츠 작업 연결(다른 영역이 대표인 유형은 이유와 함께 비활성), 메뉴 노출, 메뉴 표시명, 묶음 이름
  - 목록에 "콘텐츠 작업: 후기", "메뉴 노출", 표시명 표시
  - GROUP 줄에는 편집·구조 보기가 없다
- 콘텐츠 작업 사이드바
  - 코드에 고정된 후기·FAQ·근처 맛집 구조(시연용 IA 초안)를 지운다.
  - bootstrap의 대표 작업 영역으로 트리 위치대로 만들고, 상위 영역 이름을 묶음 제목으로, 유형의 주제를 하위 탐색으로 둔다.
  - 주소는 `?area=<페이지 id>&topic=<주제 코드>`이고, 옛 `reviewSection`·`faqSection`·`restaurantSection` 주소는 같은 유형의 대표 영역으로 이어 준다.
- 새 콘텐츠는 영역의 유형(+선택한 주제)이 미리 채워진다. FAQ·맛집 전용 입력은 유형 코드 기준이라 그대로 동작한다.
- 첫 화면·기존 메뉴의 페이지 선택지와 새 페이지의 상위 선택지는 GROUP 규칙을 따른다.

**바뀌지 않는 것**: 공개 API 응답, 홈페이지, `site_menus`와 메뉴 관리 화면, 글·분류·게시·버전·휴지통, 기존 Thymeleaf 화면. 공개 page 조회 SQL에 `area_kind='PAGE'` 조건을 더하지만, GROUP은 게시본이 생길 수 없으므로 어떤 응답도 달라지지 않는다.

**주의**: 운영 DB에는 아직 연결된 영역이 없다. 그래서 1단계 적용 뒤 콘텐츠 작업 사이드바의 후기·FAQ·맛집 항목은 영역을 연결할 때까지 보이지 않는다(글은 전체 콘텐츠에서 계속 관리). 실제 영역 구성은 4단계다.

### 2단계: 구성 게시 + 홈페이지 반영

3절 전체. 같은 schema이므로 JAR 교체 절차(`swap-v12-jar.ps1`)로 적용한다. 메뉴 관리 화면은 외부 링크 편집과 파생 메뉴 미리보기로 줄인다. 기존 PAGE·CATEGORY 행은 읽기 전용으로 두고, CATEGORY는 "이관 대상"으로 표시한다. "현재 메뉴에서 가져오기"로 기존 PAGE 메뉴의 노출·표시명·순서를 영역에 옮긴다.

### 3단계(별도): legacy category 이관

CATEGORY 메뉴 2개 변환, 페이지 65의 카테고리 방식 블록, GENERAL_NOTICE 등록.

### 4단계(별도, 운영 데이터): IA 확정 후 실제 영역 구성·연결·첫 구성 게시

사용자 승인이 필요하다.

## 5. V13 대비 변경

| 구분 | V13 | V14 |
|---|---|---|
| `site_pages` | `parent_id`, `sort_order` | + `area_kind`, `content_type_code`, `menu_visible`, `menu_label` |
| 새 표 | — | `site_structure_publications`, `site_structure_publication_pages` |
| 깊이 | 2 | 3(상수만 변경) |
| 영역 종류 | 페이지만 | PAGE / GROUP |
| 콘텐츠 작업 | 코드에 고정된 3개 섹션 | 대표 작업 영역 + 주제 자동 |
| 메뉴 | `site_menus` 수동(PAGE·CATEGORY·LINK), 즉시 반영 | 구성에서 파생 + LINK 유지, 구성 게시 후 반영(2단계) |
| 공개 API | 메뉴·페이지·글 | + `structure`, `menus` 출처 변경(2단계) |
| 페이지 삭제 | 하위·메뉴·홈 사용 중이면 거부 | + 게시된 구성이 참조하면 거부 |
| 그대로 | 위치 변경·순서 API, 홈 규칙, POSTS 블록, 글·분류·게시·버전·휴지통, 기존 Thymeleaf 화면, 페이지 주소 공개 규칙 | |

## 6. 현재 IA 초안을 이 틀에 대입한 예시 (확정 아님)

`docs/5D/5D-1/operating-data/IA_MAPPING.md` 기준이다. 실제 구성은 IA 확정 후 4단계에서 정한다.

| 영역 | 종류 | 대표 작업 영역 |
|---|---|---|
| HOME | PAGE(#1) | — |
| 인사교 알아보기 | PAGE(#65 재사용 후보) | — |
| 선배들의 SSUL | GROUP 또는 PAGE(landing 여부 결정) | — |
| └ 후기 | PAGE | REVIEW(생활·수업·프로젝트 자동) |
| └ 인터뷰 | PAGE | INTERVIEW |
| 인사교 Real Life | PAGE | — |
| └ 인사교 꿀팁 | GROUP | — |
| &nbsp;&nbsp;└ 근처 식당 | PAGE | RESTAURANT |
| 지원 전 Check!! | PAGE | — |
| └ FAQ | PAGE | FAQ(7개 자동) |
