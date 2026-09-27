# 4C-1 POSTS 콘텐츠 조건 연결 결과

2026-09-27 완료. 원본 V3 DB는 그대로이며, 4B V8 검증 DB를 복제한 새 사본에서만 작업했다. DB migration·사전 등록·페이지/콘텐츠 생성은 없다.

## 확인 화면

- React: http://127.0.0.1:8088/admin-next/pages/1/edit?view=structure
- `사관학교 소식 · POSTS` 선택 → 콘텐츠 소스 → `조건으로 불러오기`
- 기존 검증 계정: 1234 / 1234
- DB: `.cache/react-phase4c1-data/aica-phase4c1.mv.db`
- 실행 파일: `.cache/react-phase4c1-test.jar`
- 재실행 스크립트: `scripts/run-posts-query-copy.ps1` (복사 DB 경로만 허용)

검증 후 홈 페이지 1과 인사교 소개 65의 기존 내용·ID·순서·표현 방식·발행 내용을 복원했다. 홈 POSTS의 기본 연결도 기존 category 방식으로 복원했다. 시험용 복제 블록은 제거하고 ID를 폐기했다. 정상 서비스로 저장·발행했으므로 revision, 수정/발행 시각, 활동 이력은 증가했다. 기존 18개 콘텐츠의 내용·분류·미디어·맛집 주소와 메뉴·카테고리는 보존했다.

## 1. 기존 구조와 신규 구조

기존 POSTS는 schemaVersion 2의 평면 JSON 배열 안에서 categoryId로 연결했다. categoryId가 null이면 전체 카테고리다. 블록의 id/visible/variation과 기타 입력은 유지한다.

추가한 필드는 다음 둘뿐이다.

```json
{
  "sourceMode": "query",
  "query": {
    "typeCode": "REVIEW",
    "cohortIds": [2],
    "topicIds": [1, 3],
    "sort": "LATEST",
    "limit": 6
  }
}
```

이 사본에서 기수 7기 ID=2, REVIEW_LIFE=1, REVIEW_PROJECT=3이다. 실제 값은 기존 사전 API에서 읽는다. FAQ_LIFE는 별도 ID=7이다.

- sourceMode 누락/null/`category`: 기존 categoryId를 사용, 최신순 6개.
- `query`: typeCode/cohortIds/topicIds 조건 사용. 기존 categoryId는 보존하지만 조회 조건에는 섞지 않는다.
- 모드 변경 시 비활성 모드의 선택도 보관한다. 카테고리를 주제로 변환하지 않는다.
- schemaVersion 2 유지. 테이블/컬럼/새 migration이 없다. V1~V8와 격리된 예전 V4 파일은 동일하다.
- POSTS Variation은 default만 유지한다.

## 2. 조회 규칙과 두 발행 경계

유형은 하나, 기수·주제는 0개 이상 선택한다. 같은 기준은 OR, 서로 다른 기준은 AND다. 미선택 기수/주제는 그 기준으로 제한하지 않는다는 뜻이다. 최신순은 **콘텐츠 발행 시각 내림차순 → 동일 시각이면 ID 내림차순**이다. 표시 개수는 1~20, 기본 6이다.

`PublishedPostQueryService`가 기존 `ClassificationService.filter`의 ID 검증과 `CmsMapper.xml`의 **publicPosts/publicPostCount/publishedClassificationFilter**를 재사용한다. SQL 및 분류 조회 Mapper를 복제하거나 새로 만들지 않았다. 유형에 허용되지 않은 주제, 중복 ID, 잘못된 정렬, 범위 밖 개수, 자유 조건 필드는 거절한다.

목록/개수는 같은 WHERE 절을 사용한다. 기수/주제는 EXISTS로 연결하므로 한 글이 여러 선택에 해당해도 중복되지 않는다. count는 전체 일치 수, items는 제한 개수다. 조회 서비스는 읽기 트랜잭션으로 목록/개수를 읽는다.

| 대상 | 사용하는 원본 |
|---|---|
| 콘텐츠 제목·본문·기존 category·유형 | post_publications |
| 콘텐츠 기수·주제 | post_publication_cohorts / post_publication_topics |
| 공개 가능 여부 | posts의 PUBLISHED 및 deleted_at IS NULL |
| 페이지 초안 미리보기 | 현재 편집 중인 sections_json 조건 × 콘텐츠 발행본 |
| 페이지 발행본 미리보기 | page_publications 조건 × 콘텐츠 발행본 |

초안 posts의 유형·기수·주제로 조회하지 않는다. 콘텐츠 재발행은 콘텐츠 결과를, 페이지 재발행은 블록 조건을 바꾼다. 동적 query이므로 페이지가 콘텐츠 자체의 복사본을 보관하지는 않는다.

## 3. React 편집/미리보기

기존 PageEditor에서 POSTS를 선택하면 소스, 유형, 기수, 주제, 정렬, 개수와 조건 결과가 나타난다. 선택 유형에 허용된 주제만 표시한다. 유형 변경으로 기존 주제가 맞지 않으면 기존 ID를 유지하고 별도 경고 및 해제 체크박스로 보여준다. 직접 해제/유형 복원 전에는 서버가 저장과 미리보기를 거절한다.

선택 블록의 조건 미리보기와 오른쪽 전체 페이지 미리보기는 동일한 preview 응답을 사용한다. 제목 목록, 발행일, 전체 개수/실제 표시 개수를 보여주며 0건은 `조건에 맞는 발행 콘텐츠가 없습니다.`라고 표시한다. 숨김 블록은 공개 미리보기에서 제외하며 표시를 켜면 조건 결과를 확인할 수 있다.

기존 자동저장·수동 초안 저장·재조회·발행 관리 진입을 재사용한다. query 전체가 기존 dirty fingerprint에 포함된다. 보기 전환은 같은 PageEditor를 유지한다. 블록 이동은 ID/query를 함께 옮기며 복제는 structuredClone + 새 UUID로 배열까지 독립 복사한다.

## 4. 기존 관리자 호환

Thymeleaf `cms.js`는 각 카드의 sourceMode/query를 보관하고 저장 payload에 포함한다. query 모드의 category 선택기는 비활성화하고 React 편집 안내를 표시한다. 실제 브라우저의 임시저장·발행본 저장 버튼을 눌러 ID/variation/sourceMode/query 보존을 검증했다.

신규 조건을 알지 못하는 오래 열린 화면이 해당 필드를 누락하면 서버가 저장을 거절한다. 값을 조용히 지우거나 기존 category 조건으로 바꾸지 않는다. 저장된 Thymeleaf 페이지 미리보기에서도 같은 서버 조회 결과를 볼 수 있다. Thymeleaf의 작성 중 미리보기는 고급 조건의 실제 결과를 React에서 확인하도록 안내한다.

## 5. API와 코드 범위

| 경로 | 변경 |
|---|---|
| GET /api/admin/next/page-components | POSTS 입력 필드 query와 지원 sourceMode/정렬/개수 범위 제공 |
| GET, PUT /api/admin/next/pages/{id} | 기존 sections 내부 sourceMode/query 조회·검증·저장 |
| GET, POST /api/admin/next/pages/{id}/preview | 기존 응답의 각 블록에 posts/total 추가 |
| **GET /api/admin/next/pages/{id}/publication/preview** | 신규 읽기 API. 페이지 발행 조건으로 동일 조회 수행 |

기존 PageService 저장·발행 트랜잭션, 권한, CSRF, revision 충돌 검사, 블록 ID 등록/폐기 처리를 그대로 사용한다. 블록별 저장 API나 새 공개 홈페이지는 만들지 않았다. 관리자 초안 콘텐츠 목록 API는 기존 역할대로 초안 필터를 유지하며, 블록은 기존 **발행본 조회 Mapper**를 사용한다.

변경 파일:

- Java: CmsModels, PageBlockService, PageComponentRegistry, PageService, NextPageApi
- 신규 Java: PublishedPostQueryService
- React: PageEditor.tsx, pageBlocks.ts, types.ts, page-editor.css
- 신규 React: PostsBlockFields.tsx
- Thymeleaf: static/js/cms.js, templates/cms/preview.html
- 검증: PostsBlockQueryIntegrationTest.java, frontend/tests/pageBlocks.test.ts
- 실행: scripts/run-posts-query-copy.ps1
- 문서: REACT_PHASE4C1_DESIGN.md, 본 문서, DB_MIGRATION_RISKS.md

PostService, 기존 classification/publication Mapper, 인증/권한 정책, 분류 DB는 변경하지 않았다.

## 6. 검증 결과

**Java 103개, 프런트 26개 통과. 실패/오류/건너뜀 0.** TypeScript 검사와 production build 통과. 기존 V3 사본/V6 사본/V7 사본 migration 테스트도 새 사본 경로로 실행했다. V8 런타임에는 추가 migration이 적용되지 않았다.

| 검증 | 결과/근거 |
|---|---|
| 기존 category POSTS | 실제 홈의 categoryId=null과 테스트 categoryId=11 모두 정상 |
| query 저장/재조회 | 동일 block ID/type/cohorts/topics/sort/limit 유지 |
| 유형·기수·주제 OR/AND | REVIEW 3개 중 7기 + 생활/프로젝트 OR=2개; 두 기수 OR + 두 주제 OR=3개, 중복 없음 |
| count/개수/정렬 | count=3, limit=1이면 items=1; 동일 발행시각 ID 역순, 날짜 우선 정렬 검증; 0/21개 거절 |
| 동일 이름 생활 격리 | 실제 사본 REVIEW_LIFE=1 → 후기 #97, FAQ_LIFE=7 → FAQ #101, 서로 섞이지 않음 |
| 맛집 | RESTAURANT → 기존 #105만 조회 |
| 초안 콘텐츠 변경 | #97 생활 발행 후 프로젝트 초안으로 바꿔도 생활 조건 유지 |
| 콘텐츠 재발행 | 재발행 후 생활에서 제외, 프로젝트에 포함 |
| 페이지 초안/재발행 | query 초안 저장 시 이전 발행 조건 유지; 페이지 재발행 뒤에만 새 조건 반영 |
| 이동/복제/독립 수정 | 브라우저 복제 새 ID, 이동 후 ID/설정 일치, FAQ로 바꾼 복제본과 기존 REVIEW query 독립 |
| 보기 전환/재조회 | 사이트 관리↔구조 뒤 선택 블록과 입력 유지, 저장 후 재조회 동일 |
| 미허용 주제 | 유형 변경 후 기존 REVIEW_PROJECT 유지·경고, 직접 해제 후 저장 가능 |
| 빈 결과 | FAQ 생활 초안만 존재하는 최종 사본에서는 0건 안내. 미발행 글을 끌어오지 않음 |
| Thymeleaf | 실제 JS 직렬화와 저장/발행 버튼으로 query/ID/variation 보존 |
| 보안/충돌 | CSRF 누락 403, SUPPORTER 페이지 preview 접근 403, stale revision 409 |
| 서버 재시작 | query 블록 2개가 포함된 DB를 완전 종료 후 재시작; page/publication/preview/all posts JSON 동일. 브라우저 선택값 재확인 |
| 회귀/데이터 | 기존 후기/FAQ/맛집 및 페이지 테스트 모두 통과. 실제 페이지 1·65/18개 글의 원래 값 복원 |

실제 사본 테스트는 #97과 #101의 이미 있던 발행본을 잠시 수정·재발행해 검사한 뒤 원래 값으로 복원했다. 원래 임시저장 글은 발행하지 않았다. 새 콘텐츠/페이지/분류는 만들지 않았다. 검증용 조건 복제 블록만 생성·폐기했다.

전체 27개 테이블 비교에서 변경된 테이블은 정상 시험 작업의 ACTIVITY_LOG, PAGE_BLOCK_IDENTITIES, PAGE_PUBLICATIONS, POSTS, POST_PUBLICATIONS, SITE_PAGES다. 나머지 테이블 및 Flyway 이력은 동일하다. 페이지 내용 및 콘텐츠 의미 값은 API 전체 비교로 별도 확인했다.

## 7. 기준점·복구·남은 항목

기준점: `.cache/checkpoints/20260927-152902-react-phase4c1/`

- 작업 전: baseline-v8.mv.db + baseline-4b-runtime.jar + source.zip/manifest/working.patch/status/head
- query 재시작 검증 상태: verified-query-v8.mv.db + verified-4c1-runtime.jar
- 기존 값 복원 후 완료 상태: completed-v8.mv.db + completed-4c1-runtime.jar + completed-source.zip
- checksums.json 및 RESTORE.md에 파일 해시와 복구 순서를 기록한다.

원본 `.local-data/aica-local.mv.db` SHA-256은 작업 전후 `55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`로 동일하다.

**4C-2 기반은 준비됐다.** 영구 block ID/선택 상태, sourceMode, 독립 복제, 공통 발행본 조회 서비스, 동일 페이지/콘텐츠 ID를 계속 사용할 수 있다. 직접 선택의 저장 형식/선택 해제된 비공개 글 처리/수동 순서와 block ID URL 탐색은 4C-2에서 설계·구현한다.

새 조건이 들어간 V8 DB를 예전 4B JAR로 실행하지 않는다. 스키마 버전이 같아도 JSON 계약이 다르므로 DB/JAR를 짝으로 복구한다. H2/Flyway 호환 경고, 이전 파일 DB 연결 수명 문제, 원본 적용 금지는 계속 유지한다. 실제 사이트를 연결할 때도 현재 발행본 조회 서비스를 사용해야 한다. 향후 사전 삭제/유형별 허용 주제 변경 UI를 추가할 때 JSON 블록의 참조 영향 검사가 필요하다.
