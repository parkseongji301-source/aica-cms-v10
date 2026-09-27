# AICA React 3A 구현 결과

2026-09-26. 기존 콘텐츠 원본을 조회하고 초안을 편집하는 React 공통 `ContentEditor`를 추가했다. `/admin`의 Thymeleaf 화면과 Spring 업무 서비스는 계속 사용한다. DB 구조, `category_id`, 콘텐츠 유형, IA 초기 데이터, 승인·반려 및 발행 정책은 변경하지 않았다. 실행 마이그레이션은 V1–V3이며 V4는 적용하지 않았다.

## 접속과 범위

- 콘텐츠 목록: `http://127.0.0.1:8081/admin-next/posts?view=manage`
- 기존 콘텐츠 예시: `http://127.0.0.1:8081/admin-next/posts/33/edit?view=manage`
- 동일 콘텐츠의 구조 보기: `http://127.0.0.1:8081/admin-next/posts/33/edit?view=structure`
- 로그인 경로와 로그인 후 기존 `/admin`으로 이동하는 동작은 유지한다. 로그인 후 위 React 주소를 열면 된다.
- **기존 콘텐츠 편집만 React로 연결했다.** 새 콘텐츠 작성, 발행·비공개·삭제, 분류 자체의 관리는 기존 화면을 사용한다. 발행·상세 관리 링크는 동일 ID의 기존 편집기를 새 탭으로 연다.

## 1. 기존 로직 재사용

| 영역 | 재사용한 코드 / 동작 |
| --- | --- |
| 콘텐츠 조회·저장 | `PostService.get`, `attachments`, `save` 및 기존 MyBatis 매퍼. React용 DTO만 별도로 제공 |
| 초안·발행본 | 기존 revision 검사, 초안 저장, `post_publications` / `post_publication_media` 보존, 활동 이력 기록 |
| 검증 | `InputRules`, `CmsRules`, `RichTextService.validate`. 제목, 분류 존재 여부, 본문 길이, 허용 서식·링크·미디어 수 검사 |
| 미디어 | 2차 `/api/admin/next/media`, 기존 `MediaService`와 `/admin/media/{id}/file`. 소유권, 확장자·크기·형식, 사용 중 삭제 제한 유지 |
| 권한 | 기존 세션, `CurrentAccount`, `AccessPolicy.requirePostAccess`, CSRF, 계정 상태·인증 버전 검사 |
| 미리보기 | 기존 `RichTextService.html`. Thymeleaf 미리보기의 검증·렌더링을 `PostService.preview`로 모아 양쪽에서 호출 |

`PostService`에는 기존 일반 본문과 별도 첨부도 보존할 수 있는 `saveDocument` 오버로드와 공통 `preview`만 추가했다. 기존 저장 처리와 발행 정책은 그대로다. SUPPORTER는 본인 콘텐츠만 편집하고, React API에 발행 기능을 추가하지 않았다. 기존 SUPPORTER 발행 권한은 이번 단계에서 변경하지 않았다.

## 2. 새 API

접두사: `/api/admin/next/posts`. 모든 응답은 `Cache-Control: no-store`다.

| 메서드 | 경로 | 처리 |
| --- | --- | --- |
| GET | `/{id}` | 기존 원본과 현재 분류·첨부·작성자·revision·발행 상태 조회 |
| PUT | `/{id}` | 기존 ID의 초안 저장. `revision` 필수. `PostService.saveDocument(..., "save", ...)` 호출 |
| GET | `/{id}/preview` | 저장된 초안 미리보기 |
| POST | `/{id}/preview` | 입력 중인 내용 미리보기. 저장하지 않음 |

조회 응답 `PostDocument`: `id`, `title`, `content`, `richContent`, `categoryId`, `revision`, `status`, `publishedRevision`, `pending`, `authorId`, `authorName`, `createdAt`, `updatedAt`, `mediaIds`, `attachments`.

저장 입력: `revision`, `title`, `content`, `richContent`, `categoryId`, `mediaIds`. 미리보기 입력: `title`, `content`, `richContent`, `mediaIds`. 클라이언트가 `id`, `authorId`, `action=publish`를 추가해도 저장 대상을 바꾸거나 발행할 수 없다. 경로 ID와 서버의 `save` 동작을 사용한다.

없는 ID는 404, 본인 범위를 벗어난 접근은 403, 오래된 revision은 409, 입력 오류는 400이다. 로그인 만료·계정 변경은 기존 JSON 인증 오류를 반환한다. 신규 콘텐츠 생성 API는 추가하지 않았다.

## 3. React ContentEditor 구성

- `ContentPanel`: ID별 조회, 로딩·오류·재시도. 권한 거부 시 편집기를 만들지 않는다.
- `ContentEditor`: 제목, 현재 단일 분류, 본문, 초안 저장, 재조회, 발행 상태, 실시간 미리보기, 기존 발행본 확인.
- `RichEditor`: 1차부터 쓰던 Quill 편집기를 재사용. 문단·글꼴·크기·색·정렬과 굵게·기울임·목록 등 기존 허용 서식을 편집한다.
- `RichContentTools`: 같은 편집기에 사진·파일 업로드, 보관함, 이미지 크기·정렬·대체 텍스트·캡션, 첨부 표시 이름, 본문 내 이동·제거, 링크, 표, 구분선 도구를 연결했다. 기존 `aicaImage`, `aicaFile`, `aicaTable`, `divider` 저장 형식을 사용한다.
- `contentDocument`: 서식 문서가 없는 기존 일반 본문·별도 첨부를 편집기 메모리에서 기존 Delta 형식으로 표현한다. 조회만으로 DB를 쓰지 않는다. 실제로 편집 후 저장할 때 기존 `rich_content`에 반영한다. 기존 Thymeleaf 편집기의 전환 방식과 같다.

기존 1.8초 자동 임시저장 간격을 유지하고 수동 초안 저장과 Ctrl/Cmd+S도 제공한다. 저장 중 입력은 응답으로 덮어쓰지 않는다. 업로드 중에는 저장을 보류한다. 401/403/404/409 오류는 자동 저장을 중지하며 입력을 보존한다. 미저장 입력을 재조회로 버릴 때와 브라우저를 떠날 때 확인한다.

보기 전환과 내부 목록 이동 시 ID별 편집기를 메모리에 유지한다. 브라우저 새로고침 이후의 미저장 입력 복구, 오프라인 저장, 영구적인 브라우저 초안 저장은 이번 범위가 아니다.

## 4. 같은 ID와 편집기

```text
사이트 관리 → 콘텐츠 목록 → 콘텐츠 #33
사이트 구조 → 기존 분류/메뉴의 콘텐츠 목록 → 콘텐츠 #33
  → postEditorPath(33)
  → /admin-next/posts/33/edit?view=manage 또는 structure
  → ContentPanel(id=33) → ContentEditor(initial.id=33)
  → /api/admin/next/posts/33 → PostService → posts.id=33
```

`view`는 사이드바 탐색 방식만 결정한다. `main.tsx`는 query가 아닌 `/posts/33/edit`를 편집기 키로 사용한다. 구조 모드에서 분류에 연결된 콘텐츠 목록과 관리 모드의 콘텐츠 목록은 동일한 `PostTable` / `postEditorPath`를 사용한다. 분류별 저장소·편집기를 생성하지 않았다. 콘텐츠 분류 변경 후 구조의 선택 표시도 저장된 분류를 따른다.

현재 DB의 홈페이지 메뉴에는 콘텐츠 분류 연결이 없으므로 구조 보기의 ‘콘텐츠 분류’ 아래 실제 분류를 사용한다. 방문자 메뉴와 동일한 의미로 표시하지 않으며 임의의 IA 메뉴·페이지를 생성하지 않는다. 기존 페이지 POSTS 블록의 연결된 콘텐츠 목록도 같은 경로를 사용한다.

## 5. 초안·발행본 처리

- DRAFT 초안은 저장 후에도 DRAFT다. PRIVATE도 그대로 유지한다.
- PUBLISHED 콘텐츠의 수정은 초안 revision만 갱신한다. 기존 발행 제목·본문·분류·첨부와 publishedRevision은 보존한다.
- 미리보기는 저장 전 입력을 렌더링하며 DB와 발행본을 변경하지 않는다.
- 기존 화면에서 먼저 저장한 뒤 React의 오래된 revision으로 저장하면 409가 발생한다. 기존 원본과 React 입력 모두 보존하며 재조회로 해결한다.
- 발행 작업은 기존 UI에서 수행한다. 자동 발행, 승인·반려 또는 새로운 역할은 추가하지 않았다.

## 6. 검증 결과

### 자동 검증

`pnpm run build`: TypeScript 검사 및 Vite 빌드 성공.

`.\scripts\mvn-local.ps1 -B -ntp -Pegov43-probe verify`: **49개, 실패 0, 오류 0, 건너뜀 0**. 로그는 `.cache/react-phase3a-verify.log`.

기존 42개 회귀 검증과 새 `NextPostIntegrationTest` 7개를 실행했다. 새 테스트는 독립된 H2 메모리 DB와 실제 인증 HTTP 요청을 사용한다.

1. 같은 기존 ID 조회, 일반 본문·첨부 유지, 양쪽 경로, 조회 시 생성·저장 없음.
2. 일반 본문 초안 저장·재조회, 분류·첨부 보존, 기존 발행본 유지, 클라이언트의 발행·ID·작성자 변경 시도 무시.
3. 서식·이미지·파일·표·구분선 왕복 저장. 초안에서 첨부를 빼도 발행 첨부 유지.
4. Thymeleaf와 같은 미리보기 HTML, 일반 본문 이스케이프, 미리보기 시 DB 쓰기 없음.
5. revision 누락·충돌, 잘못된 분류·제목·링크, 없는 ID 저장 거부.
6. ADMIN/SUPPORTER 범위, 타인 미디어·콘텐츠 거부, CSRF, 미인증, 인증 버전 만료.
7. DRAFT/PRIVATE 및 발행 revision 유지, 미분류, V1–V3 유지, 기존 작성 UI 보존.

최종 서식 선택 옵션을 포함해 프런트엔드를 다시 빌드하고 JAR을 패키징했다. JAR의 index와 실제 React 빌드가 일치하고 V1–V3만 포함되는 것을 확인했다. 빌드는 성공하나 JS 번들이 약 502KB여서 Vite의 500KB 경고가 있다. 성능 개선용 코드 분할은 후속 항목이다. 공용 `/css/editor.css`, `/css/quill.core.css`는 Spring이 제공한다.

### 브라우저 검증

**원본 DB를 복사한 8082 검증 서버**에서 기존 콘텐츠 ID 33을 사용했다. 테스트를 위해 원본 콘텐츠를 수정하지 않았다.

- 관리 목록 → ID 33 편집, 구조의 분류 목록 → 같은 ID 33 편집. 실제 ContentEditor DOM 1개 확인.
- 저장할 수 없는 빈 제목과 수정 중인 본문을 유지한 채 보기·목록 전환 후 복귀: 입력 유지.
- 본문 첫 문단과 두 번째 문단 사이 사진·문서 삽입, 이미지 50%·오른쪽 정렬·설명·캡션, 첨부 표시 이름 변경.
- 분류·제목·본문 수정 및 수동/자동 초안 저장, 재조회 후 같은 값·미디어 위치 유지.
- 굵게·명조·20 크기, 2×2 표 추가·기존 표 수정, 링크 삽입 및 실시간 미리보기.
- 기존 Thymeleaf 편집기에서 동일 ID의 제목·분류·본문·사진·첨부·표 확인.
- 복사본에서 기존 UI로 발행한 뒤 React 초안을 변경: 기존 발행 화면에는 발행 당시 제목·본문·미디어 유지.
- 기존 UI에서 먼저 저장하고 React에서 오래된 내용 저장: 충돌 안내, 초안 저장 차단, 입력 유지. 구조 전환 후에도 동일 입력 유지.
- 기존 페이지 ID 65의 공통 RichEditor에서 편집·양쪽 정렬·저장·재조회·보기 전환·미리보기 회귀 확인.

파일 선택 업로드를 브라우저에서 검증했다. 드래그/클립보드 파일 입력, 모든 서식 조합, 모든 브라우저/모바일 기기의 실사용 검증까지 수행한 것은 아니다. 역할별 권한·세션 만료는 자동 HTTP 테스트로 검증했다.

최종 JAR로 원본 8081 서버를 재기동했다. 기존 로그인 후 원래 콘텐츠 목록 9개와 ID 33의 원래 제목·본문·분류·미리보기, 두 보기의 동일 편집기를 조회로 확인했다. 이 확인에서는 콘텐츠를 저장하거나 수정하지 않았다.

## 7. 보존 및 변경 파일

작업 전 복구 기준점: `.cache/checkpoints/20260926-231012-react-phase3a/`.

- 기존 수정·미추적 소스 167개를 `source.zip`과 SHA256 manifest로 보존.
- `history.bundle`, Git 상태·차이·기준 커밋, 기존 2차 실행 JAR, H2 `database.zip`, 복구 안내 보존.
- 원본 DB SHA256: `962cc04460bb3459d0455ff8ce93aec74f0d3dae8128ddd1af5d9c3e1854a075`.
- 브라우저 검증 후 원본 서버 재기동 전에도 이 해시와 일치했다. 테스트 DB는 `.cache/react-phase3a-data/aica-phase3a.mv.db`로 분리했다.
- 원본과 테스트 DB를 읽기 전용으로 대조했다. 양쪽 모두 콘텐츠 9개·페이지 2개·분류 3개·메뉴 2개·계정 2개로, 콘텐츠·페이지 중복 생성이 없었다. 원본 ID 33은 DRAFT/revision 0 그대로이며, 테스트 복사본만 초안 revision 10 / 발행 revision 5로 나뉘었다.
- 작업 전 manifest와 비교해 기존 파일 삭제가 없으며, DB 마이그레이션·매퍼·기존 Thymeleaf 템플릿·보존 중인 workbench는 변경하지 않았다.

| 구분 | 파일 |
| --- | --- |
| React 추가 | `frontend/src/ContentEditor.tsx`, `ContentPanel.tsx`, `contentDocument.ts`, `RichContentTools.tsx`, `content-editor.css` |
| React 연결 | `frontend/src/main.tsx`, `navigation.ts`, `ReadPanels.tsx`, `api.ts`, `types.ts`, `RichEditor.tsx` |
| API 추가 | `src/main/java/egovframework/backoffice/mvp/next/NextPostApi.java` |
| 경로·오류·접근 설정 | 같은 패키지의 `NextAdminController.java`, `NextApiErrors.java`, `mvp/security/SecurityConfiguration.java` |
| 공통 로직 연결 | `mvp/post/PostService.java`, `PostController.java` |
| 테스트 | `src/test/java/egovframework/backoffice/integration/NextPostIntegrationTest.java` |
| 문서 | `README.md`, `docs/REACT_PHASE2.md`, `docs/REACT_PHASE3A.md` |

생성 산출물인 `src/main/resources/static/next-app/`은 기존처럼 Git 제외 경로다. 배포 JAR을 만들기 전에 React 빌드를 포함해야 한다.

## 8. 3B의 DB·분류 변경이 영향을 주는 곳

아래는 설계 시 확인할 경계이며 이번 단계에서는 변경하지 않았다.

| 영향 범위 | 3B에서 필요한 검토 |
| --- | --- |
| 콘텐츠 DTO와 입력 | `PostDocument.categoryId`, `SaveRequest`, React `PostDocument`, 단일 선택 UI, fingerprint를 새 분류 계약과 호환되게 변경 |
| 저장·조회 서비스 | `PostService`의 분류 존재 검증·저장, `PostMapper` / `CmsMapper`의 목록·필터·연결 조회를 설계에 맞게 확장 |
| 초안과 발행본 | 현재 `post_publications.category_id`의 의미를 유지하면서 복수 분류의 발행 시점 스냅샷과 미반영 수정 판정을 설계 |
| 탐색 관계 | 목록 `categoryId` 필터, 사이트 구조 CATEGORY 메뉴 연결, 페이지 POSTS 블록의 분류 연결. 콘텐츠 유형/기수/주제와 방문자 메뉴를 혼동하지 않도록 정의 |
| 기존 데이터 | 기존 category_id 매핑, 미분류, 하위 호환, 데이터 이관·되돌리기, 조회 인덱스 검토. 전체 IA 자동 삽입과 구조 변경 분리 |
| 검증 | 기존 콘텐츠 ID, 권한, revision 충돌, 미디어, 초안·발행본 보존 테스트를 유지하고 새 분류 조합의 테스트 추가 |

공통 ContentEditor의 ID 기반 조회·저장, 본문 Delta, 미디어, 인증/CSRF, 기존 업무 서비스 재사용 경계는 유지할 수 있다. 콘텐츠 유형별 상세 필드, SUPPORTER 발행 권한, 승인·반려, 신규 생성 범위, 자동저장 정책은 별도 결정 사항으로 남긴다.
