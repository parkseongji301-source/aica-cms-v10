# V12 안정 기준점

2026-09-30 사용자 요청으로 V12를 정식 안정 기준점으로 정했다. 이후 Thymeleaf → React 전환 작업의 기능·schema·권한 비교 기준은 이 문서다. [V11 운영 기준점](V11_OPERATING_BASELINE.md)과 그 rollback 자료는 수정하지 않고 보존한다.

## Git 기준점

기준 태그는 `v12-stable-baseline-20260930`(annotated)이다. V11 태그 `v11-operating-baseline-20260929` 이후 커밋은 다음과 같다. 앞의 세 개는 UI만 바꿔 V11 JAR에서도 동작하고, V12 서버 커밋부터 schema V12가 필요하다.

| 순서 | 내용 | 서버·DB |
|---|---|---|
| 5d8ad04, 40f3c85 | 2026-09-29~30 V11 UI 정리와 목록 선택 삭제 | 없음 |
| U1 | 메뉴 관리 선택 삭제 제거 | 없음 |
| U2 | '공통 구조 관리'·'페이지 템플릿' 명칭, 공통 스타일 메뉴 숨김 | 없음 |
| U3 | React 새 페이지 생성(기존 `save-json`) | 없음 |
| V1 | 글쓰기 템플릿 서버·API·V12 migration, `V12PromotionTool`, 실행 guard V12 | **V12 필요** |
| V2 | 글쓰기 템플릿 React 화면 | V1 API 사용 |
| V3 | V12 실행 스크립트, 이 문서와 README·5D 문서 갱신 | 없음 |

커밋 SHA는 태그 설명과 `git log v11-operating-baseline-20260929..v12-stable-baseline-20260930`으로 확인한다. 각 커밋은 별도 worktree에서 검증했다.

| 커밋 | 프런트 테스트 | 타입 검사·빌드 | 서버 테스트 |
|---|---|---|---|
| U1, U2 | 54/54 | 통과 | 서버 변경 없음 |
| U3 | 57/57 | 통과 | 174개 실행, 실패 0, 환경 조건 제외 6 |
| V1 | 57/57 | 통과 | 178개 실행, 실패 0, 제외 6 |
| V2 | 61/61 | 통과 | 178개 실행, 실패 0, 제외 6 |

V3는 문서와 실행 스크립트만 바꾸므로 소스(`src`, `frontend`, `pom.xml`)는 V2와 같다.

## V12 RC1 실행물

| 항목 | 값 |
|---|---|
| 위치(Git 제외) | `.cache/v12-release/V12-RC1-20260930/` |
| JAR | `runtime/server.jar` |
| JAR SHA-256 | `3ba3a70199c66eadf3e239b820b355bef5f33eb3fdd2502f84b08fd221b39eaf` |
| 소스 | V2 커밋을 새 worktree에서 Vite 빌드 후 `mvnw -o package` (JDK 17.0.20.1, Node 24.21.0) |
| 정상 종료 도구 | `runtime/graceful-stop.jar` `d4365a44e5803160a131794813001175b67f14ed86e1cfc315c125b1766867c7` |
| 로그인 스타일 오버레이 | `runtime/assets/css/flow.css` `e94da365205d143e898f0c6dc0d20df819f714087fd649a50daa3e3f0a39f96f` |
| 종합 결과 | `acceptance.json`, 증거는 `evidence/` |

React 화면은 JAR 내부 파일을 쓴다. Thymeleaf 로그인 화면은 `/css/flow.css`만 불러오므로, 지금까지 모든 UI release와 같이 `css/flow.css` = 소스 `src/main/resources/static/css/flow.css` + 빈 줄 + `frontend/public/login-shell.css`를 오버레이로 제공한다. 두 원본은 Git에 있고 오버레이 해시는 실행 전 검사한다. 로그인 화면이 React로 옮겨지면 이 오버레이는 필요 없다.

8095에서 실행 중인 V12 개발 실행본(`.cache/runtime-v12-writing-templates`, JAR `8e18dbb5…`)과 비교했다(`evidence/rc-vs-development-jar.json`). Java 클래스와 라이브러리는 모두 같다. `WritingTemplateMapper.xml`은 줄바꿈만 다르다. 개발 JAR에는 이전 빌드의 사용하지 않는 UI 파일이 남아 있었고, 실제로 제공된 개발 오버레이 UI(`index-nIyg0aCm.js`, `index-DF4G3WZw.css`, `index.html`, 글꼴)는 RC JAR 내부 파일과 바이트 단위로 같다.

## Flyway V1~V12

| 버전 | 스크립트 | checksum |
|---|---|---|
| 1 | V1__backoffice.sql | 379266581 |
| 2 | V2__cms.sql | -925664058 |
| 3 | V3__rich_editor.sql | -1610808639 |
| 4 | V4__content_classification_schema.sql | 1414704998 |
| 5 | V5__content_classification_baseline.sql | 1744004732 |
| 6 | V6__content_classification_constraints.sql | -285908275 |
| 7 | V7__restaurant_details.sql | 908107551 |
| 8 | db.migration.h2.V8__page_block_identity | 804202609 |
| 9 | V9__page_templates.sql | -148566281 |
| 10 | V10__document_versions.sql | -1567156257 |
| 11 | V11__post_trash.sql | 502117418 |
| 12 | V12__writing_templates.sql | 1278504379 |

V1~V11은 V11 receipt와 같고, V12는 개발 실행본 receipt와 같다. pending 0, validate 성공.

## 사본 리허설 (2026-09-30)

1. 정상 종료된 V11 원본(`트랜스퍼 0930/실행본/AICA_V11_20260929/working/runtime/db/aica-local.mv.db`, SHA-256 `31a17a4d…8112`)이 잠겨 있지 않고 V12 개발 사본을 만든 뒤 바뀌지 않았음을 확인했다.
2. 바이트 단위로 같은 새 사본을 만들고, RC JAR의 `V12PromotionTool plan` → `migrate`로 V12만 적용했다. receipt는 `MIGRATED_V12`, 기존 36개 테이블의 열·행 fingerprint 보존, 기본 글쓰기 템플릿 1개(전체 37개 테이블), RC JAR 해시에 묶였다.
3. 공용 실행 스크립트 `scripts/start-v12-runtime.ps1`로 127.0.0.1:8097에서 2회 시작·정상 종료했다. 매회 결과:
   - `V12 file runtime`, `AUTO_COMPACT_FILL_RATE=0`, `migration=validate-only` 로그
   - `/login` 200, `/api/public/v1/menus` 200, 익명 `/api/admin/next/writing-templates`·`/manage` 401, 익명 화면 경로 302(1회차 측정)
   - 로그인 스타일 오버레이 제공
   - GracefulStop 정상 종료 → 독점 잠금 확인 → `V12PromotionTool inspect`의 migration 이력과 37개 테이블 fingerprint가 receipt와 일치
4. 리허설 전후 V11 원본 해시가 같다. 8095의 V12 개발 실행본과 그 DB는 건드리지 않았다.

리허설 도중 측정 도구 문제가 두 번 있었다. 서버 결과와는 무관하다. 1회차 cold 비교가 JSON 필드 순서 때문에 불일치로 나왔으나 값 단위로 다시 비교해 일치를 확인했다. 2회차의 실행 중 로그 읽기 실패와 화면 경로 측정 오류는 종료 후 로그로 확인했다. 내용은 `acceptance.json`의 `corrections`에 남겼다.

## 이 PC의 실행 상태

- 8095: **V12 RC1**(`.cache/v12-release/V12-RC1-20260930/runtime`, JAR `3ba3a701…9eaf`)이 실행 중이다. `.cache/current-ui.json`은 `{"kind":"v12","runtime":".cache/v12-release/V12-RC1-20260930/runtime"}`, `runtime.json`의 포트는 8095다. 실행 중인 PID는 같은 폴더의 `active.json`에서 확인한다.
- 2026-09-30 14:31 전환 기록(사용자 승인):
  1. 개발 실행본을 `STOP` 경로로 정상 종료하고 cold 검사했다.
  2. 개발 DB와 개발 receipt·RC1 DB의 fingerprint를 비교했다. **정정(같은 날 17시 확인)**: 당시 비교 스크립트의 함수 이름 `Diff`가 PowerShell 기본 별칭 `diff`(Compare-Object)에 가려져 실제로는 비교하지 않았고, "차이 없음"은 잘못된 결과였다. 개발 DB에는 migration 뒤 13:28~13:55에 쓴 7건(글 #137 신규, 글 #135 수정, 페이지 #97 임시저장·버전 2건, 글쓰기 템플릿 #1 저장 2회)이 있었고 RC1 DB에는 없다. 사용자가 7건 모두 QA·테스트 데이터로 확인해 RC1로 옮기지 않기로 했다. 개발 DB는 그대로 보관한다.
  3. RC1 `runtime.json` 포트를 8095로 바꾸고 `current-ui.json`을 RC1로 바꾼 뒤 시작했다. 이전 설정은 `.cache/current-ui.before-rc1.json`에 보관했다.
  4. 시작 후 `V12 file runtime`·`AUTO_COMPACT_FILL_RATE=0`·validate-only 로그, `/login` 200, 공개 메뉴 API 200, 익명 글쓰기 템플릿 API 401, 로그인 스타일 제공을 확인했다.
- V12 개발 실행본(`.cache/runtime-v12-writing-templates`)은 **백업으로만 보관**한다(같은 폴더의 `ARCHIVED.json`, DB SHA-256 `5301809b…19b1`). 운영에 다시 쓰지 않는다. RC1은 전환 뒤 메뉴 변경 등 새 작업이 있어, 개발 DB로 되돌리면 그 내용이 빠진다. `.cache/current-ui.before-rc1.json`은 기록용이다.
- `START.cmd`/`STOP.cmd` → `scripts/start-current-ui.ps1` → V12면 `scripts/start-v12-runtime.ps1`. 이 스크립트는 JAR·정상 종료 도구·오버레이 해시와 포트 사용 여부를 확인하고 receipt와 validate-only로 시작한다. 강제 종료하지 않는다.

## 보존 자료와 되돌리기

| 자료 | 위치 | 확인 값 |
|---|---|---|
| V11 Git 기준점 | 태그 `v11-operating-baseline-20260929`(03035cc) | 변경 없음 |
| V11 이관 묶음 원본 | `트랜스퍼 0930/AICA_V11_회사이동_20260929.zip` | SHA-256 `62c7726e…9314b0` |
| V11 실행본·DB | `트랜스퍼 0930/실행본/AICA_V11_20260929/working/runtime` | DB `31a17a4d…8112`(정상 종료 상태) |
| V11 복귀용 실행 설정 | `.cache/previous-ui.json`(V11 실행본 + 당시 UI release) | 변경 없음 |
| V12 개발 실행본·DB | `.cache/runtime-v12-writing-templates` | receipt `MIGRATED_V12`, JAR `8e18dbb5…` |
| V12 RC1 | `.cache/v12-release/V12-RC1-20260930` | 위 표 |

- V12 → V11로 되돌릴 때: V12 서버를 정상 종료하고, `.cache/current-ui.json`을 `previous-ui.json`의 V11 실행본으로 되돌린 뒤 시작한다. V11 DB는 V12 migration 전 상태 그대로다. 단, V12에서 새로 작성한 내용은 V11 DB에 없으므로 되돌리기 전에 V12 DB를 보관하고 차이를 확인한다.
- V12 DB를 V11 JAR로, V11 DB를 V12 JAR로 열 수 없다(버전·receipt 검사로 시작 거부).
- 소모된 migration plan(`*.spent`)을 다시 쓰지 않는다. 새 사본에는 새 plan을 만든다.
- 운영 DB·JAR·계정 자료는 Git에 넣지 않는다.
