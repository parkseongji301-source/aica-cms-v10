# 4C-2A POSTS 직접 선택 완료 기록

2026-09-27. 4C-1 최신 V8 검증 DB를 새 사본으로 복제해 구현·검증했다. 원본 V3 DB에는 적용하지 않았다. 사이트 구조 → block ID 직접 이동은 다음 4C-2B 범위다.

검증 화면: http://127.0.0.1:8089/admin-next/pages/1/edit?view=structure

홈의 `사관학교 소식 · POSTS` → 콘텐츠 소스 `직접 선택` → `콘텐츠 추가`에서 확인할 수 있다. 계정은 기존 로컬 개발 계정 1234 / 1234를 유지한다. 기존 관리자 `/admin`이 아니라 `/admin-next` 경로다.

## 1. 저장 구조와 순서

기존 schemaVersion 2의 평면 블록 배열을 유지하며 optional `manual`만 추가했다. 새 migration/테이블/컬럼은 없다.

```json
{
  "id": "block_UUID",
  "type": "POSTS",
  "schemaVersion": 2,
  "variation": "default",
  "sourceMode": "manual",
  "categoryId": null,
  "query": {
    "typeCode": "REVIEW", "cohortIds": [], "topicIds": [],
    "sort": "LATEST", "limit": 6
  },
  "manual": { "postIds": [105, 103, 97, 101] }
}
```

- 제목/본문/분류/발행 상태를 페이지 JSON에 복사하지 않는다.
- 배열 순서가 노출 순서다. 위/아래 이동은 배열을 새로 만들고 순서를 바꾼다. query 최신순/개수 제한은 manual 결과에 적용하지 않는다.
- 0~20개, 양수 ID, 중복 없음으로 UI와 서버가 검증한다. 빈 배열은 0건이며 전체 콘텐츠 조회가 아니다.
- category/query/manual 전환은 선택한 소스만 바꾼다. 다른 소스의 저장값은 그대로 보관한다.
- 기존 block ID는 유지한다. 복제는 기존 새 UUID + structuredClone을 재사용해 manual 배열도 독립적으로 복사한다.
- 한 번 manual을 저장한 블록에서 해당 필드를 통째로 누락한 요청은 거절한다. 의도적인 비우기는 `{ "postIds": [] }`다.

## 2. 콘텐츠 선택 UI

`ManualPostsFields`는 선택 목록·상태와 선택 창을 제공한다. 선택 창은 기존 콘텐츠 목록의 제목/본문 검색, 유형, 기수, 주제, 페이지 이동을 재사용한다. 서로 다른 필터는 AND, 같은 기준의 복수 값은 OR라는 기존 규칙을 따른다. 같은 이름의 후기/FAQ 주제는 구분된 이름과 ID를 사용한다.

선택한 항목에는 번호, 제목, 발행 상태, 위/아래 이동, 선택 해제가 표시된다. 이미 선택한 항목은 선택 버튼이 비활성화된다. 추가 선택은 배열 끝에 붙으며 최대 20개다. 설정 변경은 기존 PageEditor의 미저장 감지·자동 초안 저장·수동 저장·미리보기로 처리한다. 탐색 보기 전환이나 블록 이동에도 선택 목록이 유지된다.

## 3. 미발행·삭제·사용 불가 처리

초안 편집에서는 미발행 콘텐츠도 선택할 수 있다. 관리자 상태 조회는 다음을 구분한다.

| 상태 | 관리자 표시 | 공개 결과 |
|---|---|---|
| PUBLISHED + 발행본 존재 | 발행 | 포함 |
| 초안/발행본 없음 | 미발행 | 제외 |
| PRIVATE | 비공개 | 제외 |
| deleted_at 존재 | 삭제됨 | 제외 |
| ID에 해당하는 원본 없음 | 사용 불가 | 제외 |

어떤 경우에도 설정의 postIds를 자동 삭제하지 않는다. 운영자가 선택 해제를 해야 제거된다. 조회 실패는 `상태 확인 실패`로 표시하며 삭제로 오인하지 않는다. 상태 새로고침 버튼과 창 포커스 복귀 재조회가 있다. 초안 제목이 발행 제목과 다르면 발행 제목도 별도로 보여준다.

## 4. 발행본 조회와 페이지 snapshot

`PublishedPostQueryService`의 기존 publicPosts/publicPostCount에 ID 조건을 추가했다. SQL IN 결과는 요청 순서를 보장하지 않으므로 서비스에서 postIds 순서대로 다시 배열한다. 단일 조회 결과를 ID로 매핑하므로 복수 분류 JOIN 중복이 생기지 않는다. count는 실제 공개 가능한 항목 수다.

콘텐츠 값은 `post_publications`에서 읽는다. posts는 현재 공개 상태·삭제 여부 판단에 사용한다. 콘텐츠 초안 제목/본문 수정은 결과에 반영되지 않고 재발행 후 바뀐다. 나머지 선택 항목과 순서는 그대로 유지된다.

페이지는 기존 `PageService`의 전체 문서 저장/발행 트랜잭션을 사용한다. 초안 manual 배열은 site_pages, 발행 시점 배열은 page_publications에 각각 저장된다. 페이지 초안의 재정렬/선택 해제는 페이지를 재발행하기 전 공개 순서를 바꾸지 않는다. 새 블록별 저장 API는 추가하지 않았다.

## 5. category/query와 Thymeleaf 호환

- 기존 sourceMode 미지정 블록은 category 방식으로 동작한다. 기존 categoryId를 신규 분류로 변환하지 않았다.
- query의 발행본 분류 조회와 AND/OR, 제한·정렬 규칙을 유지한다. 소스 전환 후 되돌리면 기존 query/manual 설정이 다시 나타난다.
- 현재 Thymeleaf JS는 sourceMode/query/manual을 카드에 보관하고 저장·발행 요청에 그대로 포함한다. query/manual일 때 category 입력은 비활성화하고 React에서 편집하도록 안내한다.
- 실제 브라우저에서 Thymeleaf 임시저장·발행본 저장 후 block ID, variation, category, query, manual이 그대로 유지됐다.
- 기존 리치 텍스트 편집기는 bodyDoc가 null인 블록에 동등한 문서를 생성한다. 이는 기존 정규화 동작이다. 첫 저장 비교에서 이 차이를 구분했고, 이후 재저장·발행의 전체 블록 일치도 확인했다. 완료 DB의 홈 본문은 작업 전 값으로 복원했다.

## 6. API와 변경 파일

신규 API는 관리자 표시용 상태 조회 하나다.

`GET /api/admin/next/pages/selected-posts?ids=105,103,97`

요청 순서대로 `{id, title, status, publicationTitle}`을 반환한다. 최대 20개·중복/잘못된 ID 검사를 적용한다. 페이지 관리 권한을 재사용하며 SUPPORTER 접근은 403이다.

기존 API 재사용:

- `GET /api/admin/next/posts`: 선택 창 검색/필터/페이지 이동
- `GET /api/admin/next/classifications`: 유형·기수·주제 사전
- `GET /api/admin/next/page-components`: POSTS의 sourceModes/manual/maxManualItems 등록 정보
- 기존 페이지 조회/PUT/초안 미리보기/발행본 미리보기: manual 필드와 동일 발행 조회 서비스 사용
- 기존 Thymeleaf 페이지 저장/발행: 같은 PageService 사용

주요 변경 범위:

| 영역 | 파일 | 변경 |
|---|---|---|
| 모델/블록 | CmsModels, PageBlockService, PageComponentRegistry | manual 계약, 복제 보존, 소스 등록 |
| 저장/조회 | PageService, PublishedPostQueryService | 누락 방지, 유효성, 상태/발행본 조회와 수동 순서 |
| API/Mapper | NextPageApi, CmsMapper.xml | 상태 API, 기존 발행 조회 ID 조건 |
| React | ManualPostsFields.tsx, manualPosts.ts, PostsBlockFields.tsx, types.ts, page-editor.css | 선택 창·정렬·상태·모드 보존 |
| Thymeleaf | static/js/cms.js | manual JSON 보존과 편집 안내 |
| 검증 | PostsBlockManualIntegrationTest.java, manualPosts.test.ts | 발행 경계·순서·호환·권한·복제 테스트 |
| 실행/기록 | run-posts-manual-copy.ps1, 이 문서, 설계 문서, 위험 기록 | 사본 경로 제한과 복구 기준점 |

PostService, 콘텐츠 모델/분류 사전, ContentEditor, 계정/권한 정책, V1~V8 migration은 이번 단계에서 변경하지 않았다.

## 7. 자동 테스트

Java 전체 `verify`: **110개, 실패 0, 오류 0, 제외 0**. 새 manual 통합 테스트 7개를 포함한다. 프런트 테스트 **29개 통과**, TypeScript 검사·production build 성공.

전체 검증에 새 V3/V6/V7 파일 사본을 지정해 기존 migration 회귀 검사도 실행했다. V3 → V6, V6 → V7, V7 → V8 변환/재실행/데이터 보존 검사를 제외하지 않았다.

| 사용자 필수 검증 | 결과/근거 |
|---|---|
| 1~5 저장·다중 선택·중복 차단·순서·이동 | API 통합 + 프런트 + 브라우저 통과 |
| 6~8 미발행/삭제/사용 불가 | 통합 테스트에서 모든 상태 확인. 브라우저에서 미발행·없는 ID 표시 및 결과 제외 확인 |
| 9~10 콘텐츠 발행 경계 | 초안 제목/본문은 숨김, 재발행 후 새 snapshot 반영 |
| 11~12 페이지 발행 경계 | 초안 배열/순서 변경 후 기존 발행본 유지, 페이지 재발행 후 반영 |
| 13~14 복제 | 새 block ID, 동일 배열 복사, 복제본 선택 해제/재정렬 후 원본 불변 |
| 15~18 category/query/Thymeleaf/후기·FAQ·맛집 | 기존 전체 테스트 + 실제 기존 관리자 저장/발행 통과 |
| 19 재시작 | 페이지/발행본/미리보기/상태 API 전후 완전 일치 |
| 20 브라우저 | 검색·분류·선택·모드 전환·보기 전환·순서·복제·재조회·기존 저장/발행·재시작 확인 |

추가로 중복/0/음수/21개 초과/알 수 없는 manual 속성/비POSTS 설정/필드 누락, 충돌 시 불변, CSRF·권한 검사도 통과했다.

## 8. 실제 V8 사본 검증과 데이터 보존

기존 콘텐츠를 재사용했다. ID 105(맛집), 97(후기), 101(FAQ)은 발행 상태이고 103은 미발행 FAQ다. 별도 콘텐츠/페이지/분류 사전은 생성하지 않았다. 없는 ID 999999는 참조 상태 검사에만 사용했다.

- `[105,103,97,101,999999]` 선택 → 공개 결과 `[105,97,101]`, count 3.
- 페이지 초안 `[101,97,105,103,999999]` 저장 → 발행 결과는 기존 순서 유지, 페이지 재발행 후 `[101,97,105]`.
- 97의 초안 제목 변경 → 기존 발행 제목 유지, 콘텐츠 재발행 후 변경. 검증 후 원래 제목/본문/분류로 복원·재발행.
- 브라우저 복제 블록 `[105,101,97]`과 원본 `[105,103,97,101,999999]`가 독립적으로 저장되고 재시작 후 그대로 유지됨.

완료 시 홈을 기존 HERO/POSTS 2블록과 원래 내용·ID·순서·category 연결로 복원했다. POSTS에는 명시적인 `sourceMode=category`, 비활성 `manual.postIds=[]`가 남는다. 데이터 손실 방지 규칙상 manual 필드의 조용한 누락으로 되돌리지 않았다. 기존 query는 원래 null로 복원했다. 페이지 65는 전혀 수정하지 않았다.

작업 전후 27개 테이블 비교: Flyway 이력 완전 동일. 변경 테이블은 정상 검증 저장의 SITE_PAGES/PAGE_PUBLICATIONS/POSTS/POST_PUBLICATIONS, ACTIVITY_LOG, PAGE_BLOCK_IDENTITIES뿐이다. 기존 콘텐츠 18개의 본문/분류/주소/미디어와 ID, 페이지 1·65 내용/ID, 메뉴·category·권한이 보존됐다. revision/시각/활동 이력 증가와 복제 블록 ID 폐기 기록은 남긴다.

원본 `.local-data/aica-local.mv.db` SHA-256:

`55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`

작업 전후 동일하며 계속 V3다.

## 9. 실행·복구 기준점

현재 서버: 8089, `.cache/react-phase4c2a-test.jar`, `.cache/react-phase4c2a-data/aica-phase4c2a.mv.db`. DB URL은 IFEXISTS/DB_CLOSE_ON_EXIT=FALSE, copy-validation=true, bootstrap=false, preview=true다.

기준점: `.cache/checkpoints/20260927-155742-react-phase4c2a/`

- 작업 전: baseline-v8.mv.db + baseline-4c1-runtime.jar + source.zip/manifest.json
- manual 시험 상태: verified-manual-v8.mv.db + verified-4c2a-runtime.jar
- 기존 화면 복원 완료: completed-v8.mv.db + completed-4c2a-runtime.jar + completed-source.zip/completed-manifest.json
- RESTORE.md, 체크섬, API 비교/브라우저 이미지/테스트 로그는 같은 기준점에 보존

원본이나 동작 중인 DB를 덮어쓰지 않는다. 정상 종료 후 DB/JAR 쌍을 새 검증 폴더로 복사해 복구한다. 같은 V8이라도 이전 4C-1 JAR는 manual을 이해하지 못하므로 완료 DB와 혼용하지 않는다. H2/Flyway 경고 및 과거 파일 연결 수명 문제는 DB_MIGRATION_RISKS.md의 별도 위험으로 유지한다.

## 10. 4C-2B 준비 상태

동일 페이지 ID/공통 PageEditor, 안정적인 block ID, 선택 블록 상태, 복제·폐기 ID 처리와 저장 충돌 검사가 준비되어 있다. 직접 이동은 다음 단계에서 URL의 page ID + block ID를 같은 편집기 선택 상태로 연결하면 된다. 숨김·삭제·접근 불가 블록 처리와 미저장 상태 유지도 그 단계에서 검증해야 한다. 이번에는 새 사이트 구조 트리나 block ID 딥링크를 추가하지 않았다.
