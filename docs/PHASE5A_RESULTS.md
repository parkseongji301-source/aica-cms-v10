# 5A 결과 · 발행본 전용 공개 조회 계약

2026-09-27. **홈페이지 미구축을 사용자에게 확인했으므로 공개 API/연결 계약 범위까지만 완료했다. 실제 홈페이지 연결·화면 E2E는 완료로 간주하지 않는다.**

## 1. 실제 홈페이지와 연결 환경

현재 확인된 운영 대상은 Spring/Thymeleaf와 React 관리자다. 사용자가 홈페이지가 아직 없다고 명시했다. 공개 사이트의 소스 위치, 기술, 정적/API 표시 방식, 메뉴 라우터, 배포 방법, 동일/별도 서버와 테스트 주소는 미정이다. 다른 로컬 저장소를 실제 사이트로 추정해서 수정하지 않았다. 새 공개 HTML/React 홈페이지는 만들지 않았다.

5A는 4D 완료 DB의 정상 종료 백업으로 만든 V9 사본에서 수행했다. 검증 서버는 `http://127.0.0.1:8092`. 기존 4D 서버 8091도 유지한다. 완료 서버의 DB는 `.cache/phase5a-data/aica-phase5a-completed.mv.db`, 실행 파일은 `.cache/phase5a-test.jar`다. 원본 V3와 기존 작업 파일을 보존했다.

## 2. 공개 API / 권한

공개 계약은 `PUBLIC_API_V1.md`, 클라이언트 타입은 `public-api-v1.ts`에 정리했다.

| API | 기능 |
|---|---|
| GET `/api/public/v1/menus` | 표시 중 메뉴, 발행 페이지 제목/slug, category 연결 |
| GET `/api/public/v1/pages/{id}` | 페이지 발행 snapshot의 공개 블록 |
| GET `/api/public/v1/pages/by-slug/{slug}` | 발행 slug로 같은 페이지 조회 |
| GET `/api/public/v1/pages/{id}/blocks/{blockId}/posts` | 발행 POSTS 블록 조건의 실제 공개 콘텐츠 |
| GET `/api/public/v1/posts/{id}` | 콘텐츠 발행 본문·분류·주소·첨부 |
| GET `/api/public/v1/posts` | 기존 category 메뉴용 발행 목록·count·페이지네이션 |
| GET `/api/public/v1/media/{id}/file` | 현재 공개본에서 사용하는 파일 bytes |

익명 GET/HEAD 전용 보안 체인을 추가했다. 관리자 세션을 읽거나 생성하지 않고 변경 메서드는 차단한다. 관리 API·기존 UI의 세션/CSRF/역할 검사를 유지한다. 공개 DTO에 계정·초안 revision·작성자·소유자·활동 이력·비공개 설정을 넣지 않았다. 미발행 대상과 존재하지 않는 대상은 동일하게 404다. 외부 origin/CORS는 아직 열지 않았다.

## 3. 발행본만 읽는 근거와 블록 렌더링

- `Cms.publicPage/publicPageById`의 page_publications 문서를 파싱한다. site_pages는 공개 상태 확인에만 사용한다.
- `Cms.publicPost/publicPosts/publicPostCount`의 title/content/rich_content/category_id는 post_publications에서 가져온다. posts는 PUBLISHED 및 deleted_at 확인에 사용한다.
- `ClassificationService.published`와 `RestaurantDetailsService.published`를 재사용한다. 초안 분류 연결/주소를 공개 응답에 사용하지 않는다.
- `PublicDocuments`로 응답을 별도 구성한다. visible=false 블록은 제외하고, 나머지 block ID·schemaVersion·type·variation·순서를 보존한다. 설정 전체 JSON이나 사용 불가 manual ID를 그대로 공개하지 않는다.
- HERO/TEXT/IMAGE/POSTS/CTA를 지원하며 실제 등록 variation은 HERO default/centered, 나머지 default다. `PageComponentRegistry`와 기존 `RichTextService`를 사용한다.
- 기존 bodyHtml 생성기에서 미디어 URL만 공개 경로로 바꾼다. 실제 홈페이지 renderer는 미구현이므로 시각적 동일성은 후속 검증 대상이다. 관리자 centered 미리보기는 브라우저에서 확인했다.

## 4. POSTS 세 방식

공통 `PublishedPostQueryService.find`와 기존 Mapper를 재사용한다. category는 발행 categoryId 기반, query는 발행 분류 AND/OR 규칙과 최신순/개수 제한, manual은 페이지 발행 postIds 순서로 조회한다. 미발행/비공개/삭제 콘텐츠는 결과에서만 제외하고 저장 설정은 유지한다. 신규 필터 SQL을 복제하지 않았다.

최신 사본의 기존 검증 콘텐츠를 재사용한 실제 HTTP 결과:

| 블록 조건 | 최초 결과 ID / count |
|---|---|
| REVIEW + REVIEW_LIFE | 97 / 1 |
| REVIEW + REVIEW_PROJECT | 없음 / 0 |
| FAQ + FAQ_LIFE | 103 / 1 |
| RESTAURANT | 105 / 1 |
| manual `[105,33,103,97,999999]` | 105 → 103 → 97 / 3 |

97 초안의 생활→프로젝트 변경은 공개 결과 불변. 재발행 후 생활 0건, 프로젝트 97/1건으로 이동했다. 페이지 초안에서 query/limit/manual 순서를 바꿔도 공개 결과 불변. 페이지 재발행 후 manual 97→103→105와 새 query가 반영됐다. 33은 미발행, 999999는 사용 불가 상태로 설정에 남고 공개 결과에 없다. REVIEW_LIFE(1)와 FAQ_LIFE(7)는 섞이지 않았다.

## 5. 미디어와 미리보기

- 파일 저장은 DB BLOB이며 숫자 mediaId를 사용한다. 공개 경로가 파일 시스템 경로를 받지 않는다. basename만 반환하며 PNG/JPEG 외 문서는 다운로드로 제공한다.
- 공개 파일 허용은 실제 공개 발행본의 media 연결만 확인한다. 초안/숨김/템플릿/로고 설정만으로 허용하지 않는다. 비공개 전환 후 접근 차단과 경로 탐색 형태 요청 차단을 HTTP 테스트로 확인했다.
- 관리자 미리보기는 현재 초안의 블록, POSTS 결과는 발행 콘텐츠다. 공개 API는 페이지 블록도 발행본이다. HTML 생성기와 POSTS 조회기가 공통이며 파일 접근 기준만 다르다.

## 6. page 65 검증

page 65 / about은 단일 HERO라 HOME보다 영향 범위가 작아 첫 API 검증 대상으로 선택했다. 홈페이지의 미확정 라우팅을 가정한 선택은 아니다.

1. 기존 공개 문구 `테스트`, variation=default, block ID를 기록했다.
2. React 브라우저에서 블록 제목을 `[5A 검증] 발행 경계 확인`, variation을 centered로 변경했다.
3. 사이트 구조→사이트 관리 전환 후 입력 유지와 자동 초안 저장을 확인했다.
4. 관리자 미리보기는 새 문구/centered, 익명 공개 GET 응답은 기존 문구/default 그대로였다.
5. 기존 Thymeleaf ‘발행본 저장’ 버튼으로 발행했다.
6. 익명 공개 GET에 새 문구/centered가 반영됐으며 같은 block ID를 유지했다.
7. 서버 재시작 후 page 1/65·메뉴·콘텐츠 목록·후기/FAQ/맛집 등 7개 공개 응답이 재시작 전과 완전히 같았다.

브라우저의 공개 JSON 직접 열기는 내장 브라우저에서 `ERR_BLOCKED_BY_CLIENT`로 표시되지 않았다. 해당 응답은 쿠키 없는 실제 HTTP 클라이언트로 검증했다. 따라서 **관리자 브라우저 조작 → 공개 HTTP 응답** 검증이며 공개 홈페이지 화면 E2E가 아니다. 이를 위해 대체 홈페이지를 만들지 않았다.

## 7. 변경 코드와 재사용

| 영역 | 변경 |
|---|---|
| `publicapi/PublicDocuments.java` | 공개 전용 응답 모델 |
| `publicapi/PublicSiteService.java` | 발행본 투영, 공통 POSTS/분류/주소 조회 재사용 |
| `publicapi/PublicSiteApi.java` | 읽기 전용 공개 endpoints |
| `publicapi/PublicApiErrors.java` | 공개 오류에 내부 정보 노출 방지 |
| `security/SecurityConfiguration.java` | 별도 공개 stateless GET/HEAD 보안 체인 |
| `cms/RichTextService.java` | 같은 렌더러의 공개 미디어 URL 출력 경로 |
| `mapper/CmsMapper.xml` | 발행 페이지 메뉴 이름 조회와 공개 파일 참조 검사 |
| `PublicSiteIntegrationTest.java` | 공개 계약 통합 테스트 8개 |
| 문서 3개 및 DB_MIGRATION_RISKS.md | 계약·결과·복구/잔여 위험 기록 |

PostService/PageService의 저장·발행 정책, 기존 React/Thymeleaf 폼, block ID/템플릿/분류 스키마는 변경하지 않았다. migration을 추가하거나 V1~V9를 수정하지 않았다.

## 8. 테스트 / 데이터 보존

- 최종 Java 보고서 합계 **131 tests, failures 0, errors 0, skipped 0**. 새 공개 API 8개 포함.
- 기존 회귀에는 후기/FAQ/맛집, category/query/manual, 블록 탐색/ID/Variation, 템플릿, Thymeleaf, 계정/권한/CSRF, 재시작, eGov 호환 검증이 포함된다.
- 최초 전체 실행에서 두 migration 테스트가 사본 **파일명 접두어 안전 검사**로 중단됐다. 기존 검사/코드를 바꾸지 않고 새 사본 파일명을 해당 규칙에 맞춰 RestaurantMigrationTest/PageBlockMigrationTest 6개를 재실행해 통과했다. 나머지 통과한 테스트는 중복 실행하지 않았다.
- V3 분류 migration, V6 맛집, V7 블록 ID, V8 템플릿 실제 사본 테스트를 모두 실행했다. 적용 대상은 매번 별도 사본이다.
- 프런트엔드 기존 테스트 **39/39 통과**. React 코드는 이번에 바꾸지 않았다.
- 관리자 브라우저: page65 편집, 보기 전환, 초안 저장, centered 미리보기, 기존 화면 발행, 재조회 확인.
- 원본 SHA-256: `55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe` 유지.
- 완료 환경은 작업 전 V9 데이터를 그대로 가진 새 사본이다. 테이블 fingerprint·페이지/발행본 원문·Flyway history가 작업 전과 동일하다. 검증용 문구/블록/재발행은 `verified-v9.mv.db`에 분리 보관했다. 완료 환경에 실제 초기 데이터나 추가 IA를 넣지 않았다.

증거: `.cache/phase5a-final-audit.json`, `phase5a-runtime-checks.json`, `phase5a-draft-boundary.json`, `phase5a-browser-published.json`, `phase5a-restart-result.json`, `phase5a-evidence/`. 테스트 로그는 `phase5a-full-tests.log`, `phase5a-migration-recheck.log`, 개별 XML 보고서다.

## 9. 복구 기준점

`.cache/checkpoints/20260927-175559-phase5a/`:

- 작업 전: `baseline-v9.mv.db` + `baseline-4d-runtime.jar` + `source.zip`.
- 검증 중간: `verified-v9.mv.db` + `verified-5a-runtime.jar`.
- 완료: `completed-v9.mv.db` + `completed-5a-runtime.jar` + `completed-source.zip`.
- `RESTORE.md`와 SHA-256 manifest에 복구 순서를 기록했다. 실행 파일·DB를 서로 다른 시점에서 섞지 않는다. 원본 V3에 어떤 쌍도 덮어쓰지 않는다.

## 10. 5B 전에 남은 운영 문제

1. 홈페이지 개발/배포 위치, origin, 라우터, 실제 renderer 및 실제 화면 E2E는 미정이다. 공개 API 계약만 준비됐다.
2. 현재 메뉴·기존 category 이름·공용 미디어 이름/alt에는 발행 snapshot이 없다. 즉시 변경되는 설정의 범위와 로고 공개 방식을 확정해야 한다. 페이지/콘텐츠 초안의 본문·분류·주소 격리와 구분한다.
3. page 65에 기존 `http://127.0.0.1:8081/admin/pages/65/edit` 테스트 버튼 링크가 저장되어 있다. 실제 홈페이지 연결 전에 운영 링크로 검수해야 하며 이번에 임의 교체하지 않았다.
4. 공개 timestamp 시간대, 캐시/철회 정책, API 요청 제한·응답 크기·미디어 전송 비용, 공개 트래픽 부하/DB 연결 풀 검증은 아직 운영값을 정하지 않았다. 현재 no-store와 bounded POSTS 조회다.
5. 파일 DB 연결 수명 문제, H2 2.3.232/Flyway 지원 범위 경고는 별도 위험으로 유지한다. 이번 테스트 성공이 원본 migration 승인은 아니다.
6. 템플릿/블록의 미디어·분류·manual 참조 수명 점검, 운영 파일 검역·보관/삭제·백업 복원 훈련이 남아 있다.
7. SUPPORTER 발행·승인/반려·ADMIN 템플릿 권한 등 기존 미확정 정책을 바꾸지 않았다. 원본 적용은 5C에서 별도 승인 후 진행한다.
