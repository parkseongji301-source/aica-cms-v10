# 5B-2B-1 버전 이력·복구 설계 및 영향 분석

2026-09-27. **설계 제안이며 구현 결과가 아니다.** 애플리케이션·권한·DB·migration을 변경하지 않았다. 실제 version 생성, 복구 API/UI, 테스트 실행, 서버 재시작도 하지 않았다. 원본 V3는 유지한다.

추천: **대상별 version 테이블 3개 + JSON snapshot + 미디어 참조 테이블 3개**. 기존 PostService / PageService / PageTemplateService의 잠금·검증·트랜잭션 안에 버전 보관을 연결한다. 공개 조회는 계속 최신 publication을 사용한다.

## 1. 확인 범위와 현재 상태

현재 작업 트리의 React/Thymeleaf 저장 요청, Java 서비스, MyBatis, V1~V9 migration, 5B-2A 완료 증거를 읽었다. 실행 중인 DB에 연결하거나 새 앱을 시작하지 않았다. 따라서 아래 데이터 규모는 **5B-2A 완료 시 저장한 검증 기록**이며 이 분석 시점의 운영 DB 실시간 집계가 아니다.

- `.cache/phase5b2a-final-cold-baseline.json`: posts 18행, site_pages 2행(1·65), page_templates 2행, 콘텐츠 발행본 3행, 페이지 발행본 2행, media 1행, activity_log 144행, 기존 테이블 28개.
- `.cache/phase5b2a-runtime-before.json`: 콘텐츠 18개·페이지 2개의 조회 응답이 저장되어 있다. UTF-8 JSON 직렬화 크기는 콘텐츠 약 492~1,259 bytes, 페이지 약 547~923 bytes다. **새 version payload의 확정 크기는 아니다.** 템플릿 기록은 목록 요약이므로 블록 snapshot 용량 계산에 사용할 수 없다.
- 기존 migration 검색 경로는 `classpath:db/migration/h2`. V8은 같은 패키지의 Java migration이다. 마지막 스키마는 V9 `page_templates`이며 version 테이블은 없다.
- `ClassificationMigrationConfiguration`은 원본 `.local-data`/local profile 및 명시적으로 승인되지 않은 파일 DB migration을 차단한다. 이 보호를 유지한다.

현재는 revision 숫자와 최신 발행본, 활동 이력만 있다. `PostService.save()`는 발행 시 이전 publication을 지우고 새 publication·분류·주소·미디어를 복사한다. `PageService.save()`도 최신 publication을 교체한다. 이 구조만으로 이전 발행본을 복원할 수는 없다.

## 2. 자동저장 / 수동저장 구분

| 경로 | 현재 서버가 받는 값 | 실제 구분 | 필요한 변경안 |
|---|---|---|---|
| React ContentEditor | `PUT /api/admin/next/posts/{id}`, revision 및 문서 | 불가 | `saveIntent` 추가 |
| React PageEditor | `PUT /api/admin/next/pages/{id}`, revision/title/sections | 불가 | `saveIntent` 추가 |
| Thymeleaf 콘텐츠·페이지 JS | `POST /admin/{posts,pages}/save-json`, `action=save` | 불가 | 자동/수동 intent를 FormData에 명시 |
| Thymeleaf 발행 | `action=publish` | 발행은 구분 가능 | 서버가 PUBLISH 이유 결정 |
| 일반 Thymeleaf 폼 제출 | `action=save/publish` | 수동 제출이지만 intent 없음 | hidden 필드 및 controller 계약 명시 |
| React 공용 템플릿 | POST/PUT `/api/admin/next/page-templates` | 현재 모두 명시적 저장 | 자동저장 도입 없이 저장 성공 시 기록 |

React의 `automatic` 인수와 writing.js의 `automatic` 인수는 클라이언트 내부에만 있다. 요청 API에는 전송하지 않는다. 현재 서비스는 자동저장도 일반 임시저장 활동 이력으로 기록한다. **활동 이력의 이름으로 과거 자동/수동 저장을 역추정하면 안 된다.**

중요한 UI 차이: React는 자동저장 후 dirty=false가 되면 명시적 저장을 비활성화하고 save()도 조기 반환한다. 따라서 intent 추가만으로는 요구사항을 충족하지 못한다.

추천 요청 계약:

```json
{
  "revision": 42,
  "saveIntent": "MANUAL_DRAFT",
  "operationId": "요청마다 발급하는 UUID",
  "title": "현재 문서 제목"
}
```

- 실제 요청에는 기존 문서 필드를 함께 보낸다. `saveIntent`는 `AUTOSAVE | MANUAL_DRAFT`만 받는다. PUBLISH/RESTORE를 클라이언트가 일반 저장 요청으로 지정할 수 없게 한다.
- 초안 내용이 바뀌었다면 기존 저장과 revision 증가 후 snapshot을 생성한다.
- 자동저장이 이미 끝나 내용이 같아도 [초안 저장]/Ctrl+S는 MANUAL_DRAFT snapshot을 남길 수 있게 한다. 이때 **문서 revision은 유지**하고 version ID만 새로 발급하는 것을 추천한다. 무의미한 revision 증가로 공개본과 다른 초안처럼 표시되는 일을 피한다. 서버에서 정규화한 전체 저장 상태로 비교하며 클라이언트 dirty 플래그를 신뢰하지 않는다.
- UI에 ‘자동 저장됨’과 ‘초안 버전 저장됨’을 구분한다. version ID는 revision과 다르며 같은 revision에 수동 체크포인트와 발행 관련 기록이 함께 존재할 수 있다.
- 저장 중 수동 저장을 눌렀다면 진행 중인 자동저장 완료와 최신 revision을 반영한 뒤 직렬 실행한다. 임의로 동시에 두 저장을 보내지 않는다.
- 신규 JSON 저장 계약과 save-json은 intent 누락을 거절하고 재로딩을 안내한다. 현재 React와 Thymeleaf JS를 함께 갱신한다. JS 없이 제출하는 기존 HTML 폼은 명시적 수동 저장으로 처리한다. 구형 자동저장 요청을 수동 저장으로 추측해 버전을 폭증시키지 않는다.
- 내부 초기화/fixture용 PostService.create 및 이전 overload도 호출 목적을 명시한다. ‘신규 편집기에 들어가기 위한 빈 초안 생성’은 버전 생성 대상에서 제외하고 첫 명시적 저장/발행부터 기록한다. 사용자가 완성된 내용을 수동 생성·저장한 경우에는 최초 버전을 만든다.
- operationId로 정상적인 재시도/중복 클릭의 중복 기록을 방지한다. 권한은 재시도에도 재검사한다. 보관된 결과의 동일 operationId·동일 요청은 이미 처리됨으로 반환하고 다른 payload 재사용은 거절한다. 보관 정책으로 결과가 제거된 장기 재시도까지 무한 보장하는 멱등성 저장소는 이번 범위에서 만들지 않는다.

## 3. 버전 생성 시점과 보관 정책

| 계기 | 기록 | 보관 |
|---|---|---|
| 1.8초 자동저장 | 없음 | 현재 초안만 갱신 |
| 콘텐츠·페이지 명시적 초안 저장 | MANUAL_DRAFT, 저장 후 상태 | 대상별 최근 20개 초안 계열 |
| 콘텐츠·페이지 발행 | PUBLISH, 실제 발행 성공 상태 | 모두 보관 |
| 콘텐츠·페이지 복구 | RESTORE, 새 초안 상태 | 최근 20개 초안 계열 |
| 복구 직전 보호 기록 — 추가 추천 | RESTORE_BACKUP, 덮어쓰기 직전 초안 | 최근 20개 초안 계열 |
| 템플릿 명시적 저장/비활성화 저장 | MANUAL_SAVE | 템플릿별 최근 20개 |
| 템플릿 복구 후 명시적 저장 | RESTORE (+ 직전 RESTORE_BACKUP 권장) | 템플릿별 최근 20개 |
| 미리보기/비교/불러오기/이력 조회 | 없음 | 읽기만 수행 |
| 공개 중단 | 기존 활동 이력만 | 현재 publication 유지 |

발행 버튼이 ‘저장+발행’을 수행해도 버전은 **PUBLISH 한 개**를 만든다. 내부 초안 쓰기에 MANUAL_DRAFT를 중복 생성하지 않는다. 검증·충돌·권한 오류나 트랜잭션 실패 시 버전도 생성하지 않는다.

**20개는 시작값으로 적절하다.** 현재 검증 데이터는 작고 1.8초 자동저장을 제외하므로 탐색과 구현이 단순하다. 다만 시험 문서가 짧아 실제 운영 용량을 대표하지 않는다. 현재 rich 문서는 250,000자, 페이지 섹션 원문은 1,000,000자 제한까지 허용하므로 문서가 커질 수 있다. 발행 버전 무제한과 과거 버전이 붙잡는 미디어 BLOB이 장기 용량의 주요 변수다.

- `backoffice.versions.post-draft-limit`, `page-draft-limit`, `template-limit`을 서버 설정으로 두고 기본값을 각각 20으로 제안한다. 운영자가 화면에서 임의로 바꾸는 기능은 필요 없다.
- ‘최근’은 created_at 및 id 순서로 결정한다. PUBLISH를 초안 20개에 포함하지 않는다. 기간 기반 자동 폐기는 추가하지 않는다.
- RESTORE_BACKUP을 넣으면 복구 한 번이 초안 보관 슬롯 두 개를 사용한다. 이력 화면에 복구 전/후 기록을 같은 작업으로 묶어 보여준다. 이는 자동저장 주기마다 생성하는 이력이 아니라 **명시적 복구 작업의 안전 기록**이다.
- 버전 생성과 같은 트랜잭션에서 해당 대상의 초과 버전 및 참조 인덱스를 정리한다. 별도 운영 스케줄러는 우선 필요 없다. 설정을 낮춘 경우 다음 명시적 저장부터 적용하고 미리 폐기 범위를 안내한다.
- PUBLISH ‘모두 보관’은 대상이 존재하는 동안의 보관을 의미하도록 추천한다. SUPER_ADMIN의 명시적 영구 삭제 시 해당 이력까지 폐기할지는 아래 추가 결정 항목으로 분리한다.

## 4. 콘텐츠 snapshot 범위

서버가 검증·저장한 값을 다시 조립해 기록한다. React 요청만 저장하면 Thymeleaf에서 생략한 신규 분류/맛집 정보와 기존 첨부가 누락될 수 있다.

| 구분 | 포함/복구 방침 |
|---|---|
| title, content, richContent | 모두 보관·복구. FAQ 질문/답변, 맛집 식당명/소개 명칭은 동일 필드 해석 |
| category | 기존 category ID와 당시 표시 이름. 복구는 ID 기준 |
| classification | 유형 code/당시 이름, 기수·주제 ID/code/당시 이름 및 선택 배열 |
| media | 기존 첨부 media ID 및 sort_order, rich Delta 내부 이미지·첨부 ID/표현 데이터 |
| restaurant | RESTAURANT 주소 전체. 비맛집은 명시적으로 null |
| 당시 상태·발행 revision·발행 시각 | 비교용 메타데이터. 복구 시 공개 상태를 되돌리지 않음 |
| post ID·원저자·생성일·deleted_at | 대상 정체성/현재 삭제 판정. 복구할 편집 필드에서 제외 |
| revision·수정 시각 | 과거 값은 기록만. 복구 후 현재 revision+1, 현재 수정 시각 |

snapshot은 예를 들어 `title/content/richContent/category/classification/media/restaurant`를 가진 내부 DTO이며 `snapshot_schema_version=1`로 시작한다. 기존 rich 문서를 HTML로만 보관하지 않는다. JSON의 당시 라벨은 비교용이며 이름을 보고 다른 topic ID로 재매핑하지 않는다. REVIEW_LIFE/FAQ_LIFE는 계속 다른 ID다.

분류 연결은 version별 별도 관계형 사본을 만들지 않고 JSON에 보관한다. 과거 이력의 분류 검색이 현재 목적이 아니고, 복구할 때 한 객체 전체를 읽으면 되기 때문이다. 현재 사전의 FK를 version에 강하게 연결해 과거 표시 이름까지 현재 사전에 종속시키지 않는다.

복구 전에는 현재 사전·허용 주제·미디어 존재·유형 규칙을 다시 검사한다. 현재도 유지 중인 비활성 분류는 기존 ClassificationService 규칙을 따르고, 이미 빠졌던 비활성 분류를 다시 추가하는 것은 차단될 수 있다. 사라진 category/topic, 허용이 해제된 주제 등을 조용히 제거하거나 자동 재생성하지 않는다. 비교 화면은 읽을 수 있어야 하며 복구 불가 사유를 명시한다. 임의 분류 치환 UI는 기본 범위에 넣지 않는다.

맛집에서 과거 일반 글로 돌아갈 때는 과거 snapshot의 restaurant=null이 **의도적 전체 복원**임을 서버 복구 경로에서 처리해야 한다. 기존 요청의 필드 누락=보존 계약을 깨지 않고, 기존 RestaurantDetailsService의 명시적 비우기 검증을 거친다.

## 5. 페이지 snapshot 범위

- 제목과 sections_json 전체를 보관한다. 현재 구조는 wrapper 없는 Section 배열이며 각 블록에 `id/schemaVersion/variation/visible`와 type별 flat 필드가 있다.
- heading/body/bodyDoc/imageId/categoryId/link/label, POSTS sourceMode/query/manual을 모두 보관한다. 현재 소스로 사용하지 않는 query/manual 설정도 삭제하지 않는다.
- 순서는 배열 순서로 보관한다. POSTS manual은 postIds 참조와 순서만 저장한다. 원문 콘텐츠를 복사하지 않는다.
- pageId·원저자·생성 정체성은 유지한다. 메뉴와 첫 화면 연결을 복구에 포함하지 않는다.
- **slug는 당시 경로를 확인하는 정보로만 보관하고 일반 버전 복구 대상에서 제외한다.** ADMIN의 slug 변경 금지와 기존 메뉴/URL 경계를 지킬 수 있다. SUPER_ADMIN이 주소를 바꾸려면 기존 별도 작업을 사용한다.
- status, publishedRevision, published_at도 관찰 정보이며 복구하지 않는다. 기존 발행본에만 남아 있는 예전 slug로 현재 페이지 주소를 되돌리지 않는다.

version payload의 `snapshot_schema_version`과 블록의 `schemaVersion=2`는 서로 다른 버전이다. 이력을 위해 현재 sections_json 저장 형식을 다시 바꾸지는 않는다. 미래에 컴포넌트가 폐기되면 과거 snapshot을 읽는 decoder/호환 규칙이 필요하며, 지원 불가능한 필드를 무시하고 복구 성공으로 처리하지 않는다.

## 6. 템플릿 snapshot 범위

- name, description, active, blocks_json 전체를 보관·복구한다. 각 type/schemaVersion/variation/visible/data 및 POSTS 세 방식의 설정을 포함한다.
- templateId와 created_by/created_at은 그대로 유지한다. updated_by/updated_at은 이번 저장자를 기록한다.
- 현재 PageTemplateService는 저장할 때 페이지 block ID를 제거한다. 버전도 **페이지 block ID 없는 템플릿 구성**을 그대로 저장한다. 템플릿에 새 영구 블록 ID 체계를 도입하지 않는다.
- 복구 선택은 편집기 메모리의 작업값으로 준비한다. 그 단계에는 DB 변경/버전 생성이 없다. 취소·닫기·이동 보호를 적용한다.
- [복구 내용 저장]을 명시적으로 실행하면 expected revision을 검사하고 현재 템플릿과 RESTORE 버전을 저장한다. 발행은 없다. active도 과거 값으로 복구한다면 재활성화 여부를 확인 화면에 표시한다.
- 이미 적용된 Page A/B는 템플릿과 참조 연결이 없으므로 변경되지 않는다. 앞으로 템플릿을 새로 불러오는 경우에만 저장한 복구 결과가 사용된다.

현재 템플릿 관리 화면의 메타데이터 수정과 PageEditor의 ‘현재 구성을 템플릿으로 저장/덮어쓰기’ 두 경로 모두 동일 서비스에서 기록해야 한다. 현재 관리 UI에는 별도 영구 작업본이나 버전 복원 UI가 없다.

## 7. 추천 DB 구조

**대상별 version 테이블**을 선택한다. 대상별 FK, 권한, 복구 규칙, 삭제 수명이 달라 공통 target_type/target_id 테이블보다 검증하기 쉽다. 공통화는 메타데이터 DTO·페이지네이션·보관 계산 등 작은 코드에 한정한다.

| 새 테이블(제안명) | 핵심 내용 |
|---|---|
| post_versions | post_id → posts, 메타데이터, snapshot_json CLOB |
| page_versions | page_id → site_pages, 메타데이터, snapshot_json CLOB |
| page_template_versions | template_id → page_templates, 메타데이터, snapshot_json CLOB |
| post_version_media | version_id → post_versions, media_id → media |
| page_version_media | version_id → page_versions, media_id → media |
| page_template_version_media | version_id → page_template_versions, media_id → media |

각 version 테이블에는 다음 공통 메타데이터 항목을 둔다.

- `id BIGINT IDENTITY`, 대상 FK, `snapshot_schema_version`, `snapshot_json`.
- `reason`: MANUAL_DRAFT/PUBLISH/RESTORE/RESTORE_BACKUP/BASELINE, 템플릿은 MANUAL_SAVE/RESTORE/RESTORE_BACKUP/BASELINE. 대상별 허용값을 검증한다.
- `snapshot_kind`: DRAFT/PUBLISHED, 템플릿은 TEMPLATE. 보관 그룹과 공개본 초기 기준점 구분용이다. API의 targetKind는 테이블에서 결정한다.
- `source_revision`: 기록 시점의 대상 revision. version ID와 혼용하지 않는다.
- `created_at`, `created_by`, `creator_name_snapshot`: 이번 버전을 남긴 사람과 시각. 문서 원저자를 바꾸는 필드가 아니다. 기존 timestamp 방식을 유지하고 API는 중앙 time-zone 설정으로 offset을 붙인다.
- `source_version_id`: 복구 출처. 같은 대상 이력인지 서버가 확인한다. 원본 이력이 보관 만료되어도 식별값을 남길 수 있도록 자기 참조 FK로 삭제를 막지 않는 안을 추천한다. 이미 만료됐으면 UI에서 원문 조회 불가를 표시한다.
- `operation_id`, `request_hash`: 정상 재시도 중복 방지. 대상+operation+reason 조합에 유일 제약을 둬 복구 전/후 두 기록은 허용한다.
- `activity_log_id`: 기존 활동 기록에 연결. 활동 이력 정리로 version이 삭제되지 않도록 nullable/ON DELETE SET NULL 방향이다.
- 필요 시 `metadata_json`: 복구 전 revision과 이전→새 block ID 대응 등 **소량의 작업 결과**. 실제 본문은 여기에 중복 저장하지 않는다.

목록 인덱스는 `(대상ID, id)`와 보관 정리를 위한 `(대상ID, snapshot_kind, id)`. media 연결 테이블은 `(version_id, media_id)` PK 및 media_id 역조회 인덱스를 둔다. version 삭제 시 media 연결 행은 CASCADE, media 삭제는 참조가 있으면 RESTRICT한다. 바이너리 파일을 버전마다 복제하지 않는다.

원본 대상 FK는 물리 삭제 시 CASCADE가 가능하지만, **현재 콘텐츠 삭제는 posts의 deleted_at 처리이므로 FK만으로 이력이 제거되지 않는다.** 영구 삭제 시 이력 폐기 정책을 채택하면 PostService에서 명시적으로 처리해야 한다. 이를 휴지통/삭제 글 복원으로 확대하지 않는다.

## 8. 미디어 version 참조 보호

현재 보호는 post_media, post_publication_media, page_media, 로고 및 활성/비활성 템플릿 원문 검사까지다. version은 아직 검사할 저장소가 없다.

새 version 생성 시 같은 트랜잭션에서 아래 ID 합집합을 관계 테이블에 기록한다.

1. 콘텐츠: 별도 첨부 연결 + rich Delta의 aicaImage/aicaFile.
2. 페이지: **visible=false까지 포함한 모든 블록**의 imageId와 bodyDoc 내부 미디어.
3. 템플릿: 활성/비활성 및 표시/숨김에 관계없이 모든 블록의 미디어.

현재 페이지 발행용 page_media는 보이는 블록만 연결한다. 과거 페이지 전체를 복구하려면 version 인덱스를 그 목록에서 그대로 복사하면 안 된다. snapshot 전체에서 직접 추출해야 한다.

`MediaService.delete`, `UsageService`에 세 역조회 결과를 합친다. 사용처는 ‘페이지 #65 · 수동 저장 버전 #… · 시각’처럼 표시하고 권한 있는 경우에만 상세 링크를 제공한다. 권한 없는 이력의 본문·제목을 사용처 API로 새로 노출하지 않는다. 삭제 검사와 버전 쓰기는 기존 cms_guard 잠금 및 FK를 함께 사용해 경쟁 상태를 막는다.

보관 만료로 version이 삭제되면 해당 참조 행만 제거한다. 다른 초안·공개본·템플릿·version 참조가 없을 때 기존 미디어 삭제가 가능해진다. **파일 자동 삭제는 하지 않는다.** 손상된 snapshot은 보호 검사를 생략하는 근거가 되지 않으며 초기화/저장을 실패 처리하고 점검한다.

버전에서만 쓰는 파일은 익명 공개 파일로 승격시키지 않는다. 공개 미디어 API는 현재 발행본 참조만 허용한다. 이력 미리보기는 관리자 인증으로 처리한다. SUPPORTER가 본인 이력에 등장하는 타인 소유 미디어를 조회해야 하는 경우에는 version 소속·현재 문서 접근권한을 검사하는 제한된 파일 조회를 설계하고, 일반 미디어 목록 권한을 확대하지 않는다.

본문에 사람이 직접 넣은 외부 URL, CTA 링크는 현재처럼 ID 참조 추적의 한계가 있다. manual POSTS의 콘텐츠 ID는 미디어 ID가 아니다. 그 콘텐츠의 과거 본문까지 페이지 version에 재귀적으로 복사하거나 과거 콘텐츠를 강제 보존하지 않는다.

## 9. block ID 복구 규칙

`PageBlockService.validate()`는 retired ID, 다른 페이지 ID, 현재 초안에 없는 기존 ID의 재사용을 거절한다. `synchronize()`는 제거된 ID를 retired 처리한다. 이 규칙을 완화하지 않는다.

복구를 실행하는 시점의 잠금 안에서 과거 snapshot과 현재 초안, identity ledger를 비교한다.

| 과거 블록의 ID 상태 | 복구 동작 |
|---|---|
| 현재 같은 페이지 초안에 존재 + ledger 활성 | 같은 ID 유지, 과거 내용·순서로 복구 |
| 과거에는 있었지만 현재 초안에서 삭제/retired | `block_`+UUIDv4 새 ID 발급 |
| 과거 발행본에만 있고 현재 초안에는 없음 | 예전 ID를 부활시키지 않고 새 ID 발급 |
| 다른 페이지 소속·중복·등록 기록 손상 | 복구 차단 및 문제 표시 |
| ID 없는 구형/예외 snapshot | 순서/내용으로 추측 금지. 지원 decoder가 없으면 복구 차단 |

현재 초안에만 있고 복구 결과에서 빠지는 블록은 기존 규칙대로 retire한다. 과거 snapshot/현재 공개본의 ID를 수정하지 않는다. 복구 결과의 old→new ID 대응을 결과 메타데이터에 남겨 운영자가 확인할 수 있게 한다. 이전 딥링크를 조용히 다른 ID로 redirect하지 않는다.

예: 현재 초안 A/C, 과거 버전 A/B/C라면 복구 후 A/B′/C. A/C는 같은 ID, B′만 신규 ID. 페이지 재발행 전 공개본은 그대로다. 템플릿은 page block ID가 없는 원본이므로 복구와 무관하게 페이지에 적용할 때 항상 새 ID를 발급한다.

## 10. 복구 트랜잭션 / revision / 공개본

콘텐츠·페이지 복구는 서버 저장 동작이다. ‘새 초안’은 새 객체나 새 post/page ID가 아니라 **같은 객체의 새 revision**을 뜻한다.

1. 현재 로그인·역할·대상 접근, version 소속 검사.
2. cms_guard 잠금, 현재 revision과 expectedRevision 비교. 누락/충돌 시 거절.
3. snapshot decoder로 전체 내용을 읽고 현행 분류·미디어·컴포넌트 규칙 검증. page ID 계획 계산.
4. 권장 RESTORE_BACKUP으로 현재 초안을 보관.
5. 기존 PostService/PageService의 저장 검증·미디어 연결·분류·주소·블록 identity 처리를 재사용하여 **draft만** 복원, 현재 revision+1.
6. RESTORE snapshot 및 media 참조, audit 연결, 보관 정리까지 같은 트랜잭션에서 처리.
7. 갱신된 문서·revision·version ID·블록 ID 대응을 반환. 편집기는 이 값을 새 기준점으로 삼고 미리보기를 보여준다.

공개 중인 문서는 status=PUBLISHED를 유지한다. status를 DRAFT로 내려 공개 자체를 중단하지 않는다. PRIVATE도 그대로 유지한다. publishedRevision, 최신 publication의 제목/본문/분류/주소/섹션/파일은 건드리지 않는다. 이후 별도 발행 요청만 publication을 교체한다.

주의할 기존 보호: PageService는 query/manual 필드 누락을 구형 클라이언트 유실로 판단해 거절한다. 과거 version이 해당 설정을 갖지 않은 것은 합법적인 전체 복원일 수 있다. 복구 전용 **서버 내부 명시적 전체 교체 command**를 구분하되 일반 저장 API의 누락 방지 검사는 유지해야 한다. 클라이언트가 임의 restore 플래그로 이를 우회하도록 만들지 않는다.

복구 확인창을 여는 동안 자동저장은 일시 중지하고 진행 중 저장을 먼저 완료한다. 비교 기준 revision이 변하면 재조회·재확인을 요구한다. 로컬 미저장 값은 별도 안내하며 버릴지 명시적으로 결정하지 않은 상태에서 덮어쓰지 않는다. 기존 저장 중 이동/종료 보호는 유지한다. 복구 후 남아 있던 자동저장 타이머가 과거 입력을 다시 덮어쓰지 않도록 취소하고 fingerprint·revision을 함께 갱신한다.

서버에 아직 도달하지 않은 브라우저 입력은 RESTORE_BACKUP으로 보관할 수 없다. 그런 입력을 보존하려면 먼저 명시적 저장을 완료한 뒤 복구하거나, 사용자가 버리기를 확인해야 한다.

## 11. 권한 추천 — 아직 미적용

| 대상/작업 | SUPPORTER | ADMIN | SUPER_ADMIN |
|---|---|---|---|
| 콘텐츠 이력 조회·비교 | 본인 콘텐츠만 | 전체 | 전체 |
| 콘텐츠 새 초안 복구 | 우선 차단 추천 | 전체 | 전체 |
| 복구 후 콘텐츠 발행 | 차단 유지 | 가능 | 가능 |
| 페이지 이력 조회·내용/블록 복구 | 차단 | 가능 | 가능 |
| 복구로 slug 변경 | 차단 | 차단 | 복구 대상에서 제외, 기존 별도 수정 사용 |
| 템플릿 이력 조회·작업본 준비·저장 | 차단 | 차단 | 가능 |

SUPPORTER의 본인 초안 직접 수정 권한은 유지한다. 일괄 복구는 ADMIN이 정리한 전체 분류/첨부까지 교체할 수 있으므로 최초 제공에서는 조회만 허용하는 보수적인 안을 추천한다. 향후 필요하면 ‘본인 콘텐츠의 초안 복구’만 추가해도 발행 권한은 별도로 유지할 수 있다.

이력에 기록된 당시 작성자의 역할을 현재 접근권한으로 사용하지 않는다. 현재 계정 활성·역할·대상 소유권을 매 요청마다 검사한다. 삭제된 대상의 이력 조회로 삭제 글을 되살리는 기능을 만들지 않는다. URL의 대상 ID와 version의 소속이 다르면 차단한다. 모든 변경은 세션/CSRF 검사를 유지한다.

## 12. 필요한 API / UI

기존 저장 API를 유지·확장하며 version별 일반 CRUD/임의 snapshot 업로드 API는 만들지 않는다. 다음 URL은 설계안이다.

| API | 목적 |
|---|---|
| GET `/api/admin/next/posts/{id}/versions` | 메타데이터 목록, 페이지네이션 |
| GET `/api/admin/next/posts/{id}/versions/{versionId}` | 과거 snapshot, 현재 문서 revision, 복구 검증 결과 |
| POST `/api/admin/next/posts/{id}/versions/{versionId}/restore` | `{expectedRevision, operationId}`로 새 초안 저장 |
| pages의 동일 경로 | 페이지 이력/비교/복구, slug 복구 없음 |
| page-templates의 versions 목록/상세 경로 | SUPER_ADMIN 전용 이력/비교 |
| GET 각 대상의 `versions/{versionId}/preview` | 인증된 과거 내용 미리보기, 지원 불가능한 구성은 경고와 구성 표 |
| POST `/api/admin/next/page-templates/{id}/versions/{versionId}/prepare-restore` | DB 변경 없이 복구 후보를 편집기로 반환 |
| POST `/api/admin/next/page-templates/{id}/versions/{versionId}/restore` | 명시적 확인·저장 시 전체 복구 및 revision 증가 |
| 기존 media usage API 확장 | version 종류·번호·시각·허용된 링크 표시 |

템플릿 prepare 후 값을 추가 수정했다면 일반 PUT 저장에 검증된 sourceVersionId를 전달하는 계약을 둘 수 있다. ‘이 버전을 그대로 복구’와 ‘이 버전에서 시작해 수정’은 활동 설명에서 구분한다. 두 저장 로직을 복제하지 않고 같은 PageTemplateService를 사용한다. 최초 UI는 그대로 복구 저장 후 기존 수정 UI에서 추가 편집하는 단순 흐름으로 충분하다.

비교 화면:

- 공통: 버전 번호/당시 revision/시각/저장자/이유, 현재 초안과 선택 버전, 현재 공개 상태, 복구 시 영향, 검증 오류.
- 콘텐츠: 현재/과거 제목·분류·주소·첨부 및 본문 미리보기.
- 페이지: 현재/과거 블록 목록·순서·ID·표시 여부·Variation·POSTS 설정. 복구 시 새 ID가 필요한 블록 표시.
- 템플릿: 이름·설명·활성 여부·블록 구성 비교. 기존 적용 페이지에 전파되지 않음을 짧게 표시.
- 과거 snapshot은 현재 쓰기용 검증에 실패해도 가능한 필드 비교는 표시한다. 현재 PageService.previewSections는 현행 규칙을 검증하므로 이를 무조건 과거 미리보기에 사용하면 옛 분류/컴포넌트 문제로 이력 자체가 열리지 않을 수 있다. 읽기용 표시와 적용 가능 검사를 분리한다. 안전한 현재 rich renderer를 재사용하되 지원 불가능한 필드는 오류 설명/구성 표로 표시한다.
- POSTS 미리보기는 **과거의 블록 조건 + 현재 노출 가능한 발행 콘텐츠**다. 당시 홈페이지 전체를 보존한 화면이 아니다. 콘텐츠 원문/공통 스타일/메뉴/미디어 전역 이름·alt까지 시간 여행하는 기능은 범위 밖이다.

처음에는 React 공통 편집기에 이력을 연결한다. Thymeleaf에는 동일 ID의 React 이력 진입 링크를 제공하고, 자체 diff/복구 편집기를 중복 구현하지 않는다. 단 Thymeleaf 명시적 저장·발행에서도 서버 version 생성은 반드시 이루어져야 한다.

## 13. 예상 migration / 초기 기준점 / 검증 순서

**다음 단계에서 승인 후 수행할 계획이며 이번에는 실행하지 않았다.**

1. 최신 완료 V9 사본을 정상 종료/일관 백업 가능한 방법으로 보존하고 DB·JAR·소스·설정·체크섬을 함께 기록한다. 원본 V3는 연결하지 않는다.
2. 그 백업의 새 사본에서 실제 flyway_schema_history 및 SQL/Java 검색 경로를 다시 확인한다. 미완성 예전 V4는 격리 상태를 유지하고 기존 V1~V9 checksum을 바꾸지 않는다.
3. 다음 미사용 번호가 여전히 V10이면 `V10__document_versions.sql` 하나로 위 6개 테이블/인덱스를 추가한다. 기존 문서·분류·publication/블록 ledger를 변환하지 않는다.
4. **DDL과 초기 버전 보관은 분리한다.** 추천은 명시적인 일회성 초기화로 현재 초안·현재 발행본·현재 템플릿만 BASELINE으로 보관하는 것이다. 기존 이력을 만들어낸 것처럼 과거 시각/작성자를 추정하지 않는다. 생성 시각은 실제 캡처 시각, 생성자는 실행 관리자, 원래 발행 시각은 별도 관찰 정보다.
5. BASELINE_DRAFT 성격은 초안 20개, BASELINE_PUBLISHED 성격은 발행 보관 그룹, 템플릿 BASELINE은 템플릿 20개에 포함한다. seed 자체의 재실행도 기존 기준점을 중복 생성하지 않아야 한다. 자동 부팅마다 seed하지 않는다.
6. 초기화 전 missing media/분류/손상 JSON/ledger 검사를 수행한다. 기존 snapshot에 이미 없는 파일이 있으면 복구 가능한 상태라고 가정하지 않고 먼저 문제를 보고한다. 실제 배포에서는 초기화가 완료되기 전 쓰기를 열지 않아 첫 재발행 때 현재 공개본이 이력 없이 사라지는 구간을 없앤다.
7. V9→V10의 기존 테이블 값/ID/분류/category/미디어/공개본/블록 ID가 동일한지 비교한다. V3 **새 사본**부터 전체 migration 체인도 별도로 실행하고, 재실행 0건·완전 종료/재접속·재시작을 검증한다. 원본 경로 차단 테스트도 유지한다.
8. 실패 사본은 보존하고 대응하는 V9 DB+5B-2A JAR+설정 쌍으로 복구한다. H2 DDL 실패를 원본에서 즉석 repair하는 절차로 대체하지 않는다. V10 기능을 모르는 구 JAR로 V10 DB에 계속 쓰지 않는다.

초기 BASELINE은 새로운 제안이므로 적용 여부를 확인해야 한다. 하지 않는다면 ‘기능 도입 이후 저장/발행만 이력으로 남고 현재 공개본도 다음 발행 전에 별도 확보해야 한다’는 한계가 생긴다. 과거 수개월의 버전을 기존 revision 숫자나 activity_log만으로 재구성할 수는 없다.

## 14. publication / 활동 이력과의 관계

- publication: 공개 API가 사용하는 최신 발행 상태 한 벌. 기존 분류·맛집 주소 snapshot과 POSTS 공개 조회 규칙을 그대로 유지.
- version: 권한 있는 운영자가 비교·복구하는 과거 상태. 공개 API가 version 테이블을 읽는 경로는 추가하지 않음.
- activity_log: 누가 어떤 작업을 했는지 기록. 본문 snapshot이 없으므로 복구 데이터로 사용하지 않음.

ActivityService.record는 현재 void이며 audit insert도 ID를 반환하지 않는다. 생성된 activity ID를 얻는 경로를 추가하고 version.activity_log_id로 연결하는 것을 추천한다. 버전의 시각/저장자/이유는 이력 목록·장기 보관에 필요하므로 audit과 일부 중복되는 것이 정상이다. 본문은 audit.detail 500자에 넣지 않는다. 자동저장 활동 로그를 그대로 둘지 줄일지는 별도 정책이며 이번 버전 설계가 이를 자동 변경하지 않는다.

복구 한 번은 activity 한 개와 RESTORE_BACKUP/RESTORE 두 version을 연결할 수 있다. 버전 보관 만료가 audit를 삭제하지 않으며 audit가 정리돼도 snapshot 자체는 보존한다.

## 15. 5B-2B-2 예상 변경 영역 / 검증 기준

| 영역 | 예상 작업 |
|---|---|
| Java domain/service | version DTO/decoder/보관 서비스, PostService/PageService/PageTemplateService 저장·발행·복구 연결 |
| block/분류/맛집 | PageBlockService 복구 ID 계획, 기존 검증 재사용, 전체 복원과 생략 필드 보존 구분 |
| Mapper/DB | version 3종·미디어 참조 3종, 목록/상세/보관 정리, activity 생성 ID 반환 |
| API/권한 | NextPostApi/NextPageApi/NextTemplateApi, version 조회·복구, 현재 정책 기반 검사, capability 응답 |
| Thymeleaf | PostController/PageController, writing.js 및 폼 intent, 이력 진입 링크 |
| React | api/types, ContentEditor/PageEditor/PageTemplates의 이력 비교·복구, clean 수동 체크포인트, timer/guard/revision 동기화 |
| 미디어/삭제 | MediaService/UsageService/DeletionImpactService의 version 사용처와 영구 삭제 영향 |
| 시간/설정/문서 | 기존 중앙 시간 기준 재사용, 보관 개수 설정, API 계약/DB-JAR 복구 절차 |

필수 검증 계획:

1. React/Thymeleaf 자동저장은 revision만 바꾸고 version 개수는 증가하지 않음. 명시적 저장/발행은 정확한 이유로 기록.
2. 자동저장 직후 clean 상태에서 수동 저장이 기록됨. 재시도·더블클릭·저장 중 수동 요청으로 중복 기록/덮어쓰기 없음.
3. 초안 21개째 보관 정리, PUBLISH 전부 유지, template 20개, 복구 전/후 버전의 보관 규칙 확인.
4. 콘텐츠 title/content/rich/category/복수 분류/첨부 순서/주소 왕복. Thymeleaf 생략 신규 필드도 snapshot에 보존.
5. 페이지 block ID·schemaVersion·Variation·숨김·category/query/manual·비활성 source 설정 왕복. slug 불변.
6. 삭제된 block 신규 ID, 현재 block 동일 ID, retired ID 재사용 거절, 기존 딥링크 안전 처리.
7. RESTORE 후 revision 증가, public status/publication/발행 분류·주소·페이지 JSON 모두 불변. 재발행 후에만 공개 변경.
8. snapshot 삽입/참조 인덱스/보관 정리 실패 시 문서 변경도 rollback. 오래된 revision/다른 대상 version/역할 위조 차단.
9. template prepare는 읽기 전용, 명시적 복구 저장만 쓰기. 기존 적용 Page A/B와 독립.
10. version에서만 참조하는 파일 삭제 차단 및 사용처 표시. 숨김 블록/비활성 템플릿/과거 발행본 포함. 보관 정리 후 다른 참조 없을 때만 삭제 허용.
11. 공개 API로 이력/과거 전용 미디어 노출 금지. 본인/타인 콘텐츠와 이력 미디어 권한 검사.
12. 복구 중 자동저장/탭 전환/이동/종료, 서버 응답 유실 후 재조회, 만료 version 비교 화면 처리.
13. 현재 후기/FAQ/맛집·템플릿·POSTS 세 방식·기존 페이지1/65·Thymeleaf 저장 발행 회귀 및 서버 재시작.
14. V9 및 V3 사본 migration/재실행/DB-JAR 복구. 기존 데이터 보존 검증.

## 16. 추가 결정과 유지할 위험

실제 구현 전에 수용 여부를 확인할 추천사항은 다음과 같다.

1. **초안 계열 최근20개**에 RESTORE/RESTORE_BACKUP도 포함. 복구 직전 보호 버전 추가.
2. SUPPORTER는 본인 이력 조회만, 전체 복구 실행은 ADMIN/SUPER_ADMIN.
3. slug·공개 상태는 복구 제외. 템플릿 active는 확인 후 복구 가능.
4. 도입 시 현재 초안/발행본/템플릿 BASELINE을 명시적 일회성 작업으로 생성. DDL 자동 seed와 분리.
5. SUPER_ADMIN 영구 삭제 시 해당 version·version media 참조도 제거하고 활동 이력은 유지하는 것을 추천. 삭제 확인에 이력 폐기 개수 표시. 현재 posts 행의 tombstone/본문 잔존 방식 자체를 물리 삭제로 바꾸는 작업은 별개다.
6. clean 상태의 수동 저장은 문서 revision을 올리지 않고 version만 생성.

위 항목은 이번 분석에서 권한이나 보관 동작으로 적용한 것이 아니다.

기술 위험은 계속 유지한다.

- H2 2.3.232/Flyway 검증 범위 경고, 짧은 파일 DB 연결 수명 문제: 기능 테스트 통과로 해결 처리하지 않음.
- 원본 V3/검증 V9 격차. 버전 도입 후 새 스키마뿐 아니라 구 JAR의 권한/JSON/이력 미생성 차이도 존재하므로 반드시 DB와 JAR를 쌍으로 복구.
- 실제 공개 홈페이지 없음. 과거 블록/본문 미리보기와 실제 공개 renderer의 E2E는 아직 최종 검증 대상.
- 발행 이력 전부 보관 및 미디어 장기 보존에 따른 용량 증가. 시험 데이터 크기로 운영 비용을 보장할 수 없음.
- 과거 snapshot이 현재 사전·컴포넌트 정의와 맞지 않을 가능성. 읽기용 비교와 적용 검증 분리 필요.
- 지원되지 않는 snapshot schema를 조용히 잘라 읽으면 복구 데이터 손실. 명시적 decoder와 차단 필요.
- 타 문서·외부 URL·공통 설정까지 한 시점으로 되돌리는 기능이 아님. version 이력은 DB/JAR 백업을 대신하지 않음.
- 현재 timestamp를 UTC로 변환하지 않음. 기존 Asia/Seoul 해석과 중앙 offset 직렬화를 그대로 사용하고 과거 시각의 실제 저장 환경은 원본 전환 전에 재확인.

## 코드 근거

아래 경로는 저장소 루트 기준이다. 항목은 위 설계의 출발점이며 제안 API/테이블은 아직 없다.

- `frontend/src/api.ts:19,32`: 현재 page/post 저장 payload에 intent 없음.
- `frontend/src/ContentEditor.tsx:45`, `PageEditor.tsx:44`: auto 인수 로컬 처리, clean 반환, 1.8초 타이머.
- `src/main/resources/static/js/writing.js:16,19,30`: action=save 공유.
- `src/main/java/egovframework/backoffice/mvp/next/NextPostApi.java:32`, `NextPageApi.java:50`: 저장 요청 및 공통 서비스 호출.
- `src/main/java/egovframework/backoffice/mvp/post/PostService.java:141`: 권한/revision/분류/주소/미디어/발행 트랜잭션; `:110` 삭제 동작.
- `src/main/java/egovframework/backoffice/mvp/cms/PageService.java:85`: 전체 페이지 저장·ID 검증·발행; `:100,105` 누락 보호, `:125` 미디어 처리.
- `src/main/java/egovframework/backoffice/mvp/cms/PageBlockService.java:14,41`: UUID 발급 및 retired/타 페이지 ID 차단.
- `src/main/java/egovframework/backoffice/mvp/cms/PageTemplateService.java:26,39,47,57`: SA 제한, page ID 제거, 명시적 저장, read-only 적용 준비.
- `src/main/java/egovframework/backoffice/mvp/classification/ClassificationService.java:57`: 현재 사전/허용 주제 검증.
- `src/main/java/egovframework/backoffice/mvp/restaurant/RestaurantDetailsService.java:20`: 누락 보존 및 유형 전환 시 주소 비우기.
- `src/main/java/egovframework/backoffice/mvp/cms/RichTextService.java:20,77`: Delta/미디어 ID/길이 검증.
- `src/main/resources/mapper/CmsMapper.xml:4,36,47,67,122`: 잠금, 현재 미디어 사용처, 공개 파일 경계, 최신 publication 교체.
- `src/main/resources/db/migration/h2/V2__cms.sql`, `V4__content_classification_schema.sql`, `V7__restaurant_details.sql`, `V9__page_templates.sql`: 현재 FK와 저장 형태.
- `src/main/java/egovframework/backoffice/mvp/security/AccessPolicy.java:14`: 현재 세 역할 capability.
- `docs/PHASE5B2A_RESULTS.md`, `.cache/phase5b2a-final-cold-baseline.json`, `.cache/phase5b2a-runtime-before.json`: 완료 시 데이터/검증 기록. 이번 단계의 새 테스트 결과가 아님.

## 이번 분석의 변경·보존 확인

추가한 파일은 이 설계 문서뿐이다. 기존 수정·미추적 구현 파일을 삭제하거나 초기화하지 않았다. 소스 점검 중 계산한 `src/** + frontend/**(node_modules/dist 제외) + pom.xml` 합산 SHA-256은 문서 작성 후에도 `f413c72524309f89aa7c8d3722cf4b8d87cd55d9eae0153108ebec43cf270e31`로 동일했다. 원본 V3 파일 SHA-256도 기존 기준점과 같은 `55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`를 유지했다. DB 연결·migration·앱 실행·테스트는 수행하지 않았다.
