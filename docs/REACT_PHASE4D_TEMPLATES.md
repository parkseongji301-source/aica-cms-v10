# 4D 공용 템플릿 완료 보고

2026-09-27. 원본 V3는 유지했다. 최신 4C-2B V8 검증 DB를 정상 종료·백업한 뒤 새 사본에서 V9와 기능을 검증했다. 기존 미완성 변경을 포함한 소스 269개, DB, 기존 실행 JAR를 작업 전에 보존했다.

- 공용 템플릿: http://127.0.0.1:8091/admin-next/design/templates?view=manage
- 페이지 편집: http://127.0.0.1:8091/admin-next/pages/1/edit?view=manage
- 기존 관리자: 같은 8091 서버의 /admin. 기존 8090 4C-2B 서버도 보존했다.
- 로컬 검증 계정은 기존 1234 / 1234다. 세션 만료 후 로그인은 기존 /admin으로 이동하므로 원하는 React 링크를 다시 연다.

## 1. 저장 구조

V9__page_templates.sql은 `page_templates` 테이블 하나만 추가한다. 기존 테이블/컬럼/데이터를 수정하거나 템플릿을 자동 등록하지 않는다.

| 필드 | 역할 |
|---|---|
| id | 템플릿 ID, DB identity |
| name / description | 이름(150자), 용도(1000자) |
| active | 사용 가능 여부. 실제 삭제 API는 제공하지 않음 |
| blocks_json | 기존 Section 필드에서 페이지 block id만 제거한 배열 |
| revision | 동시 수정·적용 충돌 검사 |
| created_by / updated_by | users ID |
| created_at / updated_at | 생성·수정 시각 |

페이지 ID, slug/URL, 페이지 작성자, 공개 상태는 템플릿에 없다. 작성자 정보는 템플릿 자체의 생성·수정자다. 본문, 리치 문서, 미디어 ID, type/schemaVersion/variation/visible과 POSTS category/query/manual을 보존한다.

## 2. 템플릿과 block ID

템플릿에는 영구 페이지 block ID를 저장하지 않는다. 내부 블록 식별자를 별도로 만들 필요가 없어 추가하지 않았다. 배열은 템플릿 안의 배치 순서만 나타낸다.

저장 시 임시 새 ID를 붙여 기존 PageService.previewSections 검증을 수행한 뒤 ID를 제거한다. 불러오기 준비에서도 모든 블록에 `block_` + UUID v4를 새로 발급하고 같은 검증을 수행한다. 같은 템플릿을 두 번 불러와도 ID가 다르다.

준비 API는 페이지/템플릿/블록 ID 등록표에 쓰지 않는다. PageEditor에서 적용하고 기존 페이지 초안 저장이 실행될 때 PageBlockService가 새 ID를 등록하고 교체로 제거된 ID를 폐기한다. 템플릿과 페이지 사이 실시간 연결/FK는 없다.

## 3. UI

디자인 관리 > 공용 템플릿: 이름·설명·블록 수·활성·수정 시각·수정자 목록, 구성 요약, 생성/수정자, 이름·설명·활성 변경을 제공한다.

PageEditor:

- **현재 구성을 템플릿으로 저장**: 현재 편집 중 구성을 고정한 복사본을 미리 보여준다. 새 템플릿 저장 또는 기존 템플릿 구성 교체 저장을 제공한다. 기존 템플릿 덮어쓰기는 확인 후 진행한다.
- **템플릿 불러오기**: 활성 템플릿 선택 → 블록 구성 요약/POSTS 연결 조건/현재 콘텐츠 상태 확인 → 추가 또는 교체 → 적용.
- 별도 블록 편집기를 만들지 않았다. 템플릿 구성을 수정하려면 PageEditor에서 구성한 뒤 기존 템플릿에 저장한다. 현재 페이지 편집에 대한 자동 초안 저장 정책도 그대로 적용된다.
- 구성 미리보기는 블록 순서·요약·표시·Variation·조건·참조 상태를 보여준다. 적용 후의 실제 페이지 미리보기는 기존 PageEditor를 사용한다.

## 4. 추가/교체와 공개본

뒤에 추가는 기존 ID/구성을 유지하고 새 ID의 블록을 뒤에 붙인다. 교체는 확인창에서 다시 승인한 뒤 초안을 새 블록들로 바꾼다. 두 방식 모두 최대 30개 제한을 유지한다. 빈 템플릿도 허용하며 빈 구성으로 교체하면 선택 URL의 기존 block 값을 비운다. 신규 페이지/URL 생성 기능은 추가하지 않았다.

적용은 편집 중 문서만 변경하며 자동 발행하지 않는다. 기존 1.8초 자동 **초안** 저장은 유지한다. 저장/업로드 중이거나 준비 요청 도중 현재 블록 입력이 변경되면 적용을 중단해 다시 확인하도록 한다. 페이지 제목/주소 등 고유 정보는 적용하지 않는다.

브라우저에서 홈의 A/B에 뒤에 추가해 A/B/A'/B'를 만들고 소개 페이지에 같은 템플릿을 교체 적용했다. 각 복사본 ID가 다르며 양쪽 발행본은 그대로였다. Thymeleaf에서 재발행한 뒤에만 새 구성이 발행본과 일치했다.

## 5. 독립성

- 페이지 65의 복사 블록 제목 수정 → 템플릿 및 페이지 1 불변.
- 수정한 구성을 기존 템플릿에 저장 → 이미 적용된 페이지 1/65의 내용·revision 불변.
- 템플릿 비활성화 → 두 페이지 불변, 이후 불러오기 목록에서 제외, prepare API 차단.
- 현재 템플릿 revision이 선택 당시와 다르면 409. 조용히 다른 최신 구성을 적용하거나 덮어쓰지 않는다.

## 6. 권한

사용자 추가 답변 **“이번에는 SUPER_ADMIN만 사용”**을 적용했다.

관리와 사용은 `canManage` / `canUse`, bootstrap의 `templateManage` / `templateUse`로 분리했으며 현재는 둘 다 SUPER_ADMIN만 허용한다. Spring Security 경로와 PageTemplateService에서 이중 검사한다. ADMIN/SUPPORTER는 목록·상세·생성·수정·prepare 모두 403이다. ADMIN의 기존 페이지 편집 기능은 그대로다. 기존 역할이나 발행 정책을 변경하지 않았다. 변경/준비 요청에는 기존 CSRF 검사가 적용된다.

## 7. POSTS와 외부 참조

category/query/manual과 비활성 소스의 보관 설정도 함께 복사한다. category를 topic으로 변환하지 않는다. manual은 postIds와 순서만 보존하고 콘텐츠 제목/본문을 저장 JSON에 복사하지 않는다.

불러오기 화면과 prepare 응답에서 기존 selectedPosts 조회를 재사용해 참조의 현재 상태를 표시한다. 그 사이 상태가 바뀌면 최신 상태를 보여주고 재확인 후 적용하도록 한다. 미발행/삭제/사용 불가 ID도 지우지 않는다. 실제 공개/페이지 미리보기 결과는 기존 PublishedPostQueryService에 맡긴다.

검증 템플릿의 manual `[97,105,33,999999]`가 유지됐고, 미리보기에는 발행 가능한 97 → 105만 표시됐다. query는 REVIEW / 7기 / 프로젝트 / 최신순 3개, category는 기존 전체 분류를 보존했다. 숨김 HERO와 centered Variation도 그대로였다.

## 8. DB/API/서비스 변경

| API | 처리 |
|---|---|
| GET /api/admin/next/page-templates | 목록 |
| GET /api/admin/next/page-templates/{id} | 구성·작성 정보·현재 manual 참조 상태 |
| POST /api/admin/next/page-templates | 생성: name/description/active/blocks |
| PUT /api/admin/next/page-templates/{id} | revision 필수. blocks 생략 시 기존 구성 유지 |
| POST /api/admin/next/page-templates/{id}/prepare | revision/활성 검사 → 새 ID의 sections와 references 반환. DB 쓰기 없음 |

추가: PageTemplateService, PageTemplateStore, PageTemplateMapper.xml, NextTemplateApi, V9 SQL, PageTemplates.tsx, templateBlocks.ts, CSS, migration/통합/프런트 테스트, run-template-copy.ps1.

연결 변경: PageBlockService에 새 ID 복사 함수, NextWorkspaceApi 권한, SecurityConfiguration 및 React shell 경로, navigation/types/main/PageEditor. 기존 migration V1~V8과 PageService 저장·발행, PostService, 콘텐츠/페이지 기존 Mapper, Thymeleaf 소스는 변경하지 않았다.

V9를 추가했으므로 기존 migration 목록 기대값 테스트 3개와 등록 경로 목록 테스트를 갱신했다. V8 전용 migration 테스트는 target=8을 명시해 자신의 검증 범위를 유지했다.

## 9. Thymeleaf 호환

템플릿 적용 결과는 기존 schemaVersion 2 Section과 동일하므로 Thymeleaf에 템플릿 UI를 중복 구현하지 않았다. 실제 브라우저 임시저장/발행 후 ID·variation·visible·categoryId·query·manual 보존을 비교했다. 기존 화면의 null bodyDoc → 동등한 Quill 문서 정규화는 기존 동작으로 분리 확인했다.

## 10. 검증 및 복구

Java 전체 **123개 통과 / 실패 0 / 오류 0 / 제외 0**. 신규 템플릿 통합 7개, migration 2개다. 프런트 **39개 통과**(신규 5개), TypeScript/production build 통과. 기존 V3/V6/V7 migration 회귀와 최신 실제 V8 사본의 V9 검증을 모두 새 사본에서 실행했다.

| 필수 항목 | 근거 |
|---|---|
| 1~4 저장/재조회/필드/세 소스 | API 통합 + 실제 페이지에서 템플릿 저장 + 사본 비교 |
| 5~8 새 ID/추가/교체/확인 | 자동 테스트 + 실제 두 페이지 적용, 확인 전 DB 불변 |
| 9~10 발행본 분리/재발행 | 사본 API 전후 비교 + 실제 Thymeleaf 발행 |
| 11~14 상호 독립/두 페이지/비활성 | 통합 테스트 + 브라우저 편집/기존 템플릿 교체 저장/비활성 |
| 15~16 manual/숨김/Variation | 전체 필드 비교 + 상태 미리보기 + 새로고침 |
| 17 권한 | ADMIN/SUPPORTER 서비스·API 거부, CSRF 누락 거부 |
| 18 Thymeleaf | 실제 임시저장/발행 후 비교 |
| 19~20 POSTS/후기/FAQ/맛집 | 전체 기존 회귀 통과, 콘텐츠 18개 원본 API 완전 동일 |
| 21 재시작 | 템플릿/페이지/발행본/탐색/콘텐츠/미리보기 API 전후 완전 동일, 재로그인 후 같은 새 block ID 진입 |
| 22 자동/브라우저 | 123 + 39, 화면 저장/추가/교체/관리/비활성 및 재시작 검증 |

완료 환경은 테스트 중 교체한 페이지를 남기지 않도록 **작업 전 V8 백업의 새 사본 + V9**로 구성했다. 페이지 1/65와 발행본, 기존 block ID/등록표, 콘텐츠·메뉴·분류는 작업 전 그대로다. 검증 템플릿 두 개만 명시적으로 재등록했다: `[검증] 홈 기본 구성` 활성, `[검증] 세 가지 콘텐츠 연결` 비활성. 운영용 초기 템플릿/IA로 확정한 것이 아니며 migration seed에도 포함하지 않았다.

완료 DB의 27개 기존 테이블을 비교하면 템플릿 등록에 따른 ACTIVITY_LOG만 변경됐다. 페이지/발행본/블록 등록표를 포함한 나머지 26개 테이블은 완전히 동일하며 PAGE_TEMPLATES만 추가됐다. Flyway V1~V8 이력도 변경 없이 V9만 뒤에 추가됐다.

검증 과정의 모든 페이지 변경은 별도 verified-templates-v9 DB/JAR 쌍과 API/브라우저 증거에 남겨 두었다. 완료 DB는 `.cache/react-phase4d-data/aica-phase4d-completed.mv.db`, 실행 JAR는 `.cache/react-phase4d-test.jar`다.

복구 기준점: `.cache/checkpoints/20260927-171022-react-phase4d/`. 작업 전 source.zip/manifest, baseline-v8 DB/4C-2B JAR, 검증 상태 DB/JAR, 완료 source/DB/JAR와 체크섬, RESTORE.md를 보존한다. 해당 검증 서버를 정상 종료한 뒤 원하는 **DB/JAR 쌍**을 새 폴더에서 복구한다. 원본 DB나 현재 작업 소스를 덮어쓰거나 reset/clean하지 않는다.

## 11. 4차 이후 남은 사항

- ADMIN 템플릿 권한 확대는 이번에 하지 않았다. 향후 사용/관리 각각 별도 결정할 수 있다.
- 템플릿 구성 전용 편집 세션은 없다. 기존 PageEditor에서 구성 후 저장하는 흐름이며, 그 페이지의 기존 자동 초안 저장 정책을 따른다.
- 템플릿의 이미지/분류/콘텐츠는 ID 참조다. manual 미발행·삭제는 보존/안내/공개 제외한다. 삭제된 이미지나 사용할 수 없는 분류/컴포넌트 정의는 불러오기 재검증에서 차단한다. 템플릿 전용 미디어 사용처/삭제 보호 UI는 추가하지 않았으므로 원본 적용 전 수명 정책을 확인해야 한다.
- 템플릿 버전 이력 복원 UI, 일괄 마이그레이션, 전체 IA, 공개 홈페이지 전체 렌더링은 미구현 범위다. 템플릿 revision은 충돌 검사 용도다.
- 기존 로그인 후 딥링크 복귀 정책, H2/Flyway 호환 경고 및 파일 DB 연결 수명 위험은 유지한다. 원본 V3에 V4~V9를 적용하기 전 최신 사본과 대응 실행 파일을 이용한 별도 점검/승인이 필요하다.
