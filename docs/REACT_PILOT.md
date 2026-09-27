# AICA React 1차 병행 검증

> 이 문서는 1차 시점의 검증 기록이다. 현재 메뉴·API·대상 범위는 [React 2차 구현 결과](REACT_PHASE2.md)를 참고한다. 2차에서는 같은 편집기를 모든 기존 페이지로 확장했다.

대상은 기존 `site_pages.id=65`(인사교 소개) 하나다. 기존 `/admin`은 Thymeleaf로 유지하고 `/admin-next`에서 React + TypeScript를 검증한다. 새 페이지, 콘텐츠 저장소, 분류 테이블, 전체 IA 초기 데이터는 만들지 않는다.

## 실행과 빌드

```powershell
.\scripts\build-admin-next.ps1
.\scripts\run-local.ps1
```

빌드 스크립트는 lockfile 기준으로 프런트엔드를 설치·검사·빌드하고 기존 Maven/eGov 검증을 실행한다. 서버 실행과 로컬 DB 변경은 수행하지 않는다. Node와 pnpm, JDK 17이 필요하다. `frontend/pnpm-lock.yaml`을 유지한다.

- React: `http://127.0.0.1:8081/admin-next`
- 직접 편집: `http://127.0.0.1:8081/admin-next/pages/65/edit`
- 기존 관리자: `http://127.0.0.1:8081/admin`
- 기존 로컬 로그인: `1234 / 1234`
- 생성한 정적 파일은 `src/main/resources/static/next-app/`에 위치하고 Git에서 제외한다. React를 포함한 패키지는 반드시 프런트엔드를 먼저 빌드한다.
- 기존 Maven 명령과 Spring 서비스는 유지한다. JSON API 통합 검사의 화면 진입 검증에도 프런트엔드 빌드 결과가 필요하다.

## 복구 기준점과 미완성 작업 보존

구현 시작 전 `.cache/checkpoints/20260926-213319-react-pilot/`에 다음을 보존했다.

- 수정·미추적 파일을 포함한 소스 130개: `source.zip`, 파일별 SHA256 `manifest.json`
- Git 전체 이력: `history.bundle`, HEAD/branch/status, `working-tree.patch`
- H2 자체 BACKUP으로 생성한 `database.zip`
- 복구 절차: `RESTORE.md`

DB 백업과 원본 파일의 해시, 압축한 소스의 해시를 확인했다. 기준점 위치는 `.cache/react-pilot-checkpoint.txt`에도 기록한다. 이 백업은 저장소 내부 로컬 자료이므로 다른 장치의 백업은 아니다.

진행 중이던 계층 탐색/V4 의존 소스 13개는 `workbench/navigation-draft/`의 `.txt` 파일로 원문을 보존했다. 활성 소스는 실제 V3 DB에서 실행되던 Thymeleaf 리소스와 연결 구조로 맞췄다. V4 실행 파일과 미완성 NavigationService/Configuration은 활성 소스에서 제외했으며, 보존본이나 전체 체크포인트에서 복구할 수 있다. 복구본의 V4를 그대로 실행하면 안 된다. FAQ는 후속 IA에서 `지원 전 Check!!` 하위이며 스키마 변경과 IA 데이터 등록은 별도 작업이다.

## 같은 대상과 편집기를 사용하는 근거

```text
사이트 관리 → 전체 페이지 현황 → 인사교 소개 ┐
                                             ├→ PageEditor(initial.page.id=65)
사이트 구조 → 기존 메뉴가 연결한 인사교 소개 ┘          ↓
                                             /api/admin/next/pages/65
                                                       ↓
                                        기존 PageService.saveDocument
                                                       ↓
                                            기존 site_pages.id=65
```

`frontend/src/main.tsx`의 두 버튼은 같은 `openPage()`를 호출한다. `PageEditor`는 한 번 마운트하고 목록으로 돌아가도 숨기기만 한다. 모드 변경은 사이드바와 URL의 `view`만 바꾸며 편집기 key·문서·저장 버전을 바꾸지 않는다. 화면의 `data-page-id`도 같은 ID다.

첫 방문 기본값은 사이트 관리다. 보기 선택은 계정별 sessionStorage에 보관하여 현재 탭 세션에서 기억한다. 명시적인 `?view=manage` 또는 `?view=structure`가 우선한다. 입력 내용은 브라우저 저장소에 쓰지 않는다. 새로고침·외부 이동은 미저장 입력이 있을 때 경고하며, 브라우저 종료 후 입력 복원은 이번 범위가 아니다.

구조 목록은 기존 `site_menus.kind=PAGE`, `target_id=65` 연결만 사용한다. 숨김 메뉴 또는 연결이 없는 대상도 편집 접근을 유지하되 '메뉴 밖'으로 표시한다. 메뉴를 생성하지 않는다. 다른 탭에서 변경한 메뉴 연결·노출은 화면 새로고침 시 bootstrap으로 반영한다. 섹션 이동용 임시 UUID는 React 내부 배열 식별에만 쓰며 저장하지 않는다. 섹션/블록 URL이나 IA 노드로 사용하지 않는다.

## 새 API

공통 접두사: `/api/admin/next`. 기존 세션과 CSRF를 사용한다. 별도 토큰 인증·CORS 허용·새 권한을 추가하지 않았다.

| 메서드·경로 | 역할 |
|---|---|
| GET `/bootstrap` | 로그인 사용자, CSRF, ID 65 문서, 기존 메뉴 연결, 분류·이미지 선택 자료 |
| GET `/pages/65` | 저장된 초안 조회 |
| PUT `/pages/65` | `{revision,title,sections}` 저장, 정규화된 문서·새 버전 반환 |
| GET `/pages/65/preview` | 현재 저장된 초안의 내부 미리보기 |
| POST `/pages/65/preview` | `{title,sections}`의 미저장 미리보기, DB 변경 없음 |

다른 페이지 ID는 404다. ID·slug·action을 입력에서 받아 생성·주소 변경·발행하는 경로가 없다. revision 필수, 오래된 버전은 409, 유효하지 않은 내용은 400, 비로그인은 401, 권한/CSRF 위반은 403 JSON이다. 역할뿐 아니라 계정 활성 여부·auth_version·필수 비밀번호 변경 상태를 기존 검사로 확인한다. 관리자 화면과 API는 ADMIN/SUPER_ADMIN만 접근한다. SUPPORTER의 기존 콘텐츠 권한은 변경하지 않는다.

이미지 업로드는 기존 `/admin/media/upload`, 파일 조회는 기존 `/admin/media/{id}/file`을 사용한다.

## 재사용한 기능

- `PageService.saveDocument`: 트랜잭션, 잠금, 저장 버전 확인, 기존 초안 갱신, 미디어 연결, 활동 이력.
- `PageService`의 섹션 검증을 새 읽기 전용 `previewSections`에서 재사용.
- `RichTextService`: Quill Delta 정규화, 미디어 참조 검사, HTML 안전 렌더링.
- 기존 HERO/TEXT/IMAGE/POSTS/CTA와 bodyDoc 형식, 이미지 저장소, 메뉴의 페이지명 연결.
- Spring Security 세션, CSRF, 기존 AccessPolicy/CurrentAccount/AccountSessionFilter.
- 기존 Quill 2.0.3 문서 형식과 공통 본문 스타일. React는 별도 Quill 컴포넌트를 사용하고 Thymeleaf 폼·숨김 필드·기존 폼 스크립트를 읽지 않는다.

저장은 기존 `save` 동작이며 발행본을 변경하지 않는다. 입력 후 1.8초 자동 임시저장을 기존 동작에 맞췄다. 저장 중 추가 입력은 덮어쓰지 않고 다음 저장으로 보낸다. 오류가 난 같은 입력을 자동으로 무한 재시도하지 않는다. 충돌·권한 오류는 자동저장을 중지하고 입력을 보존한다. 재로그인 시 CSRF를 다시 받은 뒤 수동 재시도할 수 있다.

## 변경 파일

| 구분 | 파일 |
|---|---|
| React 신규 | `frontend/package.json`, `pnpm-lock.yaml`, `tsconfig.json`, `vite.config.ts`, `index.html`, `src/{main.tsx,PageEditor.tsx,RichEditor.tsx,api.ts,types.ts,styles.css,vite-env.d.ts}` |
| API 신규 | `src/main/java/egovframework/backoffice/mvp/next/{NextAdminController,NextPageApi,NextApiErrors}.java` |
| 인증 오류 응답 | 신규 `security/ApiSecurityResponse.java`, 수정 `security/{SecurityConfiguration,AccountSessionFilter}.java` |
| 서비스 재사용 | `cms/PageService.java`의 읽기 전용 previewSections 추가 |
| 검증·실행·문서 | 신규 `NextAdminIntegrationTest.java`, `HttpBrowser.java` JSON 요청 지원, `scripts/build-admin-next.ps1`, `scripts/run-local.ps1` 안내 주소, `.gitignore`, `README.md`, 이 문서 |
| V3 유지·기존 작업 보존 | `workbench/navigation-draft/`, `cms/{CmsModels,CmsController,SiteService}.java`, `common/WebAdvice.java`, `mapper/CmsMapper.xml`, `templates/{fragments,cms/page-form,posts/form,posts/list}.html` |

기존 Git 작업 트리에는 이전 단계의 변경이 많다. 위 목록은 이번 단계의 범위이며 `git diff HEAD` 전체를 이번 React 변경으로 해석하지 않는다.

## 검증 결과 (2026-09-26)

- `pnpm run build`: TypeScript 검사와 Vite production 빌드 통과. `/css/`의 두 스타일은 Spring이 제공하는 기존 리소스이므로 Vite의 런타임 경로 안내가 표시된다.
- `mvn-local.ps1 -B -ntp -Pegov43-probe verify`: **35개 통과, 실패 0, 오류 0, 건너뜀 0**. 기존 28개 + `NextAdminIntegrationTest` 7개. 로그: `.cache/react-pilot-final-verify.log`.
- 실제 HTTP 검증: 두 진입 URL/ID 일치, JSON→기존 편집기→JSON 저장 호환, 발행본 보존, 페이지 수 불변, 미저장 미리보기 무쓰기, 리치 서식 저장과 안전한 렌더링, 버전 충돌/필수 버전/CSRF/유효성 검사, SUPPORTER 차단, 세션 무효화·필수 비밀번호 변경, 다른 ID 차단, V1–V3만 적용, 메뉴 노출/대상 변경 반영.
- 브라우저: 원본 DB 복사본을 `8082`에서 실행해 검증했다. 빈 제목으로 자동저장이 진행되지 않는 상태에서 본문을 입력하고 사이트 관리→목록→편집→사이트 구조를 왕복했다. 입력이 유지되고 DOM의 편집기는 1개, 경로의 `data-page-id`는 모두 65임을 확인했다.
- 브라우저: 저장 전 우측 미리보기 반영, 굵게/크기 변경→수동 임시저장→다시 조회, 자동저장→새로고침 후 본문·서식 유지, 이미지 업로드→저장→재조회→정상 이미지 로딩, PC/모바일 미리보기를 확인했다. 커서를 본문으로 옮겼을 때 툴바도 저장된 서식을 표시한다.
- 브라우저: 두 창으로 같은 페이지를 열어 한 창을 먼저 저장했다. 오래된 창은 충돌 오류와 저장 차단을 표시하고, 모드 전환·추가 입력 후에도 작성 내용을 유지했다.
- 최종 React 변경은 다시 타입 검사·빌드하고 실행 JAR에 포함했다. JAR 안에 현재 React 빌드가 포함되고 V4가 없는 것을 확인했다.
- 원본 앱을 다시 시작하기 직전까지 `.local-data/aica-local.mv.db`는 착수 전 DB 백업과 바이트 단위 SHA256이 같았다. 브라우저 테스트 내용은 복사 DB에만 기록했다.
- 원본 DB의 페이지 2개, 계정 2개, 글 9개, 메뉴 2개, 분류 3개를 확인했다. ID 65는 `인사교 소개 / about / revision 4`, 기존 테스트 문구와 bodyDoc를 그대로 유지했다.
- 최종 실행은 기존 `local` 프로필/원본 DB/8081이다. 서버 로그에서 스키마 버전 3과 migration 불필요를 확인했고, 브라우저에서도 ID 65의 원래 제목·본문을 읽기만 했다. 실행 로그는 `.cache/admin-next-local.log`, PID는 `.cache/admin-next-local.pid`다. 테스트 복사 DB의 8082 서버는 종료했다.

## 한계와 후속 결정

- React 검증 대상은 ID 65 하나. 페이지 신규 생성·발행·비공개·삭제는 기존 관리자에서만 가능하다.
- 미리보기는 내부 작성 내용 확인용이다. 외부 홈페이지의 공통 레이아웃·실제 글 목록을 재현하지 않는다.
- 기존 리치 본문은 읽고 보존하며 기본 서식을 편집한다. 본문 커서 위치의 이미지/파일/표 삽입 도구와 기존 전체 툴바의 동등성은 후속 범위다. 이번 화면의 새 이미지는 IMAGE 섹션으로 추가한다.
- 전체 IA, 섹션의 영구 ID, 복수 분류 DB, 콘텐츠 유형별 상세 필드는 미구현 상태로 둔다.
- SUPPORTER 발행 권한, 승인/반려, 신규 페이지 생성 범위, 정식 자동저장 정책은 결정하지 않았다.
- React 전환 시각 디자인과 메뉴 전체 확장은 이번 검증 결과를 확인한 뒤 진행한다.
