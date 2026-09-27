# 4A 블록 ID·저장 기반 완료 기록

2026-09-27. 원본 V3 DB는 변경하지 않았다. 최신 V7 맛집 검증 DB를 새로 복제해 V8을 적용했다. 검증 주소는 http://127.0.0.1:8086/admin-next/pages/65/edit?view=structure 이다. 로그인은 기존 개발 계정 1234 / 1234다.

## 1. 기존 sections_json과 실제 페이지

`site_pages.sections_json`, `page_publications.sections_json`은 JSON **배열**을 CLOB에 저장한다. 페이지 초안의 키는 `site_pages.id`, 발행본의 키는 `page_publications.page_id`다. 별도 발행본 ID를 새로 만들지 않는다.

| 항목 | 기존 방식 |
|---|---|
| 섹션 식별 | 저장된 ID 없음. 배열 위치와 클라이언트 메모리/DOM 임시 키 사용 |
| 종류 | HERO / TEXT / IMAGE / POSTS / CTA |
| 입력 | type, heading, body, bodyDoc, imageId, categoryId, link, label |
| 본문 서식 | bodyDoc에 Quill Delta JSON 문자열, body에 일반 텍스트 |
| 표시 | visible boolean |
| 순서 | 배열 순서 |
| 초안 저장 | PageService가 검증 후 site_pages JSON과 revision 갱신 |
| 발행 | 기존 publishPage가 현재 초안 JSON과 revision을 page_publications에 복사 |
| React | NextPageApi의 sections 배열을 공통 PageEditor가 읽고 저장 |
| Thymeleaf | 같은 PageService에 sectionsJson 폼 필드를 전달 |

작업 전 최신 V7 사본의 실제 값:

- 페이지 **1**, `home`, 제목 **홈**, revision/publishedRevision **1/1**. **HERO → POSTS** 2개. bodyDoc 키는 원문에 없었다.
- 페이지 **65**, `about`, 제목 **인사교 소개**, revision/publishedRevision **4/4**. **HERO** 1개. heading/body/label은 `테스트`, bodyDoc은 `{"ops":[{"insert":"테스트\n"}]}`, link는 기존 `http://127.0.0.1:8081/admin/pages/65/edit`였다. 이 문구와 링크를 재작성하지 않았다.
- 두 페이지 모두 초안과 발행본의 revision 및 JSON이 같았다. 원문과 모든 테이블 지문은 `.cache/react-phase4a-baseline.json`에 기록했다.

코드 근거: `CmsModels.Section`, `PageService.sections/validated/save`, `NextPageApi`, `mapper/CmsMapper.xml`의 `publishPage`, `frontend/src/PageEditor.tsx`, `static/js/cms.js`.

## 2. 새 저장 구조와 버전

기존 배열과 기존 데이터 필드를 유지하고 블록마다 메타데이터 3개를 추가했다.

```json
[
  {
    "id": "block_9a49edcb-e9e6-4891-860d-28fa2eb88d1c",
    "schemaVersion": 2,
    "type": "HERO",
    "variation": "default",
    "heading": "테스트",
    "body": "테스트",
    "bodyDoc": "{\"ops\":[{\"insert\":\"테스트\\n\"}]}",
    "imageId": null,
    "categoryId": null,
    "link": "http://127.0.0.1:8081/admin/pages/65/edit",
    "label": "테스트",
    "visible": true
  }
]
```

기존 형식을 암묵적 v1, 새 블록을 v2로 취급한다. 문서 envelope나 별도 정렬 컬럼은 추가하지 않았다. 블록별 version은 이후 등록 컴포넌트/Variation의 변환 기준이 된다. 지금은 기존 5종 type과 `variation=default`만 허용한다. 지원하지 않는 version, variation, 입력 필드는 저장을 거절해 조용히 사라지는 일을 막는다.

## 3. ID 발급·유지·폐기 규칙

- Java `UUID.randomUUID()`, React/Thymeleaf `crypto.randomUUID()`로 `block_<UUID v4>`를 발급한다. 내용, 페이지명, 배열 위치에서 계산하지 않는다.
- 내용·표시·순서 변경 시 기존 ID를 유지한다. React의 수정/비동기 이미지 업로드 대상도 ID로 찾는다.
- 신규 블록과 복제 데이터에는 새 ID를 발급한다. 서버/React 복제 유틸만 검증했으며 복제 버튼이나 새 복제 API는 만들지 않았다.
- 같은 배열 안 중복 ID, 다른 페이지 소유 ID, 삭제된 ID, 현재 초안에 없는 과거 ID의 재사용을 거절한다.
- ID 소유/폐기 이력용 테이블을 추가했다. 내용은 계속 sections_json에만 저장한다.

```sql
page_block_identities (
  block_id VARCHAR(42) PRIMARY KEY,
  page_id BIGINT REFERENCES site_pages(id) ON DELETE SET NULL,
  retired BOOLEAN NOT NULL DEFAULT FALSE
)
```

블록 삭제 시 `retired=true`, 페이지 삭제 시 소유 페이지 FK만 null로 바뀌며 기록은 남는다. 따라서 이후 삭제 ID를 다른 블록에 다시 등록할 수 없다. JSON과 이력은 기존 PageService의 CMS lock·revision 검사·트랜잭션 안에서 함께 처리한다.

## 4. 기존 ID 부여와 migration

`src/main/java/db/migration/h2/V8__page_block_identity.java`를 추가했다. V1~V7 및 격리된 예전 미완성 V4는 수정하지 않았다. 사전 설계는 `REACT_PHASE4A_DESIGN.md`다.

1. 모든 초안/발행본의 JSON과 지원 형식, 중복 ID를 먼저 검사한다.
2. ID 없는 블록에 UUID를 부여하고 원래의 JSON 필드·값은 그대로 둔다. 키 순서/공백의 직렬화는 달라질 수 있다.
3. 같은 페이지의 **revision과 전체 JSON이 모두 동일한 경우** 기존 발행 처리로 복사된 동일 snapshot으로 판단해 양쪽에 같은 ID를 부여한다. 실제 페이지 1·65가 이에 해당한다.
4. 서로 다른 revision이나 문서는 과거 대응을 추측하지 않고 각각 식별한다. 이후 재발행부터 초안 ID를 발행본에 복사한다. 기존 이력 없이 모든 과거 블록의 동일성을 소급 복원할 수는 없다.
5. 이미 v2인 블록은 ID를 유지한다. 반복 변환과 Flyway 재실행, 완전 재접속 후에도 동일함을 검증했다.

검색 경로는 기존 `classpath:db/migration/h2`다. 실행 JAR에는 SQL V1~V7과 Java V8 클래스가 함께 들어 있다. 실제 Flyway 이력은 1~8 성공, V8 `JDBC`, script `db.migration.h2.V8__page_block_identity`, checksum `804202609`다. V1~V7 파일 해시와 패키지 SQL 일치를 확인했다.

H2 DDL의 암묵적 commit 때문에 비정상 데이터 검사는 DDL 전에 한다. 예기치 않은 적용 실패 시 자동 repair하지 않고 DB/JAR 쌍으로 복구한다.

## 5. 초안·발행본과 양쪽 편집기

초안 수정은 site_pages와 ID 이력에만 반영된다. 발행본의 ID·내용·순서는 재발행 전까지 그대로다. 재발행은 기존 트랜잭션의 `publishPage`로 초안 전체를 복사한다. 삭제한 블록이 기존 발행본에 남아 있어도 그 snapshot은 수정하지 않는다.

React/Thymeleaf 모두 현재 ID/version/variation을 제출한다. ID 없는 구버전의 기존 페이지 저장은 다시 열도록 오류를 반환한다. 배열 위치로 임의 복구하지 않는다. 새로운 페이지 생성의 기존 서버 흐름에는 ID 없는 **신규** 블록을 서버에서 식별하는 호환 처리를 두었다.

기존 React 조회·초안 저장·미리보기 URL과 sections 배열 계약을 유지했다. 미리보기 응답에도 블록 ID가 포함된다. 추가 API는 읽기 전용 **GET `/api/admin/next/pages/{id}/publication`** 하나다. 기존 manager 권한 검사를 재사용하고 발행 API나 발행 정책은 추가하지 않았다.

사이트 관리/사이트 구조는 계속 같은 PageEditor와 페이지 ID를 사용한다. 기존 자동저장 정책은 변경하지 않았다.

## 6. 변경 파일 범위

| 영역 | 변경 |
|---|---|
| Java 신규 | V8__page_block_identity, PageBlockService, PageBlockMapper |
| Java 수정 | CmsModels.Section 메타데이터, PageService ID 검증/동기화, NextPageApi 미리보기 ID·발행본 조회 |
| Mapper 신규 | mapper/PageBlockMapper.xml (조회·등록·폐기) |
| React 신규 | pageBlocks.ts (발급·수정·이동·복제 유틸), pageBlocks.test.ts |
| React 수정 | types.ts, PageEditor.tsx (저장 ID로 렌더링·수정·업로드 대상 식별) |
| Thymeleaf 연계 | static/js/cms.js (메타데이터 보존·신규 ID 발급). 템플릿/컨트롤러 중복 작성 없음 |
| Java 테스트 신규 | PageBlockMigrationTest, PageBlockWorkflowIntegrationTest |
| 기존 테스트 수정 | BackofficeIntegrationTest, ClassificationMigrationTest, NextAdminIntegrationTest, NextPostIntegrationTest, NextWorkspaceIntegrationTest, RestaurantMigrationTest |
| 실행/문서 | scripts/run-page-block-copy.ps1, 4A 설계·결과 문서, DB_MIGRATION_RISKS.md |

기존 테스트는 구형 ID 없는 편집 payload를 현재처럼 조회한 ID를 보존해 제출하도록 수정했고, 단계별 migration 테스트는 해당 target을 유지했다. 기대값을 완화하거나 기존 사본 검사를 제외하지 않았다. 기존 미추적/수정 파일과 다른 단계의 소스는 보존했다.

## 7. 데이터 보존과 테스트 결과

| 검사 | 결과 |
|---|---|
| V7 새 사본 → V8 | 최초 적용 1회, 다음 migrate 0회 |
| 내용 비교 | 기존 **26개 테이블**의 모든 기존 컬럼 지문 보존. sections_json은 새 3개 메타데이터만 제외하고 기존 필드/값 전체 비교 통과 |
| 페이지/발행본 | 페이지 1·65 ID, 발행본 page_id, revision/날짜, 본문·서식·미디어/카테고리 연결 보존 |
| 반복 변환/파일 재접속 | 같은 ID·내용·등록 기록 유지 |
| 실제 Spring 종료/재시작 | 초안·발행본·블록 ID 및 기존 18개 콘텐츠 조회값 동일 |
| Java 전체 | **95개 통과, 실패 0, 오류 0, 제외 0** |
| 실제 파일 사본 테스트 | V3→V6, V6→V7, V7→V8 모두 실행 |
| React | **23개 통과**, TypeScript/Vite build 성공 |
| 서버 ID 검증 | 신규/복제·중복·삭제 ID/다른 페이지 ID 재사용 차단, 잘못된 version/variation/필드 차단 |
| 트랜잭션/보안 | 발행 실패 시 JSON+등록 이력 rollback, revision 충돌 409, CSRF 403, SUPPORTER 권한 403 |
| 브라우저 React | 페이지 1 내용·순서 변경/저장/재조회/미리보기, 페이지 65 새 블록 생성·이동·재조회, 모드 전환 시 입력 유지 |
| 브라우저 Thymeleaf | 동일 ID로 저장 성공, 초안 저장 동안 발행본 유지, 재발행 후 새 순서·내용·ID snapshot 일치 |
| 기존 콘텐츠 회귀 | 후기/FAQ/맛집 포함 전체 자동 검사 통과. 실제 기존 18개 콘텐츠와 주요 발행본 조회값 동일, 브라우저 맛집 105 주소·이미지·분류 확인 |
| 원본 V3 | 작업 전후 SHA-256 동일. 원본 migration 없음 |

실제 보존된 ID:

- 페이지 1 HERO: `block_b6fa6085-ae9d-4e3b-b00b-ec54f7ecee32`
- 페이지 1 POSTS: `block_baafe12a-15d8-481b-b809-501e56203685`
- 페이지 65 HERO: `block_9a49edcb-e9e6-4891-860d-28fa2eb88d1c`

브라우저 검증 뒤 테스트 문구·순서는 기존 모습으로 복원했고 추가 시험 블록은 제거했다. 해당 ID는 폐기 기록에 남겼다. 이 UI 검증의 정상 저장/발행으로 최종 사본의 revision과 활동 이력은 증가했다. **migration 자체가 revision을 바꾼 것은 아니다.**

증거: `.cache/react-phase4a-runtime-migration.json`, `react-phase4a-pre-edit-api-audit.json`, `react-phase4a-browser-*.json`, `react-phase4a-restart-audit.json`, `react-phase4a-final-verify2.log`, `react-phase4a-code-audit.json`. 화면은 `react-phase4a-browser-blocks.png` 및 복원 후 `react-phase4a-final-page65.png`.

## 8. 복구 기준점과 남은 범위

기준점: `.cache/checkpoints/20260927-140345-react-phase4a/`.

- 작업 전 `baseline-v7.mv.db` + `baseline-v7-runtime.jar` + `source.zip`/manifest/history.bundle.
- 완료 `completed-v8.mv.db` + `completed-v8-runtime.jar` + `completed-source.zip`/manifest와 검증 증거.
- 상세 복구 순서와 해시는 해당 기준점의 `RESTORE.md`, `completed-checksums.json`에 기록했다.

원본 `.local-data/aica-local.mv.db`의 SHA-256은 `55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`로 유지됐다. 원본 경로 차단 설정과 이전 V4 격리는 유지한다. V8 DB를 구버전 V7 JAR로 편집하면 메타데이터를 잃을 수 있으므로 **반드시 DB와 JAR를 짝으로 복구**한다.

4B의 추가·삭제·복제·순서 변경·블록 직접 이동 UI를 만들기 위한 식별 기반은 마련됐다. 이후 작업은 저장된 ID를 이용하면 된다. 4A에는 DnD, 복제 버튼, 직접 이동 UI, Variation 편집, 새 컴포넌트·콘텐츠 조건·목록 블록·템플릿을 추가하지 않았다.

4B에서는 등록 컴포넌트/Variation 계약과 기존 화면의 편집 동작을 구체화해야 한다. 지원 Variation은 현재 default뿐이며 다른 모양의 데이터/렌더러는 미구현이다. 복구나 undo가 삭제된 블록을 새로 만드는 경우에는 새 ID를 발급해야 한다. 과거 revision 간 대응 불확실성은 유지하고 유사도로 연결하지 않는다.

H2/Flyway 호환 경고와 이전 파일 DB 연결 수명 문제는 해결된 것으로 처리하지 않았다. 원본 적용 전 별도 검증 항목이며 `DB_MIGRATION_RISKS.md`에 남겼다. 인터뷰 구조화 필드는 실제 시안에서 필요가 확인될 때 확장한다.
