# 4C-2B 사이트 구조 → block ID 직접 이동 완료

2026-09-27. 4C-2A 최신 검증 DB를 새 V8 사본으로 복제했다. 원본 V3 및 V1~V8 migration은 변경하지 않았다. 이번 단계는 탐색/선택 연결이며 IA나 별도 블록 저장소를 추가하지 않았다.

검증 링크:

- 홈 POSTS: http://127.0.0.1:8090/admin-next/pages/1/edit?view=structure&block=block_baafe12a-15d8-481b-b809-501e56203685
- 인사교 소개 HERO: http://127.0.0.1:8090/admin-next/pages/65/edit?view=structure&block=block_9a49edcb-e9e6-4891-860d-28fa2eb88d1c

기존 로컬 개발 계정 1234 / 1234를 유지한다. 로그인 후 위 링크를 열면 된다.

## 1. 사이트 구조 연결

기존 메뉴의 PAGE targetId와 페이지 원본을 읽고, 각 페이지의 현재 초안 sections_json에서 블록 탐색 정보를 만든다. 기존 방문자 메뉴의 이름/순서/노출을 유지하며 메뉴 밖 페이지도 같은 방법으로 연결한다. 숨김 메뉴/블록 역시 관리자에게는 접근 가능하다.

현재 실제 연결은 홈(1)의 HERO/POSTS, 인사교 소개(65)의 HERO다. 현재 제목이 `테스트`인 소개 블록을 임의로 `무엇을 배우는지` 등으로 바꾸거나 없는 IA 항목을 생성하지 않았다. 후기/FAQ/맛집의 기존 content-list 탐색은 그대로다.

읽기 전용 응답 형태:

```json
{
  "kind": "page", "label": "홈", "pageId": 1,
  "blocks": [{
    "kind": "block", "label": "사관학교 소식", "pageId": 1,
    "blockId": "block_baafe12a-15d8-481b-b809-501e56203685",
    "type": "POSTS", "visible": true
  }],
  "issue": null
}
```

본문·query·manual 콘텐츠 내용은 트리에 복사하지 않는다. 탐색 타입은 page/block과 별도 filters를 가진 content-list로 구분할 수 있다. DB에는 탐색 트리를 저장하지 않는다. 블록 제목/순서/표시 상태는 원본에서 파생되며, 열린 편집기의 미저장 변경은 같은 문서의 메모리 상태로 트리에 반영한다.

## 2. URL과 같은 편집기

`/admin-next/pages/{pageId}/edit?block={blockId}&view=structure`

사이트 관리에서는 view만 manage다. pagePath 함수가 동일 경로를 만들고, 두 경로 모두 하나의 PagePanel/PageEditor와 같은 페이지/블록 ID를 사용한다. block은 영구 ID이며 배열 위치나 제목으로 연결하지 않는다.

블록을 선택하면 URL의 block 값을 변경한다. 블록 입력 영역의 `현재 블록 링크`를 복사하거나 새 탭에서 열 수 있다. 미저장 본문은 URL에 포함하지 않는다.

block이 지정되지 않은 페이지 진입은 첫 번째 **유효하고 유일한 ID**를 가진 블록을 선택해 replaceState로 URL을 보완한다. 명시적으로 주어진 block이 비어 있거나 틀리거나 삭제됐다면 이 기본 선택을 적용하지 않는다.

## 3. 선택과 스크롤

PageEditor는 요청한 blockId를 현재 doc.sections에서 정확히 찾는다. 선택된 기존 입력 화면을 렌더링한 뒤 입력 영역으로 scrollIntoView하고 포커스를 이동한다. 고정 상단 바에 가리지 않도록 scroll-margin을 적용했다. 브라우저에서 편집 영역 상단 155px, 포커스 block-inspector, URL/선택 ID 일치를 확인했다.

내용/visible/variation/복제/삭제/위아래 이동은 기존 4B 기능이다. 추가·복제 후 새 ID가 URL 선택으로 연결되며 이동/내용 수정은 ID를 바꾸지 않는다. 원본 POSTS의 category/query/manual도 그대로다. 별도 사이트 구조 편집기나 블록 저장 API는 없다.

## 4. 상태와 미저장 보호

| 동작 | 처리 |
|---|---|
| 사이트 관리 ↔ 사이트 구조 | 같은 인스턴스/입력을 유지하고 URL의 view만 변경 |
| 같은 페이지의 다른 블록 선택 | pushState, 같은 PageEditor에서 선택만 변경 |
| 뒤로/앞으로 | URL의 page/block/view로 선택 복원 |
| 새로고침·새 탭 | 같은 URL의 페이지 원본 조회 후 해당 block 선택 |
| 다른 화면으로 이동 | 미저장/저장 중/업로드 중이면 관리자 확인창 표시 |
| 이동 취소 | 페이지·block·입력·URL 유지. 취소한 popstate도 원래 history 위치로 복원 |
| 이동 승인 | 이전 편집기를 이 탭에 유지. 돌아오면 작성 중 입력도 유지 |

기존 페이지별 편집기 캐시, beforeunload 보호, 1.8초 자동 초안 저장, 저장 충돌 검사와 재조회 보호를 유지했다. 페이지 내부 이동/보기 전환에는 확인창을 띄우지 않는다. 다른 화면 이동 확인은 공통 BlockDialog를 사용한다. 브라우저 뒤로가기 승인도 실제 history 위치를 이동하므로 앞으로가기를 유지한다.

미저장 내용의 영구 복구를 새로 구현한 것은 아니다. 새로고침/탭 종료에는 기존 beforeunload 정책이 적용된다. 세션 만료 후 로그인은 기존 인증 정책대로 `/admin`으로 이동하므로, 전달받은 딥링크를 다시 열어야 한다. 인증 리다이렉트 정책은 변경하지 않았다.

## 5. 삭제·숨김·예외

- 없는/삭제된 block: 페이지는 열고 `연결된 블록을 찾을 수 없습니다`를 표시한다. 선택은 null이며 첫 블록으로 대체하지 않는다. 운영자가 목록에서 다른 블록을 직접 선택할 수 있다.
- 현재 선택 블록 삭제: 삭제한 ID를 URL에 유지해 같은 상태를 표시한다. 새 ID로 조용히 바꾸지 않는다. 재조회 후에도 동일하다.
- 숨김 block: 트리에 `숨김`, 편집기에 `숨김 블록`을 표시한다. 기존 편집 기능은 계속 사용할 수 있다. 공개 미리보기 제외 원칙은 그대로다.
- ID 없음/중복/잘못된 형식: 탐색 응답의 blockId는 null, UI는 이동 불가와 이유를 표시한다. 내용/배열 순서로 ID를 생성하거나 추측하지 않는다. 해당 페이지 편집에서도 식별 정보 오류 안내와 저장 차단을 적용한다.
- JSON 자체가 잘못된 페이지: 해당 페이지의 블록 조회 오류를 표시하고 다른 페이지 탐색은 유지한다. 데이터를 자동 변환하지 않는다.

현재 트리는 원본의 투영이므로 별도 연결 관리 화면이 필요하지 않다. 향후 독립적인 IA 연결을 등록한다면 삭제된 참조를 운영자가 수정하는 정책은 별도 설계해야 한다.

## 6. API·변경 범위

신규 `GET /api/admin/next/page-structure` 하나를 추가했다. 기존 PageService.list와 sections 파서, 컴포넌트 정의를 재사용한다. 본문을 반환하지 않고 페이지/블록 탐색 메타데이터만 읽는다. SecurityConfiguration과 PageService 양쪽에서 기존 페이지 관리 권한을 적용한다. SUPPORTER는 403이며 기존 역할/권한 자체를 변경하지 않았다.

| 영역 | 변경 파일/역할 |
|---|---|
| Spring | PageService: 읽기 전용 투영, NextPageApi: 조회 경로, SecurityConfiguration: 기존 관리자 권한 등록 |
| ID 검사 | PageBlockService: 기존 UUID 형식 검사를 공통 함수로 추출 |
| React 탐색 | PageStructureBranch, blockNavigation, types, navigation: 원본 기반 트리와 page/block 경로 |
| React 상태 | useWorkspaceRoutes, main: history·보기·미저장 이동 확인과 기존 편집기 유지 |
| 공통 편집기 | PageEditor, page-editor.css: URL 선택, 입력 영역 이동, 숨김/없는 대상 안내, 블록 링크 |
| 검증 | PageStructureNavigationIntegrationTest, blockNavigation.test.ts |
| 실행 | run-block-navigation-copy.ps1: 8090과 4C-2B 사본 경로 제한 |

Mapper, PostService, ContentEditor, sections_json 계약, 페이지 저장/발행 서비스 로직, Thymeleaf 소스, 콘텐츠 분류/주소 모델, schema migration은 변경하지 않았다.

## 7. 테스트 결과

Java 전체 verify: **114개 통과, 실패 0, 오류 0, 제외 0**. 프런트 **34개 통과**, TypeScript/production build 성공. Java 신규 4개, 프런트 신규 5개다. 기존 V3/V6/V7 파일 사본 migration 회귀 검사도 새 사본에서 실행했다.

첫 검증에서 새 API의 보안 경로 등록 누락을 발견했다. 기존 페이지 관리 권한에 등록한 뒤 전체 114개를 다시 통과했다. 이후 이동 확인 UI를 관리자 내부 dialog로 정리하고 프런트 테스트/빌드 및 실제 브라우저 이동 취소·승인을 재검증했다.

| 필수 검증 | 결과 |
|---|---|
| 1~5 구조 → page/block, 자동 선택/스크롤, 동일 원본 | API/브라우저에서 페이지 1·65와 실제 ID 일치 |
| 6 새로고침 | POSTS 딥링크 그대로 복원, 새 탭도 동일 |
| 7 뒤로/앞으로 | HERO ↔ POSTS, 홈 ↔ 소개, 미저장 취소/승인 후 이력까지 확인 |
| 8 숨김 | 트리·편집기 표시와 재조회 후 접근 확인 |
| 9 삭제/없는 ID | 복제 ID 삭제 후 URL 유지, 선택 null, 새로고침 후 동일 안내 |
| 10 ID 없는 예외 | Java/프런트 테스트에서 ID 생성·내용/순서 추측 없음, 원본 JSON 불변 |
| 11~12 같은/다른 페이지 | 동일 페이지 선택만 변경, 다른 페이지 미저장 확인·취소·입력 유지 |
| 13~14 편집·이동·복제 | 기존 ID 유지, 복제 새 ID, 재정렬/재조회 후 각각 링크 유지 |
| 15~16 POSTS·후기/FAQ/맛집 | 기존 category/query/manual 및 콘텐츠 전체 회귀 통과 |
| 17 Thymeleaf | 실제 임시저장/발행 후 ID/visible/variation/소스 설정 보존 |
| 18 재시작 | 페이지/발행본/탐색 API/미리보기 전후 완전 일치. 재로그인 후 같은 딥링크 선택·숨김·스크롤 확인 |

브라우저 입력 보호 검증은 제목을 잠시 비워 자동 저장이 진행되지 않는 상태에서 수행했다. 이동 취소 후 빈 입력/URL/ID가 유지됐고, 승인하여 소개 페이지로 이동했다가 앞으로 돌아와도 입력이 남았다. 검증 후 제목은 원래대로 복원했다.

기존 Thymeleaf는 bodyDoc가 null인 블록을 동등한 리치 문서로 정규화하는 기존 동작이 있다. ID/본문/소스 설정 보존을 분리 비교했고 완료 시 원래 bodyDoc로 복원했다.

## 8. 데이터 보존·실행·복구

서버 8090: `.cache/react-phase4c2b-test.jar`, DB `.cache/react-phase4c2b-data/aica-phase4c2b.mv.db`.

기준점 `.cache/checkpoints/20260927-162901-react-phase4c2b/`:

- baseline-v8.mv.db + baseline-4c2a-runtime.jar + 작업 전 source.zip
- verified-navigation-v8.mv.db + verified-4c2b-runtime.jar: 숨김/제목 수정 검증 상태
- completed-v8.mv.db + completed-4c2b-runtime.jar + completed-source.zip: 기존 화면 복원 완료
- manifest/체크섬, RESTORE.md, API 비교·브라우저 이미지·자동 테스트 로그

완료 DB는 페이지 1의 원래 내용/블록 ID/순서/표시/variation/category/query/manual로 복원했다. 페이지 65는 변경하지 않았다. 기존 콘텐츠 18개 API 문서는 모든 필드가 작업 전과 정확히 같다. 메뉴·category·권한도 같다. 시험 clone은 삭제하고 ID 폐기 기록은 남겼다. IA/콘텐츠/신규 페이지를 생성하지 않았다.

27개 테이블 비교에서 변경은 SITE_PAGES, PAGE_PUBLICATIONS, PAGE_BLOCK_IDENTITIES, ACTIVITY_LOG뿐이다. 홈 저장/발행 revision·시각과 시험 clone 폐기/활동 이력은 남는다. Flyway 이력 및 V1~V8 파일/패키징 결과는 그대로다.

원본 V3 SHA-256은 전후 동일하다:

`55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`

정상 종료 후 대응하는 DB/JAR 쌍을 새 폴더에 복사해 복구한다. 원본이나 현재 작업 폴더를 덮어쓰거나 reset/clean하지 않는다. 이번에는 JSON 저장 계약도 추가하지 않았지만 복구 시 검증한 쌍을 사용한다. 원본 migration은 여전히 금지한다.

## 9. 4D 전에 남은 사항

- 템플릿 적용을 페이지 초안의 추가/교체 중 어떻게 제공할지, 적용 후 개별 편집과 템플릿 변경의 관계를 결정해야 한다. 템플릿을 복제할 경우 새 block ID를 발급해야 한다.
- 현재 등록 컴포넌트와 variation/POSTS 소스 설정의 호환 범위를 템플릿 정의에서 명시해야 한다. 기존 페이지·발행본을 자동 덮어쓰면 안 된다.
- 실제 IA의 다른 이름/계층을 수동 매핑하는 관리 기능은 이번에 추가하지 않았다. 현재 저장된 메뉴/페이지/블록만 반영한다.
- 열린 편집기는 입력 보호를 위해 메모리 문서를 유지한다. 다른 창에서 변경했을 때는 기존 다시 조회/충돌 처리로 갱신한다. 전역 메뉴 새로고침이 편집 중 문서를 덮어쓰지 않는다.
- 로그인 후 딥링크 자동 복귀는 기존 인증 리다이렉트 정책과 함께 별도 결정할 사항이다.
- H2/Flyway 호환 경고와 이전 파일 연결 수명 문제는 해결된 것으로 보지 않는다. 원본 적용 전 최신 사본·대응 실행 파일에서 별도 재검증해야 한다.
