# 4B 블록 편집 UI 완료 기록

2026-09-27. 원본 V3 DB는 그대로 유지했다. 4A의 최신 V8 DB를 새 사본으로 복제해 검증했으며 **새 migration과 DB 구조 변경은 없다.**

- 검증 화면: http://127.0.0.1:8087/admin-next/pages/65/edit?view=structure
- 개발 계정: 1234 / 1234
- 실행 스크립트: `scripts/run-block-editor-copy.ps1` (기본 포트8087, 4B 사본 경로만 허용)
- 검증 DB: `.cache/react-phase4b-data/aica-phase4b.mv.db`
- 실행 JAR: `.cache/react-phase4b-test.jar`

## 1. 블록 편집 UI

공통 PageEditor를 다음 영역으로 구성했다.

1. 페이지 제목과 고정 작업 영역: 초안 저장, 다시 조회, 미리보기, 기존 발행 관리 진입.
2. 블록 목록: 실제 순서, 타입, 제목, Variation, 표시/숨김 상태. 목록에서 선택한 블록만 속성을 편집한다.
3. 선택한 블록: 입력값, Variation, 표시 여부, 위/아래 이동, 복제, 삭제.
4. 실시간 초안 미리보기: 선택한 표시 블록에 윤곽선을 표시하고 숨김 블록은 렌더링하지 않는다.

넓은 화면은 목록/편집/미리보기 3열, 중간 폭은 왼쪽 목록·편집과 오른쪽 미리보기, 작은 화면은 한 열로 표시한다. 블록 목록과 편집 상태는 ID로 연결된다. 사이트 관리/사이트 구조 모드 전환에서도 같은 페이지 ID와 PageEditor를 유지한다.

저장은 기존 페이지 전체 초안 PUT과 기존 자동저장을 재사용한다. 블록별 저장 API는 추가하지 않았다. 발행 관리 링크는 미저장 변경·저장/업로드 진행·충돌 상태에서는 비활성화하고, 저장 완료 후 기존 Thymeleaf 발행 화면을 연다. 발행 권한/정책은 변경하지 않았다.

## 2. 컴포넌트 정의와 실제 지원 타입

서버의 `PageComponentRegistry`가 type, 표시명, 설명, 입력 필드, 기본값, schemaVersion, 기본 Variation, 허용 Variation을 정의한다. 운영자가 코드를 등록하는 기능이 아니다.

| 타입 | 입력 | Variation |
|---|---|---|
| HERO | 제목, 본문, 버튼 이름·주소 | default / centered |
| TEXT | 제목, 본문 | default |
| IMAGE | 제목, 본문, 기존 이미지 선택·업로드 | default |
| POSTS | 제목, 기존 카테고리 | default |
| CTA | 제목, 본문, 버튼 이름·주소 | default |

새 타입은 추가하지 않았다. 기존 POSTS의 category 연결을 유지하며 후기/FAQ/맛집 조건 연결은 만들지 않았다. 등록 정의를 읽어 React가 입력 항목을 표시하고, 같은 정의로 서버가 type/version/Variation을 검증한다.

사이트 관리 > 디자인 관리 > 공통 컴포넌트는 같은 등록 목록과 Variation을 조회한다. 기존 로고·상단 문구·하단 문구 설정은 아래에 유지하고 사이트 전체에 적용되는 설정임을 구분했다. 필요성이 없는 활성/비활성 정책이나 설정 DB는 추가하지 않았다.

## 3. 실제 Variation

HERO의 `default`에 더해 **`centered` — 가운데 강조형**을 구현했다. 기존 제목·본문·버튼을 그대로 사용하면서 가운데 정렬, 본문 최대 폭, 패딩과 상단 강조선으로 배치를 바꾼다.

- React 미리보기와 Thymeleaf의 실시간/저장 내용 미리보기에 동일한 `static/css/page-blocks.css`를 사용한다.
- 실제 브라우저에서 default의 `text-align:start`, 상단선 0px가 centered의 `text-align:center`, 상단선 3px로 바뀜을 확인했다.
- centered 저장·재조회·초안 미리보기·발행 snapshot을 검증했다.
- 다른 타입에 centered를 지정하거나 미등록 Variation/자유 CSS 값을 제출하면 저장·미리보기에서 거절한다.
- schemaVersion은 **2 유지**다. 현재 저장 필드 형식은 그대로이고, 등록된 허용 값과 렌더링 규칙만 확장했다.

공개 홈페이지 전체 렌더러는 이번 범위가 아니다. 미리보기의 POSTS도 기존 연결 전 안내 영역을 유지한다.

## 4. 추가·복제·삭제·순서 변경

- **추가:** ‘블록 추가’ 대화상자에서 등록된 타입을 선택한다. 해당 정의의 기본값·schemaVersion·default Variation과 새 UUID를 사용한다.
- **복제:** 원본 바로 다음에 삽입하고 복제본을 선택한다. 입력 전체, Variation, visible을 복사하고 ID만 새로 발급한다. 현재 필드는 원시 값과 bodyDoc 문자열이므로 원본과 가변 객체를 공유하지 않는다.
- **삭제:** 삭제 확인창에서 취소/확정을 제공한다. 확정 시 초안에서 제거하고 인접 블록을 선택한다. 저장 시 기존 PageBlockService가 삭제 ID를 폐기 기록으로 남긴다.
- **이동:** 위/아래 버튼으로 배열 순서만 바꾼다. 선택과 편집 데이터는 같은 ID를 유지한다. 드래그는 추가하지 않았다.
- **숨김:** visible을 변경하며 미저장 감지·전체 저장·미리보기 흐름에 포함한다.

등록된 ID의 소유 확인, 중복/다른 페이지/폐기 ID 거절, revision 충돌, CMS lock과 트랜잭션은 4A 처리를 재사용한다.

## 5. ID·초안·발행본 결과

실제 페이지의 기존 ID는 유지했다.

| 페이지 | 블록 | ID |
|---|---|---|
| 1 | HERO | block_b6fa6085-ae9d-4e3b-b00b-ec54f7ecee32 |
| 1 | POSTS | block_baafe12a-15d8-481b-b809-501e56203685 |
| 65 | HERO | block_9a49edcb-e9e6-4891-860d-28fa2eb88d1c |

브라우저에서 페이지 65의 원본을 centered로 바꾸고 복제했다. 복제본 ID는 `block_9e2c7aff-7469-41d9-aa80-cef236850df8`였다. 복제본만 본문·제목·Variation·visible을 바꾸고 이동해도 원본은 같았다. 새 TEXT의 `block_317f2d73-a997-4f43-ac85-ff2df6d41253`은 삭제 후 재사용 요청이 400으로 차단됐다.

초안에서 위 작업을 하는 동안 기존 발행본은 그대로였다. Thymeleaf로 재발행하면 같은 ID/순서/Variation/visible의 전체 초안 snapshot이 반영됐다. 발행본에 포함된 복제본을 다시 초안에서 삭제했을 때도 발행본은 남았고, 다음 발행 후에만 제거됐다.

검증 종료 시 페이지 1·65의 원래 내용·순서·Variation·표시 상태를 복원했고 시험으로 추가한 블록은 제거했다. 원래 블록 ID는 유지했다. 검증용 정상 저장/발행으로 revision·날짜·활동 이력은 증가했고 삭제한 시험 ID의 폐기 기록은 남았다. 원본 데이터의 임의 재작성이나 migration에 의한 변경과 구분한다.

## 6. Thymeleaf 호환

기존 페이지 폼과 PageService를 유지했다. cms.js는 ID/schemaVersion/Variation을 보존하며 새 Variation의 미리보기도 표시한다. 기존 화면에 React 편집 진입 안내를 추가했다. Variation 선택 UI는 React에만 있다.

브라우저에서 기존 폼의 제목을 바꾸고 임시저장한 뒤 ID·Variation·visible 보존과 발행본 유지가 확인됐다. 같은 폼의 발행 버튼으로 현재 초안 snapshot을 저장하는 것도 확인했다. 기존 후기/FAQ/맛집 저장·발행 로직은 변경하지 않았다.

## 7. API와 변경 코드

새 API는 **GET `/api/admin/next/page-components`** 하나다. 페이지 관리와 같은 manager 권한으로 읽기 전용 등록 정의를 반환한다. SUPPORTER 접근은 403이다. 기존 페이지 미리보기 응답에 `variation`을 추가했으며 별도 블록 저장·발행·복제 API는 없다.

| 영역 | 변경 파일 |
|---|---|
| 등록 정의 | 신규 PageComponentRegistry.java |
| 검증·복제·조회 | PageBlockService.java, PageService.java, NextPageApi.java |
| 접근 제어 | SecurityConfiguration.java에 새 조회 경로를 기존 페이지 관리 권한으로 연결 |
| 편집 UI | PageEditor.tsx, pageBlocks.ts, types.ts, main.tsx |
| 공통 컴포넌트 | 신규 ComponentCatalog.tsx, 기존 EditPanels.tsx |
| 대화상자/스타일 | 신규 BlockDialog.tsx, page-editor.css, page-blocks.css |
| 기존 관리자 | cms.js, page-form.html, preview.html, fragments.html |
| 테스트 | pageBlocks.test.ts, PageBlockWorkflowIntegrationTest.java |
| 실행 | 신규 scripts/run-block-editor-copy.ps1 |

기존 수정/미추적 파일을 삭제하지 않았다. 코드 변경 목록은 `.cache/react-phase4b-final-audit.json`에도 기록했다.

## 8. 자동·브라우저 검증

| 검사 | 결과 |
|---|---|
| Java 전체 | **98 통과 / 실패 0 / 오류 0 / 제외 0** |
| 실제 사본 migration | V3→V6, V6→V7, V7→V8 테스트 포함. 보호 경로/파일명 규칙 유지 |
| React 유틸 | **25 통과**, 복제 독립성·이동·삭제 후 선택·새 ID 포함 |
| TypeScript/Vite | build 성공 |
| ID/내용 유지 | 기존 블록 수정·위아래 이동·모드 전환·재조회 성공 |
| 신규/복제 | 새 UUID, 기본값, 전체 데이터 복사, 독립 수정 성공 |
| 삭제 | 확인창 취소/확정, 삭제 재조회, 폐기 ID 재사용 400 |
| visible/Variation | 저장·재조회·실제 렌더링 차이 확인 |
| 초안/발행 | 변경 중 기존 발행본 유지, 재발행 후 같은 전체 snapshot 반영 |
| Thymeleaf | 브라우저 저장·발행 후 ID/Variation/visible 유지 |
| 서버 재시작 | 정상 종료/재시작 전후 페이지 1·65, block ID, 초안/발행본, 18개 콘텐츠 조회값 동일 |
| 기존 페이지/데이터 | 실행 직후 모든 기존 API 값 동일. 종료 시 시험 수정 복원. 메뉴·category·미디어·기존 콘텐츠 보존 |
| 후기/FAQ/맛집 | 전체 회귀 검사 통과, 실제 18개 콘텐츠와 주요 발행본 조회값 동일 |
| 보안/충돌 | 기존 CSRF·권한·revision·트랜잭션 검사 통과, 미등록/타입 불일치 Variation 거절 |
| DB/원본 | 새 migration 없음. V8 이력 동일. 원본 V3 파일 해시 동일 |

브라우저 초기 기존 탭에서는 로그인 클릭 후 이동하지 않아 새 검증 탭으로 전환했다. 새 탭에서 로그인과 편집 동작을 검증했으며 이 문제를 앱 인증 수정으로 우회하지 않았다. 검증 도중 닫힌 탭은 같은 브라우저의 새 탭으로 이어서 확인했다.

주요 증거:

- `.cache/react-phase4b-verify2.log`, `target/surefire-reports/`: 최종 자동 테스트.
- `.cache/react-phase4b-pre-edit-audit.json`: 편집 전 기존 API 전체 비교.
- `.cache/react-phase4b-browser-*.json`: Variation·복제·독립 수정·추가·삭제·발행·기존 폼·HOME 이동.
- `.cache/react-phase4b-restart-audit.json`: 실제 서버 재시작 전후 동일성.
- `.cache/react-phase4b-centered-editor.png`: 검증 중 가운데 강조형/숨김 복제본 화면.
- `.cache/react-phase4b-component-catalog.png`: 공통 등록 목록 화면.
- `.cache/react-phase4b-final-page65.png`: 검증 내용을 복원한 완료 화면.

## 9. 보존·복구

`.cache/checkpoints/20260927-145301-react-phase4b/`에 작업 전 `baseline-v8.mv.db` + `baseline-4a-runtime.jar` + 수정/미추적 소스 ZIP을 보존했다. 완료 상태의 `completed-v8.mv.db` + `completed-4b-runtime.jar` + 소스 ZIP과 검증 기록도 같은 기준점에 보관했다. DB는 서버 정상 종료 후 복사했다. 상세 절차는 해당 폴더의 `RESTORE.md`, 해시는 `completed-checksums.json`이다.

27개 기존 테이블의 종류와 Flyway 이력은 변하지 않았다. UI 검증 때문에 값이 달라진 표는 SITE_PAGES, PAGE_PUBLICATIONS, PAGE_BLOCK_IDENTITIES, ACTIVITY_LOG뿐이다. 나머지 표의 지문은 작업 전과 같다.

원본 V3 SHA-256은 `55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`로 유지됐다. V1~V8 및 이전 미완성 V4 격리 자료의 파일 해시도 유지했다. 같은 V8 DB라도 4A 앱은 centered를 지원하지 않으므로 과거 버전 복구는 DB/JAR 쌍으로 한다.

H2/Flyway 버전 호환 경고와 이전 파일 연결 수명 문제는 기능 완료와 별개로 남긴다. 원본 적용 전에는 최신 원본 사본과 배포 JAR로 다시 검증해야 한다.

## 10. 4C에 전달할 기반

등록 정의→입력 필드/Variation 선택→서버 검증→페이지 전체 저장→초안 미리보기→기존 발행 snapshot 흐름이 연결됐다. 안정적인 ID와 공통 PageEditor를 그대로 사용해 4C의 콘텐츠 연결 블록을 확장할 수 있다.

4C에서는 콘텐츠 조건/직접 선택의 저장 필드, 원본 콘텐츠 조회 조건, 초안·발행 조회 기준, 미리보기와 실제 렌더러의 계약을 설계해야 한다. 현재 POSTS는 기존 category_id 기준만 유지한다. 후기/FAQ/맛집 조건 블록, 사이트 구조에서 block ID로 직접 이동, 공용 템플릿은 아직 구현하지 않았다. 인터뷰의 구조화 필드도 실제 시안에서 필요성이 확인될 때 검토한다.
