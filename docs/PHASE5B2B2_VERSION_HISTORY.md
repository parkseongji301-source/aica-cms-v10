# 5B-2B-2 버전 이력·복구 구현 결과

2026-09-27. 최신 V9 검증 DB의 새 사본에서 V10을 적용했다. 원본 V3에는 연결하거나 migration하지 않았다. 기존 작업 파일은 변경 전 source.zip/manifest/working.patch로 보존했다.

검증 화면: http://127.0.0.1:8094/admin-next/pages/65/edit?view=structure
실행 DB: `.cache/phase5b2b2-data/aica-phase5b2b2-completed.mv.db`
실행 JAR: `.cache/phase5b2b2-test.jar`
복구 기준점: `.cache/checkpoints/20260927-195745-phase5b2b2/`

## 1. 실제 DB 구조

V10__document_versions.sql은 다음 7개 테이블만 추가한다. 기존 V1~V9와 격리된 과거 미완성 V4는 수정하지 않았다.

| 테이블 | 용도 |
|---|---|
| post_versions / page_versions / page_template_versions | 대상 FK + 불변 JSON snapshot |
| post_version_media / page_version_media / page_template_version_media | version FK + media FK, 삭제 보호 |
| version_baseline_runs | 도입 기준점 작업의 전체 완료를 한 번만 기록 |

각 버전은 ID, 대상 ID, snapshot_schema_version=1, snapshot_json, reason, source_revision, source_version_id, operation_id, 생성자 ID/당시 이름, 생성 시각, activity_log_id를 가진다. page snapshot 내부 블록의 schemaVersion=2와 history snapshot schema version=1은 서로 다른 버전이다.

snapshot을 갱신하는 UPDATE API/Mapper는 없다. 현재 초안·publication 변경은 과거 JSON을 수정하지 않는다. 운영 DB 관리자에 의한 직접 SQL 변경까지 금지하는 DB 트리거는 두지 않았다.

대상 삭제 시 버전은 함께 제거한다. 페이지/템플릿 물리 삭제는 FK cascade, 기존 콘텐츠 삭제 처리에서는 같은 트랜잭션에서 버전을 명시적으로 제거한다. 콘텐츠의 기존 deleted_at 방식은 유지하며 휴지통이나 삭제 취소를 추가하지 않았다.

## 2. saveIntent

- React 콘텐츠/페이지 자동저장: AUTOSAVE. 현재 초안만 저장한다.
- React 콘텐츠/페이지의 초안 저장: MANUAL_DRAFT. 변경이 없어도 버전이 생성된다. 같은 정규화 내용이면 revision을 추가 증가시키지 않는다.
- Thymeleaf writing.js: 자동/수동 intent를 FormData에 명시한다. 일반 폼도 hidden MANUAL_DRAFT를 보낸다.
- React 템플릿 저장/수정: MANUAL_DRAFT를 명시한다. 템플릿 자동저장은 없다.
- 누락 요청은 경고 로그를 남기고 호환 저장한다. 초안 버전은 생성하지 않는다. 누락을 MANUAL_DRAFT로 추측하지 않는다.
- 발행 요청은 서버가 PUBLISH로 기록한다. saveIntent 누락 호환 규칙이 명시적인 발행 기록까지 없애지는 않는다. 클라이언트가 PUBLISH를 saveIntent로 임의 지정하는 요청은 거절한다.

## 3. 생성·보관 정책

| 이유 | 보관 |
|---|---|
| BASELINE_DRAFT / BASELINE_PUBLISHED / BASELINE_TEMPLATE | 자동 정리에서 제외 |
| PUBLISH | 모두 보관 |
| MANUAL_DRAFT / RESTORE_BACKUP / RESTORE | 대상별 합산 최근 20개 |

템플릿도 명시적 저장·복구 계열 합산 최근 20개다. 설정은 `backoffice.versions.post-draft-limit`, `page-draft-limit`, `template-limit`; 환경변수는 `BACKOFFICE_POST_DRAFT_VERSIONS`, `BACKOFFICE_PAGE_DRAFT_VERSIONS`, `BACKOFFICE_TEMPLATE_VERSIONS`다. 기본값은 모두 20이며 복구 보관 쌍 때문에 최소 2를 검증한다.

정리는 새 버전 생성 트랜잭션 안에서 수행한다. version-media는 FK cascade로 함께 제거되며 실제 파일은 자동 삭제하지 않는다. 보관 정책으로 출처 버전이 사라질 수 있어 source_version_id는 강제 FK 대신 과거 식별 기록으로 유지한다.

## 4. 최초 baseline

schema migration에는 데이터 snapshot 생성을 넣지 않았다. SUPER_ADMIN 세션 + CSRF + confirmed=true로 `POST /api/admin/next/version-baseline`을 별도 실행한다. CMS 잠금과 단일 트랜잭션 안에서 모든 대상을 캡처하고 완료 마커를 남긴다.

이번 사본의 생성 결과: 콘텐츠 21개(초안 18 + 발행본 3), 페이지 4개(초안 2 + 발행본 2), 템플릿 2개, 합계 27개. 재시작 후 재실행 결과는 모두 0개다. 이후 새로 생성한 문서에도 baseline을 반복 생성하지 않는다. 화면에는 ‘도입 기준점’으로 표시하며 이전 과거 이력으로 표현하지 않는다.

## 5. snapshot 범위

| 대상 | 보관/복구 내용 | 유지하는 정체성·외부 상태 |
|---|---|---|
| 콘텐츠 | title/content/richContent, category ID/비교용 이름, type/cohorts/topics 및 당시 이름, 미디어 ID·순서·비교용 메타데이터, RESTAURANT 주소 | post ID, 원저자, 생성 시각, 현재 공개 상태, publication |
| 페이지 | 제목, slug 비교값, sections 전체, block ID/schemaVersion/type/variation/visible/bodyDoc, category/query/manual 설정 | page ID, 현재 slug, 현재 공개 상태, publication |
| 템플릿 | 이름·설명·활성 여부·블록 전체 설정 | template ID, 기존 적용 페이지 |

미디어 복구는 ID와 순서·관계를 복구한다. 과거 파일명/alt는 비교용 snapshot이며 공유 미디어의 현재 메타데이터를 전역으로 되돌리지 않는다. 템플릿 snapshot에는 페이지 block ID를 저장하지 않는다. manual POSTS에는 콘텐츠 ID만 보관하며 콘텐츠 본문을 함께 복제하지 않는다.

## 6. 복구와 block ID

복구 요청은 현재 expectedRevision을 필수 검사한다. 동시 변경이면 409로 끝나며 덮어쓰지 않는다.

1. 대상·버전·역할·revision 검사
2. 현재 초안 RESTORE_BACKUP 생성
3. 선택 snapshot을 기존 저장 서비스로 검증하고 새 초안으로 저장
4. revision 증가 및 sourceVersionId가 있는 RESTORE 생성
5. 같은 트랜잭션으로 commit

중간 실패 시 초안/publication/backup/history 모두 rollback한다. publication과 공개 상태는 건드리지 않는다. 다시 발행했을 때만 공개 결과가 변경된다. UUID operationId를 같은 대상·출처로 재전송하면 중복 복구하지 않는다.

현재 초안에도 있는 동일 page block ID는 유지한다. 현재 없는 과거 블록은 새 UUID block ID를 발급한다. 삭제된 ID는 은퇴 상태를 유지한다. 다른 페이지 소속·등록 기록 없음·중복·잘못된 ID는 복구를 차단한다. 내용/순서로 대응 블록을 추측하지 않는다. 응답은 바뀐 ID 대응표를 제공하며 이전 딥링크를 조용히 재지정하지 않는다.

템플릿의 ‘작업본으로 불러오기’는 조회/확인 단계다. ‘복구 내용 저장’에서만 서버 변경과 RESTORE_BACKUP/RESTORE를 생성한다. 기존 적용 페이지는 변경되지 않는다.

## 7. 미디어와 영구 삭제

기존 초안/발행본/템플릿 보호에 세 version-media 관계를 추가했다. 숨김 페이지 블록과 비활성 템플릿의 과거 버전도 보호한다. 사용처에 대상 ID·버전 ID·이유와 React 이력 링크를 표시한다. 권한 없는 대상의 이름/이력 링크는 노출하지 않는다.

과거 버전만 참조하는 파일도 영구 삭제가 차단된다. 마지막 보호 버전이 보관 정리로 제거되고 다른 참조가 없으면 삭제 가능하다. 대상 영구 삭제 확인 화면에는 함께 지워지는 버전 수와 관련 미디어 목록을 보여준다. 버전 제거가 파일 자동 삭제를 뜻하지 않는다고 표시한다.

이력 전용 파일 URL은 해당 대상/버전 조회 권한과 version-media 관계를 모두 확인한다. 인증 없이 이력이나 이력 전용 파일을 조회할 수 없다.

## 8. 권한

| 역할 | 콘텐츠 이력 | 콘텐츠 복구 | 페이지 조회/복구 | 템플릿 조회/복구 |
|---|---|---|---|---|
| SUPPORTER | 본인만 | 차단 | 차단 | 차단 |
| ADMIN | 전체 | 허용 | 허용 | 차단 |
| SUPER_ADMIN | 전체 | 허용 | 허용 | 허용 |

기존 세션/CSRF/현재 계정 정책을 재사용한다. UI뿐 아니라 서버가 대상 소유권·역할을 검사한다. 복구는 발행 권한을 우회하는 API가 아니다.

## 9. React UI와 Thymeleaf

ContentEditor/PageEditor/공용 템플릿 목록에 공통 VersionHistoryDialog를 연결했다. 20개 단위 목록 → 선택 → 현재/과거 비교 → 확인 → 복구 흐름이다.

- 콘텐츠: 제목, 현재/과거 본문, 기존 분류·복수 분류, 주소, 미디어 순서 확인.
- 페이지: 블록 순서·ID·타입·숨김·Variation·POSTS 설정과 전체 설정 확인.
- 템플릿: 현재/과거 구성 비교 및 명시적 복구 저장, 저장 전 닫기 보호.
- 저장 중/미저장 상태의 이력 진입을 보호한다. 비교 중 편집기 자동저장을 멈춘다. 복구 완료 후 서버 원본을 재조회해 같은 공통 편집기에 반영한다.
- 복구 충돌은 최신 상태 재비교를 제공한다. 복구 성공 후 화면 재조회만 실패하면 복구를 반복하지 않고 재조회할 수 있다.
- `?history={versionId}`는 해당 버전을 연다. 기존 Thymeleaf에는 `history=all`로 같은 대상 React 이력을 여는 링크만 추가했다.
- 과거 페이지 전체 홈페이지 렌더링은 구현하지 않았다. 페이지/템플릿은 블록 구성 비교이며 POSTS가 과거에 조회했던 모든 콘텐츠 결과까지 보관한 것은 아니다.

## 10. API·서비스·publication·활동 이력

공통 경로의 `{kind}`는 posts/pages/page-templates 등록값만 허용한다.

| API | 용도 |
|---|---|
| GET `/api/admin/next/{kind}/{id}/versions?page=0` | 이력 목록·조회/복구 가능 여부 |
| GET `/api/admin/next/{kind}/{id}/versions/{versionId}` | snapshot + 현재 상태 + expectedRevision용 revision |
| POST `.../versions/{versionId}/restore` | expectedRevision/confirmed/operationId로 복구 |
| GET `.../versions/{versionId}/preview` | 콘텐츠 현재/과거 본문 비교용 HTML |
| GET `.../versions/{versionId}/media/{mediaId}` | 권한과 버전 참조 확인 후 파일 |
| POST `/api/admin/next/page-templates/{id}/versions/{versionId}/prepare-restore` | 읽기 전용 복구 준비 |
| POST `/api/admin/next/version-baseline` | SUPER_ADMIN 1회 기준점 생성 |

기존 PostService/PageService/PageTemplateService, 분류·주소·미디어·블록 검증, CMS 잠금과 트랜잭션을 사용한다. 새 version 패키지는 snapshot/보관/복구/사용처만 담당한다. 기존 publication 테이블과 공개 API는 history를 조회하도록 변경하지 않았다.

활동 이력은 작업 기록이며 snapshot 저장소가 아니다. version의 activity_log_id로 생성 작업을 연결하고, 활동 상세에는 대상·reason·revision·복구 출처 ID를 남긴다. 버전을 정리해도 활동 기록은 유지된다.

## 11. migration·자동·브라우저·재시작 검증

최종 Surefire 결과 합계: **155개, 실패 0, 오류 0, 제외 0**. 전체 실행 후 새 테스트의 빈 페이지 발행 fixture를 수정했고 VersionHistoryIntegrationTest 15개를 재실행해 모두 통과했다. 나머지 전체 회귀 결과와 합산한 최종 XML 기준이다. 프런트 테스트 **39개 통과**, TypeScript/Vite 빌드와 JAR 패키징 통과.

migration 결과:

- 빈 DB 초기화 V1~V10, 기존 V9 사본 → V10: 성공.
- 원본 V3의 새 파일 사본 → V4~V10: 7개 적용, 이력 V1~V10 확인.
- 별도 실제 V3→V6, V6→V7, V7→V8, V8→V9 사본 테스트도 제외 없이 실행.
- V9→V10 기존 모든 컬럼 fingerprint 동일, version/baseline은 DDL 직후 0건.
- V3 전체 경로에서는 V8이 추가한 id/schemaVersion/variation만 제외해 기존 페이지/발행본 내용·나머지 필드를 비교했다. 기존 ID/category/메뉴/분류/콘텐츠/미디어 보존.
- 재실행 migration 0건, DB 재연결/서버 재시작 후 V10 검증 성공.
- 기존 migration 소스 10개(검증용 stage0 포함) checksum 동일. 원본 SHA-256: `55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`.

주요 자동 검증:

| 요구 | 검증 |
|---|---|
| autosave/수동/누락 intent | HTTP 요청으로 이력 수, 같은 내용 revision 유지, 임의 intent 거절 |
| 보관 | 23회 초안 저장 후 20개, PUBLISH/baseline 보존, 템플릿 20개 |
| snapshot | richContent, 복수 분류, 첨부, 맛집 주소 복구 및 publication 독립 |
| page | 전체 구성, 살아 있는/삭제된 ID, slug 유지, foreign/missing ID 차단 |
| POSTS/template | category/query/manual, hidden/centered, 적용 페이지 독립 |
| 원자성/충돌 | DB 제약으로 manual/publish/restore 기록 실패를 주입해 rollback, stale revision, 동일 operation 재시도 |
| 미디어 | 콘텐츠/페이지/비활성 템플릿의 과거 버전 보호, prune 후 참조 해제, 대상 삭제 후 버전 제거 |
| 권한/호환 | SUPPORTER 본인 조회·복구 차단·타인 차단, ADMIN 전체 콘텐츠/페이지, SA 템플릿, Thymeleaf 콘텐츠/페이지 manual/publish 기록 |
| 기존 기능 | 후기/FAQ/맛집, block/query/manual/template, 공개 API, 기존 운영 정책 회귀 |

브라우저 검증:

- page 65 제목 변경 → 자동저장: 버전 2개 유지, publication 불변.
- 같은 내용 수동저장: 버전 3개, revision 20 유지.
- 기준점 복구: 원래 제목/블록/ID, RESTORE_BACKUP+RESTORE, publication 불변.
- post 97 제목·주제 변경 및 사이트 관리↔구조 전환 → 자동/수동저장 → 기준점 복구. 제목/본문/richContent/분류/미디어/원저자/공개 상태 보존, 공개 API 불변.
- template 2 이름 변경 → 수동 버전 → 과거 구성 준비 → 닫기 보호 → 복구 저장. 세 POSTS 방식·숨김·Variation 유지, page 1·65 불변.
- 재시작 후 문서·publication·버전 ID/메타데이터 전체 동일. baseline 재실행 0개. 이력 딥링크와 비교 화면 재조회 성공.

완료 사본에는 위 검증의 이력 9개가 baseline 27개와 함께 남아 있다. page 65/post 97/template 2의 편집 내용은 기준점으로 복구했고 revision/수정 시각/이력은 시험 작업을 정직하게 기록한다. 기존 테이블 중 변한 것은 해당 초안들의 POSTS/SITE_PAGES/PAGE_TEMPLATES와 ACTIVITY_LOG뿐이며 나머지 기존 테이블 fingerprint 및 모든 발행본은 동일하다.

증거: `.cache/phase5b2b2-test-summary.json`, `phase5b2b2-final-tests.log`, `phase5b2b2-history-final.log`, `phase5b2b2-preservation.json`, `phase5b2b2-before-restart.json`, `phase5b2b2-restart.log`, `phase5b2b2-evidence/`.

## 12. 5C 전 남은 위험과 복구

- H2 2.3.232 / Flyway 지원 경고는 재시작 로그에도 남는다. 해결로 처리하지 않았다.
- 파일 DB 연결 수명 문제도 별도 위험이다. 이번 정상 종료·재시작 성공이 과거 문제의 원인 해결을 의미하지 않는다.
- 원본 V3와 검증 V10 차이는 커졌다. 원본 migration은 여전히 금지하고, 5C에서 최신 원본을 다시 백업·사본 검증·전환 승인해야 한다.
- 실제 홈페이지가 없어 공개 화면 렌더링 E2E는 미검증이다. publication API 불변/재발행 흐름까지만 검증했다.
- 삭제/비활성 분류, 제거된 컴포넌트 정의, 사용할 수 없는 category 등은 과거 snapshot 복구 시 현재 검증 규칙에서 거절될 수 있다. 값을 조용히 지우거나 추측 변환하지 않는다.
- 모든 발행 버전과 baseline은 무기한 보관하므로 DB와 보호 미디어 용량이 증가한다. 운영 DB 용량·백업 주기는 5C 전에 확인한다.
- 역사적 POSTS 결과나 연결 콘텐츠 자체의 과거 상태를 한꺼번에 복구하지 않는다. 블록 설정은 복구하고 콘텐츠는 각자의 이력으로 관리한다.
- 복잡한 본문 diff, 과거 홈페이지 전체 렌더링, 휴지통, 승인/반려, 원본 데이터 변환은 추가하지 않았다.

복구는 정상 종료 후 **V9 DB + 5B-2A JAR** 또는 **V10 DB + 5B-2B-2 JAR** 쌍으로 수행한다. V10 DB를 구형 JAR와 섞으면 버전 기록/미디어 보호를 우회할 수 있으므로 혼용하지 않는다. 실패한 DB에서 수동 DROP/역 migration 대신 보존된 파일 쌍을 새 사본으로 복원한다. 상세 경로와 명령은 checkpoint의 RESTORE.md에 기록했다.

## 이번 단계 변경 파일

- `docs/DB_MIGRATION_RISKS.md`
- `docs/PHASE5B2B2_VERSION_HISTORY.md`
- `frontend/src/ContentEditor.tsx`
- `frontend/src/PageEditor.tsx`
- `frontend/src/PageTemplates.tsx`
- `frontend/src/VersionHistoryDialog.tsx`
- `frontend/src/api.ts`
- `frontend/src/version-history.css`
- `frontend/src/versionHistory.ts`
- `scripts/run-version-history-copy.ps1`
- `src/main/java/egovframework/backoffice/mvp/cms/ActivityService.java`
- `src/main/java/egovframework/backoffice/mvp/cms/DeletionImpactService.java`
- `src/main/java/egovframework/backoffice/mvp/cms/MediaService.java`
- `src/main/java/egovframework/backoffice/mvp/cms/PageBlockService.java`
- `src/main/java/egovframework/backoffice/mvp/cms/PageController.java`
- `src/main/java/egovframework/backoffice/mvp/cms/PageService.java`
- `src/main/java/egovframework/backoffice/mvp/cms/PageTemplateService.java`
- `src/main/java/egovframework/backoffice/mvp/cms/RichTextService.java`
- `src/main/java/egovframework/backoffice/mvp/cms/UsageService.java`
- `src/main/java/egovframework/backoffice/mvp/next/NextPageApi.java`
- `src/main/java/egovframework/backoffice/mvp/next/NextPostApi.java`
- `src/main/java/egovframework/backoffice/mvp/next/NextTemplateApi.java`
- `src/main/java/egovframework/backoffice/mvp/next/NextVersionApi.java`
- `src/main/java/egovframework/backoffice/mvp/post/PostController.java`
- `src/main/java/egovframework/backoffice/mvp/post/PostService.java`
- `src/main/java/egovframework/backoffice/mvp/security/SecurityConfiguration.java`
- `src/main/java/egovframework/backoffice/mvp/version/SaveIntent.java`
- `src/main/java/egovframework/backoffice/mvp/version/VersionHistoryService.java`
- `src/main/java/egovframework/backoffice/mvp/version/VersionKind.java`
- `src/main/java/egovframework/backoffice/mvp/version/VersionMediaReferences.java`
- `src/main/java/egovframework/backoffice/mvp/version/VersionRestoreService.java`
- `src/main/java/egovframework/backoffice/mvp/version/VersionSnapshots.java`
- `src/main/java/egovframework/backoffice/mvp/version/VersionStore.java`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/h2/V10__document_versions.sql`
- `src/main/resources/mapper/CmsMapper.xml`
- `src/main/resources/mapper/VersionMapper.xml`
- `src/main/resources/static/js/writing.js`
- `src/main/resources/templates/cms/delete-confirm.html`
- `src/main/resources/templates/cms/page-form.html`
- `src/main/resources/templates/posts/form.html`
- `src/test/java/egovframework/backoffice/integration/ClassificationMigrationTest.java`
- `src/test/java/egovframework/backoffice/integration/NextAdminIntegrationTest.java`
- `src/test/java/egovframework/backoffice/integration/NextPostIntegrationTest.java`
- `src/test/java/egovframework/backoffice/integration/NextWorkspaceIntegrationTest.java`
- `src/test/java/egovframework/backoffice/integration/OperatingPolicyIntegrationTest.java`
- `src/test/java/egovframework/backoffice/integration/VersionHistoryIntegrationTest.java`
- `src/test/java/egovframework/backoffice/integration/VersionMigrationTest.java`
