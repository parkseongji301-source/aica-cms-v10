# React 관리자 전환

목표는 로그인 이후의 모든 관리자 업무를 React 안에서 끝내는 것이다. Thymeleaf를 모두 없애는 것이 목표는 아니다. 기준은 [V12 안정 기준점](V12_STABLE_BASELINE.md)이며 다음 조건을 지킨다.

- 전자정부 표준프레임워크 RTE 4.3.0, Spring Boot 3.4.5, Spring Framework 6.2.6, Spring Security 6.4.5, 내장 Tomcat 10.1.40, Java 17을 유지한다.
- 기존 Service·Mapper·MyBatis·Flyway·DB 구조, 세션·쿠키·CSRF, 권한·게시·버전·휴지통 규칙을 그대로 쓴다. 별도 백엔드나 저장소를 만들지 않는다.
- 로그인·강제 비밀번호 변경·오류 화면은 서버 렌더링으로 유지한다. 기존 Thymeleaf 파일은 전환 기간 동안 삭제하지 않는다.

## 단계

| 단계 | 내용 | 상태 |
|---|---|---|
| 1 | 즉시 가능한 공백: React 로그아웃, 현재 공개본 보기 | 완료(2026-09-30) |
| 2 | 기존 Service를 쓰는 JSON API: 콘텐츠 공개 중단, 페이지 게시·비공개·주소 변경, 페이지 삭제(JSON)·삭제 영향, 기존 카테고리, 계정 발급·역할·비활성·비밀번호 초기화 | 진행 중: 계정 관리만 남음 |
| 3 | 2단계 기능의 React 화면 | 예정 |
| 4 | React 안의 기존 화면 링크(`LegacyLink`, "기존 관리자") 제거, SUPPORTER/ADMIN/SUPER_ADMIN 전체 업무 검증 | 예정 |
| 5 | `/admin-next` → `/admin` 전환, 기존 Thymeleaf GET 화면을 `/admin/legacy`로 이동 | 4단계 PASS 후 |

2~4단계에는 DB migration이 없다. `/admin/media/{id}/file`, `/admin/media/upload`, `/admin/pages/save-json`은 React와 저장된 본문이 쓰므로 계속 유지한다.

## 1단계 결과

- **로그아웃**: React 상단바에 추가했다. 기존 Spring Security `POST /logout`을 세션 CSRF 토큰으로 호출하므로 세션 무효화와 `JSESSIONID` 삭제 방식은 같다. 방문한 편집기 중 하나라도 저장하지 않은 입력이나 진행 중인 저장·업로드가 있으면 로그아웃을 막고 이유를 보여 준다.
- **현재 공개본 보기**: 콘텐츠 편집기에서 새 창의 기존 화면 대신 React 대화상자로 연다. 기존 `publication` API는 분류·주소만 돌려주므로, 기존 공개본 화면과 같은 로직(`PostService.publication` + `RichTextService.html`, 일반 본문의 발행 첨부)을 쓰는 읽기 전용 `GET /api/admin/next/posts/{id}/publication/view`를 추가했다. 접근 권한은 기존 공개본 화면과 같고 DB에 쓰지 않는다.
- 검증: 프런트 테스트 64개, 타입 검사·빌드 통과. 서버 전체 테스트 179개 실행, 실패 0, 환경 조건 제외 6. 메모리 DB `design-preview`에서 저장하지 않은 입력이 있을 때 로그아웃 차단, 정상 로그아웃 후 API 401과 로그인 화면 안내, 게시 후 초안만 수정했을 때 공개본 대화상자에 초안이 나오지 않음을 확인했다. 8095 RC1 실행본과 DB는 바꾸지 않았다.

로그인 화면 스타일을 템플릿에서 직접 불러오는 작업은 5단계로 옮겼다. 로그인 화면은 비로그인 상태라 `/next-app/login-shell.css`를 불러올 수 없고, V11 UI release 도구(`scripts/v11/serve_ui.py`)가 이 파일을 쓰기 때문이다.

## 2단계 진행 (2026-09-30)

기존 Service와 기존 권한·CSRF·revision 규칙을 그대로 쓰는 JSON API를 추가했다. 화면은 3단계에서 연결한다.

| API | 기존 Service | 권한 |
|---|---|---|
| `POST /api/admin/next/posts/{id}/unpublish` `{revision}` | `PostService.unpublish` | PUBLISH_POSTS (ADMIN, SUPER_ADMIN) |
| `POST /api/admin/next/pages/{id}/publish` `{revision,title,slug?,sections}` | `PageService.saveDocument(..."publish")` | ADMIN 이상. 주소를 바꾸면 SUPER_ADMIN |
| `POST /api/admin/next/pages/{id}/unpublish` `{revision}` | `PageService.unpublish` | ADMIN 이상 |
| `GET/POST /api/admin/next/categories`, `PUT/DELETE /categories/{id}`, `PUT /categories/order`, `GET /categories/{id}/usage` | `SiteService.category/deleteCategory/reorder`, `UsageService` | SUPER_ADMIN |
| `GET /api/admin/next/pages/{id}/delete-impact`, `DELETE /api/admin/next/pages/{id}` `{revision,confirmed}` | `DeletionImpactService`, `PageService.delete` | DELETE_PERMANENT (SUPER_ADMIN) |

공개 중단은 기존과 같이 상태만 `PRIVATE`로 바꾸고 초안·이력·발행본 기록은 보존한다. 방문자 공개 여부는 상태로 결정된다. 사용 중인 카테고리와 메뉴·첫 화면에 연결된 페이지는 기존 규칙대로 삭제할 수 없다.

검증: 각 API마다 역할별 허용·차단, CSRF 누락, 오래된 revision, 입력 오류, 성공 후 DB 상태를 통합 테스트로 확인했다. 서버 전체 테스트 183개 실행, 실패 0, 환경 조건 제외 6. DB migration 없음.

남은 2단계 작업은 **계정 발급·역할 변경·비활성화·비밀번호 초기화 API**다. 임시 비밀번호를 한 번만 응답하고(no-store) 다시 조회할 수 없게 하는 처리와, 계정 변경 시 기존 세션 무효화(`account_guard`)를 기존 Thymeleaf 경로와 같게 검증해야 한다.
