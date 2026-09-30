# React 전환 5단계: `/admin` 전환

React 관리자 주소를 `/admin-next/**`에서 `/admin/**`으로 옮기고, 기존 Thymeleaf GET 화면을 `/admin/legacy/**`로 옮겼다. [전환 계획](REACT_ADMIN_TRANSITION.md)의 5단계다. 기준점은 태그 `react-admin-step4-20260930`(`fecf694`)이다.

**바꾸지 않은 것**: DB schema, Flyway migration(V1~V12), Service·Mapper 구조, 세션·쿠키·CSRF, 데이터 API(`/api/admin/next/**`)의 권한 판정, 모든 POST 주소, 전자정부 표준프레임워크 RTE 4.3.0·Spring Boot 3.4.5·Spring Framework 6.2.6·Spring Security 6.4.5·Tomcat 10.1.40·Java 17. Thymeleaf 파일은 삭제하지 않았다.

## 주소

| 구분 | 주소 | 처리 |
|---|---|---|
| React 화면 | `/admin`, `/admin/dashboard`, `/admin/trash`, `/admin/posts`, `/admin/posts/{id}/edit`, `/admin/media`, `/admin/pages`, `/admin/pages/{id}/edit`, `/admin/menus`, `/admin/design/{style,components,templates,writing-templates}`, `/admin/accounts`, `/admin/roles`, `/admin/activity`, `/admin/settings/{basic,links,system}` | `NextAdminController`가 `/next-app/index.html`을 돌려준다 |
| 기존 화면(비교·복구용) | `/admin/legacy`, `/admin/legacy/posts/**`, `/pages/**`, `/media`, `/categories`, `/menus`, `/design/{style,components}`, `/settings/{basic,links,system}`, `/accounts/**`, `/roles`, `/activity`, `/{posts,pages,media}/{id}/delete-confirm` | 기존 컨트롤러의 GET 매핑만 옮겼다 |
| 옛 React 주소 | `/admin-next`, `/admin-next/**` | `/admin/**`으로 302. 뒷부분 경로와 쿼리를 그대로 붙인다 |
| React에 짝이 없는 옛 GET | `/admin/posts/new` → `/admin/posts`, `/admin/posts/{id}`·`/{id}/publication` → `/admin/posts/{id}/edit`, `/admin/pages/new` → `/admin/pages`, `/admin/pages/{id}/preview` → `/admin/pages/{id}/edit`, `/admin/accounts/new`·`/issued`·`/{id}/role` → `/admin/accounts`, `/admin/categories` → `/admin/legacy/categories` | 302(`AdminRouteRedirectController`) |
| 그대로 | 모든 POST, `GET /admin/media/{id}/file`, `/login`, `/logout`, `/account/password`, `/error`, `/css/**`, `/js/**`, `/next-app/**`, `/api/admin/next/**`, `/api/public/v1/**` | 변경 없음 |

- 301이 아니라 302를 쓴다. 301은 브라우저가 기억하므로 4단계 JAR로 되돌렸을 때 옛 주소가 계속 새 주소로 이동한다.
- 같은 주소를 GET과 POST로 나눠 쓴다. 예: `/admin/posts/{id}/edit`의 GET은 React 화면, POST는 기존 폼 저장이다.
- 저장된 본문 HTML 안에 `/admin/media/{id}/file`이 들어 있으므로 이 주소는 영구히 유지한다.
- 로그인 성공 뒤 이동(`/admin`)은 코드 변경 없이 React가 된다. 비밀번호 변경이 필요한 계정은 계속 `/account/password`로 간다.
- 서버가 만드는 사용처 링크는 React 주소다. `/admin-next/...`로 만들던 곳(`DeletionImpactService`, `TemplateReferences`, `UsageService`, `VersionMediaReferences`, `CmsMapper.xml`의 휴지통 링크)은 `/admin/...`으로 바꿨다. SQL 문자열만 바뀌었고 스키마는 그대로다.

## 권한

| 요청 | 규칙 |
|---|---|
| React 화면 GET | 로그인한 모든 계정. 화면 파일은 모든 역할에 같은 정적 HTML이며 계정·역할·CSRF 토큰·권한별 데이터를 담지 않는다 |
| 데이터 API | 변경 없음. 로그인 안 함 JSON 401 `AUTH_REQUIRED`, 권한 없음 JSON 403 `FORBIDDEN_OR_CSRF` |
| 기존 화면 GET | 옮기기 전과 같은 권한을 주소만 옮겨 적용한다. 메뉴·카테고리·디자인·설정·새 페이지: MANAGE_SITE. 페이지: ALL_POSTS. 계정·역할·활동 이력: MANAGE_ACCOUNTS. 글·페이지 삭제 확인: DELETE_PERMANENT. 대시보드·글·미디어: 로그인 |
| 옛 주소 redirect GET | 로그인. 이동한 곳의 규칙이 다시 적용되므로 redirect가 권한을 넓히지 않는다 |
| POST | 변경 없음 |
| 목록 밖 주소 | `denyAll` 유지 |

권한 없는 React 화면을 열면 React가 "접근 권한이 없습니다."와 이유, 작업 영역 홈으로 가는 버튼을 보여 준다(서버 403 화면 대신). 그 화면이 부르는 API는 여전히 403이다. 권한 없는 기존 화면은 서버 403 안내 화면이다.

## 기존 화면

- 상단에 "기존 화면(비교·복구용) · 새 관리센터로 이동" 표시가 붙는다(`WebAdvice`의 `legacyScreen`).
- 기존 화면끼리는 `/admin/legacy/...`로 이동한다. 폼을 저장한 뒤에도 기존 화면에 머문다(`redirect:`와 `ListLocation`이 legacy 주소를 쓴다). 폼 제출 주소는 그대로다.
- 비밀번호 변경·오류 화면은 React에서 들어오므로 상단 메뉴가 React 주소를 가리키고 기존 화면 표시와 카테고리 메뉴가 없다(`adminBase`).
- React 코드에는 기존 화면 링크가 없다. 프런트 테스트가 `/admin/legacy`, `/admin-next`, 허용 목록 밖의 `/admin...` 문자열을 막는다. 허용: 앱 기준 주소 `/admin`(`adminBase.ts`), 파일 `/admin/media/`, 업로드 `/admin/media/upload`, 페이지 생성 `/admin/pages/save-json`.

**제거 조건**: 기간이 아니라 안정화 조건으로 정한다. 5단계 완료 후 RC2 실제 적용, 3개 역할 전체 업무 검증, 정상 종료·재시작, rollback 검증, React 안의 legacy 링크 0개 확인을 모두 마친 뒤 별도 정리 단계에서 제거한다.

## 검증 (2026-09-30)

- 서버 전체 테스트 192개 실행, 실패 0, 환경 조건 제외 6. 기존 테스트 17개 파일의 주소를 옮겼다. 기존 화면 GET과 POST 뒤 이동 주소는 `/admin/legacy`, `/admin-next`는 `/admin`, POST 주소는 그대로다. 업무 규칙 검사는 모두 그대로 살아 있다.
- 권한 없는 React 화면의 서버 403을 기대하던 4곳은 조건 1에 따라 200으로 바꾸고, 같은 테스트의 API 403 검사는 유지했다.
- 새 `RouteTransitionIntegrationTest`(6개): 3개 역할이 React 주소 20개에서 같은 정적 화면을 받고 계정 정보가 없음, 비로그인은 로그인 화면으로, 비밀번호 변경 필요 계정은 `/account/password`로, `/admin-next` 302와 쿼리 보존, 옛 GET 302와 redirect가 권한을 넓히지 않음, 기존 화면 20개 주소 × 3개 역할 권한표, 기존 화면 링크와 표시, POST 주소·CSRF·권한 불변, `/admin/media/{id}/file` 불변, **역할별 데이터 API 권한표 18개 × 3개 역할과 비로그인 401 고정**.
- 프런트 테스트 70개, 타입 검사·빌드 통과.
- 메모리 DB `design-preview`(8081) 브라우저 확인: SUPER_ADMIN과 새로 발급한 ADMIN·SUPPORTER(첫 로그인 비밀번호 변경 강제 통과)로 로그인 → `/admin`. React 주소 16개 모두 200이고 세 역할의 화면 파일이 같음. API·기존 화면 권한표가 역할별 기대값과 같음. `/admin-next/pages?view=structure` → `/admin/pages?view=structure`. SUPPORTER로 `/admin/accounts`를 열면 "접근 권한이 없습니다."와 "사이트 관리 홈으로" 버튼, 누르면 대시보드. React 화면의 기존 화면 링크 0개.

## RC2 적용 절차 (DB 공유, receipt는 JAR별)

같은 V12 DB를 RC1과 RC2가 함께 쓰고 receipt는 JAR마다 따로 둔다. 필수 조건:

- **두 runtime 동시 실행 금지**: H2 파일 잠금에 더해, 시작 전에 같은 DB를 가리키는 다른 runtime이 실행 중이 아닌지 검사한다.
- **V1~V12 checksum 동일**: RC2 JAR의 migration 목록과 checksum이 RC1 receipt와 하나라도 다르면 중단한다.
- **schema·데이터 호환성**: RC2 발급 전에 cold 상태의 DB를 읽기 전용으로 검사한다(Flyway history, 테이블·열 구성, 행 수·지문이 마지막 RC1 정상 종료 기록과 같음).

도구와 절차는 [V12 JAR 교체](V12_JAR_SWAP.md)에 있다. rollback은 RC2 정상 종료 → RC1 선택 → START이며, RC1 receipt도 같은 DB에 유효하므로 RC2에서 작성한 내용을 잃지 않는다.

| 항목 | 상태 |
|---|---|
| RC2 빌드 | 완료. `081a35e`를 깨끗한 worktree에서 Vite 빌드 후 `mvnw -o package`(테스트 192개, 실패 0, 제외 6). JAR `90718f0ab1783bdea964b70d5ecbb83c9ee7a35166c60b731260e95adfb848a6` |
| RC2 실행본 폴더 | 준비 완료. `.cache/v12-release/V12-RC2-20260930/runtime`(JAR, RC1과 같은 정상 종료 도구와 로그인 스타일 오버레이 `e94da365…`, `database` = RC1 DB). receipt는 아직 없다 |
| 리허설 | PASS(DB 사본, 8096/8097). 결과는 V12_JAR_SWAP.md |
| 8095 실제 적용 | 대기. RC1 정상 종료가 필요해 사용자 확인 후 진행한다 |

## 남은 것

- 로그인 화면 스타일 오버레이(`css/flow.css` = 원본 + `login-shell.css`)는 그대로 둔다. RC2도 RC1과 같은 방식으로 제공한다. 로그인 화면을 바꾸는 작업은 이 단계의 설계에 넣지 않았다.
- `README.md`의 8095 접속 링크는 RC2 적용 뒤 `/admin/...`으로 바꾼다. 지금 8095(RC1)의 `/admin`은 기존 대시보드다.
- 과거 단계 문서와 보조 스크립트의 `/admin-next` 주소는 기록이므로 고치지 않는다. 옛 주소는 302로 계속 열린다.
