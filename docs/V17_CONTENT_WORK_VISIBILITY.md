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

(사본 검증 뒤 채운다.)
