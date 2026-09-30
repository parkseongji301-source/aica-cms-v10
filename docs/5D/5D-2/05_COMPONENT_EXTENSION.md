# 05. 컴포넌트 확장 가이드

대상: 개발자. **향후 동결 해제 후의 개발 절차**이며 현재 컴포넌트·UI·schema를 변경하는 문서가 아니다. 운영자는 등록된 블록과 Variation을 선택한다. 임의 React 코드·HTML 실행 코드·자유 CSS를 등록하는 웹페이지 빌더가 아니다.

## 현재 정의와 저장 계약

현재 HERO/TEXT/IMAGE/POSTS/CTA를 `PageComponentRegistry`에서 등록한다. 정의에는 type, label, 설명, schemaVersion=2, 기본 Variation, 입력 필드 목록, 허용 Variation, 기본값, POSTS 기능 범위가 있다. `/api/admin/next/page-components`는 이 정의를 조회한다.

저장은 site_pages.sections_json의 배열이다. `CmsModels.Section`의 평면 필드에 id/type/schemaVersion/variation/visible/heading/body/bodyDoc/imageId/categoryId/link/label/sourceMode/query/manual 등이 있다. API 공개 응답의 `data` 계층과 혼동하지 않는다. 블록 DB 테이블은 없으며 ID 소유·은퇴 기록만 page_block_identities에 있다.

현재 HERO만 default/centered, 나머지는 default다. centered는 `page-blocks.css`의 실제 표현과 미리보기에서 검증된 선택지다. 이름만 있는 Variation을 등록하지 않는다.

## 변경 설계부터 테스트까지

1. **기존 타입/Variation으로 해결 가능한지 확인한다.** 단순 배치 차이는 의미/입력값을 바꾸지 않는 등록형 Variation을 먼저 검토한다. 콘텐츠 원문을 블록 안에 복제하는 새 타입을 만들지 않는다.
2. **입력 계약을 적는다.** 필수/선택 값, 길이·범위·URL·미디어 제약, 기본값, visible=false 동작, 공개 출력값을 정의한다. 원문 HTML이나 SQL·검색식을 허용하지 않는다.
3. **컴포넌트 정의를 등록한다.** PageComponentRegistry의 타입/필드/Variation/기본값과 서버 검증을 같이 바꾼다. Registry 추가만으로 모든 입력 UI가 자동 생성되는 것은 아니다.
4. **저장 DTO·검증을 연결한다.** `CmsModels.Section`, `PageService`, `PageBlockService`, `CmsRules`, `RichTextService`에서 필요한 부분을 검토한다. 새로운 필드가 역직렬화/정규화 과정에서 사라지지 않게 한다. 페이지 전체 저장·기존 transaction/revision을 사용하고 블록별 독립 저장 API를 만들지 않는다.
5. **React 입력 UI를 연결한다.** `types.ts`, `pageBlocks.ts`, `PageEditor.tsx`의 타입별 입력을 검토한다. ID를 key로 사용하고 deep copy로 복제한다. 빈 기본값도 저장·미리보기 검증을 통과하는지 확인한다.
6. **미리보기 규칙을 구현한다.** `NextPageApi`, `PageService.views`, React 미리보기, 기존 `cms/preview` 템플릿·CSS를 함께 확인한다. 임의 HTML을 그대로 출력하지 않는다.
7. **공개 계약을 정의한다.** `PublicDocuments`, `PublicSiteService`의 안전한 DTO 투영을 추가하고 공개 schema/type/variation 처리 정책을 적는다. 공개 홈페이지는 아직 없으므로 실제 renderer와 시각 E2E는 후속으로 남긴다.
8. **템플릿·history·미디어를 검토한다.** 새 입력값이 템플릿 저장/적용, page/template version snapshot, 복구 비교, 사용처와 공개 미디어 참조에 보존되는지 확인한다. 숨김 블록과 비활성 템플릿/과거 버전도 삭제 보호에서 놓치지 않는다.
9. **Thymeleaf 호환을 검증한다.** 현재 `static/js/cms.js` 등의 직렬화/기존 폼이 신규 필드를 조용히 버리지 않아야 한다. 편집할 수 없는 기능은 React 안내와 값 보존으로 처리한다.
10. **테스트·release 승인을 거친다.** 기존 RC·migration을 수정해 원본에 끼워 넣지 않는다. 새 release와 사본 리허설을 사용한다.

## ID·schemaVersion·migration 판단

기존 블록의 내용/순서/Variation 변경은 같은 ID다. 새 블록·복제·템플릿 적용은 `block_`+UUID v4이며 은퇴 ID를 재사용하지 않는다. 과거 복구는 현재 살아 있는 동일 ID만 유지하고 삭제 블록에 새 ID를 준다. 내용·배열 위치로 ID를 추정하지 않는다.

현재 문서는 배열 그대로이며 각 블록에 schemaVersion=2를 둔다. 의미·직렬화가 호환되지 않는 변경이면 새 버전 읽기/쓰기·변환 규칙을 먼저 설계한다. Registry가 현재 버전을 엄격 검사하므로 숫자만 올리면 과거 publication·template·version이 깨질 수 있다.

| 변경 | 판단 기준 |
|---|---|
| 기존 필드만 쓰는 실제 Variation | DB DDL이 반드시 필요한 것은 아님. 허용값·미리보기·공개·템플릿·이력 회귀 필요 |
| JSON에 선택 필드 추가 | 기존 누락값 기본 의미와 모든 reader 보존 확인. 저장 형태·history 영향에 따라 데이터 변환 필요 |
| 기존 필드 의미 변경/중첩 구조 변경 | 구버전 snapshot 보존·변환과 schemaVersion 전략 필수 |
| 새 테이블/제약 또는 영구 데이터 변환 | 새 V13 이상 migration, 사본 검증·별도 승인. V1~V12 수정 금지 |

예전 publication이나 version을 새 코드 편의를 위해 일괄 덮어쓰지 않는다. 고정 snapshot을 읽는 호환 코드를 먼저 검토한다. 자세한 DB 절차는 [migration 가이드](09_DATABASE_MIGRATIONS.md)를 따른다.

## 최소 회귀 체크

- 기존 page 1·65의 ID/본문/순서 보존, 새 타입의 기본값·경계값·잘못된 값 거절.
- 추가·복제·이동·삭제·visible·Variation 저장/재조회와 서로 독립적인 배열/객체.
- 초안 변경으로 페이지 공개본 불변, 발행 후 새 블록 표현 반영.
- category/query/manual POSTS 설정 보존, manual 원문 복사 없음.
- 템플릿 적용마다 새 ID, 이전 적용 페이지 불변.
- 자동저장 이력 미생성, 수동/발행 버전·복구·미디어 보호.
- Thymeleaf 저장/발행 후 고급 필드 유지, ADMIN/SUPER_ADMIN/SUPPORTER 권한.
- 브라우저 보기 전환/딥링크/새로고침, 서버 정상 종료·재시작 지속성.

근거: [Registry](../../../src/main/java/egovframework/backoffice/mvp/cms/PageComponentRegistry.java), [Section DTO](../../../src/main/java/egovframework/backoffice/mvp/cms/CmsModels.java), [블록 서비스](../../../src/main/java/egovframework/backoffice/mvp/cms/PageBlockService.java), [React 블록 조작](../../../frontend/src/pageBlocks.ts), [공개 DTO](../../../src/main/java/egovframework/backoffice/mvp/publicapi/PublicDocuments.java).
