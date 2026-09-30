# V17 콘텐츠 작업에 보이기 (2026-10-01)

## 1. 결정 (사용자)

페이지 설정의 "홈페이지 메뉴에 보이기" 옆에 **"콘텐츠 작업에 보이기"**를 두고, 체크한 페이지만 왼쪽 콘텐츠 작업 메뉴에 보인다. 관리할 글 종류나 하위 항목이 없어도 그냥 보이기만 한다(항목을 누르면 페이지 내용 편집으로 간다).

## 2. 구조

| 항목 | 내용 |
|---|---|
| DB | `V17__content_work_visibility.sql`: `site_pages.content_work_visible BOOLEAN NOT NULL DEFAULT FALSE`. 기존 행은 FALSE(운영자가 페이지별로 켬). 다른 변경 없음 |
| 적용 도구 | `V17PromotionTool` plan/migrate: 사본만, 열 하나만 추가, 모든 행 FALSE 확인, 기존 데이터 지문 불변. receipt `MIGRATED_V17`. `CURRENT_VERSION` 17, `AICA_V17_PROMOTION_ENABLED`로 웹 서버 시작 불가 |
| 서비스 | `PageService.compose`에 `contentWorkVisible`(생략 시 유지). 활동 이력 "콘텐츠 작업에 보이기/숨기기". `contentAreas`는 구조 안의 PAGE 중 이 값이 TRUE인 페이지(글 종류 유무와 무관, `typeCode`는 null 가능) |
| API | `PUT /pages/{id}/composition`에 `contentWorkVisible`, `PageRow`·bootstrap `pages`에 같은 필드 |
| 화면 | 설정 대화상자 "홈페이지 메뉴" 구역에 "콘텐츠 작업에 보이기" 체크. 글 종류를 고르면 자동으로 켜짐(저장 전 끌 수 있음). 미리보기 "→ 콘텐츠 작업 메뉴에 ‘후기’ (후기 글/내용 편집)이 보입니다". 종류는 있는데 꺼져 있으면 안내. 목록 메타 "콘텐츠 작업에 보임: 후기 · 하위 항목 N개" / "글 종류: 후기 (콘텐츠 작업에 숨김)" |
| 사이드바 | 글 종류 없는 항목은 페이지 내용 편집으로 이동(하위 항목 없음). 글 종류가 있으면 이전과 같음 |
| 스크립트 | `promote-v17-runtime.ps1`(V16 → V17), 선택·기동·JAR 교체 스크립트가 `MIGRATED_V17`·`v17` 수용 |

## 3. 검증

코드 commit `55d6b6d`, JAR sha256 `442977c41aff2ba9f975658caceb807294768fade227304ce3a211ea6bc931e9`(서버 전체 테스트 뒤 같은 실행에서 package).

- 서버 전체 235개, 실패 0, 환경 조건 제외 6. `ContentWorkVisibilityMigrationTest`(2): V16 파일 DB 사본 plan/migrate, 원본 불변, 기존 행·게시본 불변, 모든 행 FALSE, NULL 거부, V16 receipt 거부, 바뀐 사본·V15 DB 거부. 기존 시험: Flyway 이력 17, 승인 migration 목록, 구성 요청에 `contentWorkVisible`(연결 시 true), Page 레코드 필드. 첫 실행에서 메뉴 가져오기 저장이 새 열을 빠뜨려 500이 났고 `SiteStructureService`에 열을 더해 고쳤다.
- 프런트 82개, 타입 검사·빌드 통과.

### 사본 검증 (8097)

`.cache/site-ux-rehearsal-20261001/rc3/runtime`(V16, 정상 정지) → `promote-v17-runtime.ps1` → `v17/runtime`(`MIGRATED_V17`, 이력 17, 원본 불변). 공개 API 5개 동일. 이관 직후 모든 페이지 FALSE, 사이드바 비어 있음 → 후기·FAQ 켬 → 사이드바에 두 항목(FAQ는 글 종류 없이 단순 항목) → 지원 켬/끔 → 후기 끄면 사이드바에서 빠지되 REVIEW 연결·하위 항목 유지 → 다시 켬. 활동 이력 "콘텐츠 작업에 보이기/숨기기".

## 4. 8095 적용 결과 (2026-10-01)

사용자 승인으로 적용했다.

| 순서 | 결과 |
|---|---|
| 적용 전 기록 | 공개 API 9개 저장(`.cache/v17-release/apply-20261001/before-*.json`) |
| V16 RC3 정상 종료 | `stopped-1790795907978.json`(이력 16, DB `552d9241…`) |
| 백업 | `.cache/v12-release/backups/aica-local.before-V17-20261001-stopped-1790795907978.mv.db` + 검사 파일, 해시 일치 |
| `promote-v17-runtime.ps1` | `.cache/v17-release/V17-RC1-20261001/runtime`(JAR `442977c4…`, commit `55d6b6d`, 자기 DB) `MIGRATED_V17`, 이력 17. V16 RC1 DB 해시 `552d9241…` 불변 |
| 선택·기동 | `current-ui.json` = `{kind:v17, runtime:.cache/v17-release/V17-RC1-20261001/runtime}` |
| 공개 API | 9개 교체 전후·재시작 후 동일 |
| 운영 데이터 | 이관 직후 모든 페이지 FALSE(사이드바 비어 있음) → 후기(#98)·FAQ(#100) "콘텐츠 작업에 보이기" 켬 → 사이드바 후기 › 생활·수업·프로젝트, FAQ › 준비사항·수업·프로젝트 |
| 정상 종료 → 재시작 | 통과, 사이드바·공개 API 동일 |

되돌리기: V16 RC3 선택(V16 RC1 DB 그대로). V17 이후 운영 변경은 잃는다.
