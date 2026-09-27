# AICA React 2차 구현 결과

이 문서는 2차 완료 시점의 기록이다. 이후 기존 콘텐츠 편집이 React 공통 ContentEditor로 연결되었다. 현재 콘텐츠 편집·API·검증 범위는 [3A 구현 결과](REACT_PHASE3A.md)를 참고한다.

2026-09-26. 기존 `/admin`을 보존하고 `/admin-next`의 관리 골격을 확장했다. 기존 페이지·콘텐츠·계정·미디어·메뉴·설정 원본을 사용한다. DB 마이그레이션, IA 초기 데이터, 새로운 업무 서비스는 추가하지 않았다.

## 1. React 메뉴와 실제 기능

| 메뉴 | React에서 제공하는 기능 | 기존 화면 / 후속 범위 |
| --- | --- | --- |
| 대시보드 | 기존 PostService의 콘텐츠 수, 오늘/최근 7일 작성 수, 최근 콘텐츠 | 방문 통계는 연동 전 표시. 예시 수치를 실측처럼 보여주지 않음 |
| 콘텐츠 목록 | 전체·임시저장·발행·비공개, 제목/본문 검색, 분류 필터, 페이지 이동 | 작성·수정·발행·삭제와 분류 관리는 기존 화면 |
| 미디어 관리 | 검색, 업로드, 이름/대체 텍스트 수정, 삭제, 사용 중인 위치 표시 | 파일 본문은 기존 파일 전달 경로 사용 |
| 전체 페이지 현황 | 검색, 상태 필터, 최근 수정, 미반영 수정 표시 | 모든 기존 페이지를 같은 React PageEditor로 편집. 신규 생성·발행 등은 기존 화면 |
| 메뉴 관리 | PAGE/CATEGORY/LINK 연결, 노출, 순서 저장, 추가·수정·삭제 | 기존 단일 단계 메뉴 구조 유지 |
| 공통 스타일 | 강조색, 상단 배경색, 모서리 저장 | 실제 홈페이지 연결은 별도 |
| 공통 컴포넌트 | 기존 로고, 상단·하단 문구 저장 | **일부 구현**. 등록형 컴포넌트·공용 템플릿은 **후속 구현** 표시 |
| 운영 계정 관리 | 기존 계정·역할·활성 상태 조회 | 계정 발급·역할 변경·비밀번호 초기화·비활성화는 기존 화면 |
| 역할 / 권한 관리 | AccessPolicy의 현재 권한 조회 | 정책 편집·새 역할은 추가하지 않음. 계정별 역할 변경은 기존 화면 |
| 활동 이력 | 검색, 페이지 이동, 임시저장 포함 여부 | 기존 이력 재사용. 버전 복원은 제공하지 않음 |
| 기본 정보 | 사이트 이름·소개·문의 이메일·첫 화면 저장 | 기존 설정 원본 공유 |
| SNS / 외부 링크 | 조회·추가·수정·삭제·순서 저장 | 기존 링크 원본 공유 |
| 시스템 설정 | 목록당 글 수 저장 | **일부 구현**. 새로운 시스템 정책·설정은 임의 추가하지 않음 |

위 13개 진입점을 모두 구성했다. SUPPORTER는 본인 콘텐츠·미디어와 해당 범위의 대시보드만 사용한다. 페이지·메뉴·디자인·설정은 기존 ALL_POSTS, 운영 계정·권한 조회·활동 이력은 기존 MANAGE_ACCOUNTS 권한을 요구한다.

## 2. 재사용과 중복 방지

- `PageService`, `PostService`, `SiteService`, `MediaService`, `UsageService`, `AccountService`, `AccessPolicy`, `CmsAccess`를 그대로 호출한다. 이번 단계에서 이 서비스들의 소스는 변경하지 않았다.
- 기존 MyBatis 매퍼, 엔티티, 테이블, 세션 인증, 비밀번호/계정 정책, CSRF, 미디어 검증, 사용 중 삭제 제한, 저장 이력 처리를 재사용한다.
- 페이지 API는 기존 `PageService.saveDocument(..., "save")`를 호출한다. 현재 revision 검사와 발행본 보존을 유지한다. 페이지 작성·발행 API를 새로 만들지 않았다.
- React 화면과 JSON 응답 변환만 추가했다. 같은 값을 별도 테이블, 저장소, 브라우저 영구 저장소에 복제하지 않는다. 화면 전환 중 입력 상태는 메모리에만 유지한다.
- 계정 API는 별도 `AccountRow`를 사용해 비밀번호 해시와 인증 버전을 응답에서 제외한다.
- 기존 Thymeleaf 템플릿과 관리자 컨트롤러는 2차에서 변경하지 않았다. 계정 쓰기 작업과 콘텐츠 편집기는 새 탭으로 연결한다.

## 3. 두 탐색 경로와 같은 원본

`frontend/src/navigation.ts`의 `pagePath(id)`가 목록과 구조 보기의 공통 페이지 경로다.

```text
사이트 관리 → 전체 페이지 현황 → 인사교 소개
사이트 구조 → 홈페이지 메뉴 → 인사교 소개
  → /admin-next/pages/65/edit
  → PagePanel(id=65) → PageEditor(initial.id=65)
  → /api/admin/next/pages/65 → PageService → site_pages.id=65
```

1차의 ID 65 제한만 해제해 기존 홈 ID 1도 같은 편집기를 사용한다. 조회만으로 페이지를 생성하지 않으며 없는 ID는 404다.

콘텐츠도 구조의 CATEGORY 대상과 페이지 POSTS 블록의 ‘연결된 콘텐츠 보기’가 같은 `/admin-next/posts?categoryId=...` 목록으로 들어간다. 사이트 관리의 분류 필터도 같은 목록이다. 각 행은 `postEditorPath(id)`로 **동일한 기존 `/admin/posts/{id}/edit` 편집기**를 연다. 분류별 별도 편집기나 콘텐츠 사본은 없다.

사이트 구조는 `/bootstrap`이 반환하는 실제 메뉴의 PAGE/CATEGORY 연결을 사용한다. 메뉴 순서와 숨김을 반영하고, 연결되지 않은 페이지는 ‘메뉴 밖 페이지’, 연결되지 않은 분류는 ‘콘텐츠 분류’에 표시한다. 분류에는 방문자 메뉴에 연결되지 않았다는 표시를 둔다. 직접 LINK는 해당 메뉴 설정을 연다. 트리는 탐색 전용이며 트리 전용 저장·생성·정렬 기능은 없다. 새로고침과 브라우저 포커스 복귀 시 원본을 다시 조회한다.

## 4. 추가/변경 API

공통 접두사: `/api/admin/next`. JSON 변환을 담당하는 `NextWorkspaceApi`를 추가했다.

| 메서드 | 경로 | 처리 |
| --- | --- | --- |
| GET | `/bootstrap` | 기존 API 확장: 권한별 사용자·페이지 목록·메뉴·분류·이미지·CSRF |
| GET | `/dashboard` | 실제 콘텐츠 집계 |
| GET | `/pages` | 전체 페이지 요약 |
| GET | `/posts` | 기존 콘텐츠 조회/필터/페이지 이동 |
| GET, POST | `/media` | 조회, multipart 업로드 |
| PUT, DELETE | `/media/{id}` | 파일 정보 수정, 삭제 |
| GET | `/media/{id}/usage` | 사용 중인 위치 |
| GET, POST | `/menus`, `/links` | 기존 항목 조회, 추가 |
| PUT, DELETE | `/menus/{id}`, `/links/{id}` | 수정, 삭제 |
| PUT | `/menus/order`, `/links/order` | 기존 전체 ID 목록 순서 저장 |
| GET, PUT | `/settings/{group}` | basic / style / components / system 기존 설정 |
| GET | `/accounts`, `/roles`, `/activity` | 계정 요약, 현재 권한 정의, 기존 활동 이력 |

기존 `NextPageApi`의 GET/PUT `/pages/{id}`, GET/POST `/pages/{id}/preview`는 유지하며 대상만 모든 기존 페이지로 확장했다. RichEditor 이미지 업로드는 기존 `/admin/media/upload`, 파일 조회는 `/admin/media/{id}/file`을 사용한다.

변경 API는 CSRF와 기존 권한 검사를 거친다. API 오류는 JSON으로 반환한다. UI의 비활성 메뉴만으로 권한을 제한하지 않는다.

## 5. 입력 상태와 기본 동작

- 기본 진입은 대시보드, 최초 탐색 모드는 사이트 관리다. `?view=manage|structure`가 우선하며 선택은 계정별 sessionStorage에 기억한다.
- 보기 전환은 현재 작업 경로를 유지한다. 브라우저 제목과 경로가 현재 작업을 나타낸다.
- 방문한 화면을 경로별로 유지해 페이지·설정·메뉴/링크·파일 정보 입력이 다른 메뉴를 다녀와도 남는다. 같은 페이지 ID의 편집기 인스턴스는 하나다.
- 페이지의 기존 1.8초 자동 초안 저장 정책을 유지한다. 다른 화면으로 이동해 숨겨진 편집기도 미완료 초안 저장을 이어간다. Ctrl/Cmd+S는 현재 보이는 페이지 편집기에만 적용한다.
- 미저장 상태에서 탭 닫기/전체 새로고침은 이탈 경고를 사용한다. 보기 전환과 내부 메뉴 이동은 입력을 버리지 않는다. 탭 종료 후 복구하는 브라우저 영구 초안 기능은 추가하지 않았다.
- 페이지는 기존 충돌 감지와 재조회 동작을 유지한다. 메뉴/설정에 새로운 동시 편집 정책을 추가하지 않았다.

## 6. 검증 결과

### 자동 검사

- `frontend`: `pnpm run build` — TypeScript 검사와 Vite 빌드 성공.
- `scripts/mvn-local.ps1 -B -ntp -Pegov43-probe verify` — **42개, 실패 0, 오류 0, 건너뜀 0**. `.cache/react-phase2-verify.log` 및 `target/surefire-reports/`.
- 마지막 UI 수정 뒤 프런트엔드를 다시 빌드하고 `-DskipTests package`로 실행 JAR에 포함했다. 업무/백엔드 코드는 통과한 검사 이후 변경하지 않았다.
- 추가한 `NextWorkspaceIntegrationTest` 7개는 메뉴/API 조회, 데이터 자동 생성 방지, ID 1/65 공통 저장, 발행본 유지, 분류/상태/소유자 조회 제한, 메뉴 변경/정렬, 설정/링크 검증, 미디어 소유권/사용 중 삭제 방지, 권한/CSRF/세션 무효화, 계정 응답의 민감 필드 제외를 검증한다.
- 기존 `NextAdminIntegrationTest`를 새 bootstrap/권한 범위에 맞추고 초안·미리보기·충돌·발행본 보존 검증을 유지했다. 기존 CMS, 계정, eGov 호환 검사도 통과했다.

### 실제 브라우저

원본 백업에서 복원한 별도 H2 DB, `127.0.0.1:8082`에서 변경 테스트했다.

- 13개 관리 메뉴 화면 로딩 및 실제 데이터 표시 확인.
- ID 65의 제목을 비워 자동 저장을 막고 섹션을 수정한 뒤, 보기 전환·홈 이동·전체 페이지 목록 재진입을 거쳐 입력이 유지되는지 확인. ID 65 편집기 DOM 인스턴스는 1개였다.
- 제목 복구 후 초안 저장·재조회·실시간 미리보기에서 수정 내용 확인. 홈 ID 1은 별도의 원본으로 같은 PageEditor에서 열렸다.
- 페이지의 POSTS 블록, 구조의 교육 소식, 콘텐츠 목록이 같은 분류 ID 2와 콘텐츠 ID 33(`/admin/posts/33/edit`)을 사용함을 확인. 기존 편집기 새 탭 정상 진입 확인.
- 메뉴에 기존 분류 연결, 숨김, 순서 변경 후 구조 보기의 순서·숨김·분류 위치가 갱신됨을 확인.
- 공통 설정 입력이 다른 화면을 다녀와도 유지됨을 확인. 스타일·상하단 문구·기본 정보·목록 수·외부 링크 저장 확인. Thymeleaf 디자인 설정에서 동일한 저장 값 확인.
- 미디어 업로드·이름/대체 텍스트 수정·삭제와 목록 갱신 확인.
- 계정 목록, 조회 전용 권한 정의, 실제 활동 이력 확인. 미구현 공통 컴포넌트와 통계 연동 상태가 명시됨을 확인.
- 서버 종료 후 읽기 전용 SQL로 확인: 검증 DB의 페이지 65 초안 revision은 7, 발행본은 기존 4를 유지했다. 페이지 2개·콘텐츠 9개·분류 3개는 그대로이며, 명시적으로 추가한 검증 메뉴만 1개 증가했다. 원본 DB는 페이지 65 초안/발행본 모두 4이고 메뉴도 기존 2개였다.

권한별 거부·CSRF·충돌·사용 중 미디어 삭제 차단은 자동 HTTP 통합 검사로 검증했다. 모든 권한별 브라우저 조합을 수동 검사한 것은 아니다. 실제 홈페이지 통계나 게시 연동을 검증한 것은 아니다.

## 7. 변경 파일과 보존

2차 기준점은 `.cache/checkpoints/20260926-222040-react-phase2/`이다. 수정/미추적 소스 159개의 ZIP·SHA256 목록, Git bundle/patch/status, H2 BACKUP과 DB 해시, `RESTORE.md`를 보존했다. 원본 DB가 이 해시와 일치함을 테스트 종료 전 확인했다. 이전 `workbench/navigation-draft/` 미완성 작업도 유지했다.

1차 실행 JAR도 같은 기준점의 `phase1-runtime.jar`로 보존했다. 검증 DB는 `.cache/react-phase2-data/`에 분리해 남겼으며 원본으로 복사하지 않았다. 기존 로컬 프로필을 8081에서 실행해 원래 데이터로 `/admin-next`를 제공한다.

주요 변경 파일:

- 신규: `frontend/src/navigation.ts`, `ReadPanels.tsx`, `EditPanels.tsx`, `ui.tsx`, `workspace.css`.
- 변경: `frontend/index.html`, `frontend/src/main.tsx`, `PageEditor.tsx`, `types.ts`, `api.ts`.
- 신규: `src/main/java/egovframework/backoffice/mvp/next/NextWorkspaceApi.java`.
- 변경: 같은 디렉터리의 `NextAdminController.java`, `NextPageApi.java`, `NextApiErrors.java` 및 `mvp/security/SecurityConfiguration.java`.
- 검사: 신규 `NextWorkspaceIntegrationTest.java`, 변경 `NextAdminIntegrationTest.java`.
- 문서: 이 문서, README, 1차 문서의 현행 안내.

기준점 이후 기존 파일 삭제는 없다. DB/매퍼/업무 서비스/Thymeleaf 소스 변경도 없다. V4는 비활성 보관 상태를 유지하고 실행 JAR에는 V1~V3만 포함한다. React 빌드 산출물은 기존 정책대로 Git에서 제외한다.

## 8. 3차 전에 결정할 사항

1. 기존 콘텐츠 편집기와 계정 쓰기 화면 중 React 전환 우선순위. 같은 기존 서비스/API 경계는 유지한다.
2. 실제 IA 확정과 현재 메뉴·페이지·분류의 연결표. 다단계 구조가 필요하면 DB 구조와 초기 데이터 등록을 별도로 결정한다.
3. 콘텐츠 유형/기수/주제의 복수 분류 표현 및 전문 콘텐츠 필드. 이번에는 DB를 추가하지 않았다.
4. SUPPORTER 발행 권한, 승인·반려, 페이지 신규 생성 범위, 자동저장/이탈 정책. 기존 동작을 유지했으며 새 정책을 확정하지 않았다.
5. 블록 식별자·공용 컴포넌트/템플릿의 관리 범위와 실제 홈페이지/통계 연결 계약.

## 실행

```powershell
.\scripts\build-admin-next.ps1
.\scripts\run-local.ps1
```

React `/admin-next`, 기존 관리자 `/admin`, 기존 로컬 계정 `1234 / 1234`. 로그인 자체는 기존 로그인과 성공 이동 경로를 유지하므로 로그인 후 `/admin-next`에 진입한다.
