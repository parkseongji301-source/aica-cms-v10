# 현재 상태 인수인계 (2026-10-01, 사이트 구조 단순화 적용 뒤)

새 Claude Code 세션이 이 문서만 읽고 이어서 작업할 수 있도록, 과거 일지가 아니라 **지금 상태와 다음 행동**을 적는다. 자세한 근거는 각 절의 링크 문서에 있다.

## 1. 고정 조건 (절대 바꾸지 않음)

- 전자정부 표준프레임워크 RTE 4.3.0, Spring Boot 3.4.5, Spring Framework 6.2.6, Spring Security 6.4.5, 내장 Tomcat 10.1.40, Java 17. 버전 업그레이드·다른 프레임워크·별도 백엔드 제안 금지.
- 기존 Service·Mapper·MyBatis·Flyway·H2 DB 구조, 세션·쿠키·CSRF, 권한·게시·버전·휴지통 로직을 재사용한다.
- DB는 일반적으로 설계하고 운영 한도(깊이 3단계, 유형당 대표 영역 1개 등)는 서비스·화면 규칙으로 둔다.
- 이미 push했거나 리허설한 migration은 고치지 않는다. 다음 버전(V16…)으로 추가한다.
- 특정 AICA IA 이름·구조를 제품 코드·migration에 넣지 않는다(시험 데이터에만). IA는 운영 데이터로 입력한다.
- push는 요청받았을 때만. 예상 밖 데이터 변경은 먼저 알린다. 답변은 한국어.
- 임시 검증 계정은 확인 뒤 비활성화하고, 문서에는 ID만 남긴다(비밀번호 금지).

## 2. Git

- branch `main` = origin/main(이 문서 커밋까지 push). V16 구현 `9c4c649`.
- 기준점 태그: `v13-page-hierarchy-20260930`, `react-admin-step5-20260930`, `react-admin-step4-20260930`, `v12-stable-baseline-20260930`, `v11-operating-baseline-20260929`.

## 3. 8095 운영 런타임: V16 RC1 (2026-10-01 적용)

**현재 값은 [V16_CONTENT_WORK_NODES.md](V16_CONTENT_WORK_NODES.md) 4절이 우선한다**: 선택 `{kind: v16, runtime: .cache/v16-release/V16-RC1-20261001/runtime}`, JAR `0fee384ed76e92087d01625a1b59f8263c4b1e48548958f979ea8c29d9dedb89`(commit `9c4c649`), DB `.cache/v16-release/V16-RC1-20261001/runtime/db/aica-local.mv.db`(자기 DB), receipt `MIGRATED_V16`, Flyway V1~V16. 되돌리기 대상은 V15 RC2(DB 불변, 백업 `before-V16-20261001-…`). 아래 표는 V15 RC2 기록이다.

### (이전) V15 RC2

| 항목 | 값 |
|---|---|
| 선택 파일 | `.cache/current-ui.json` = `{kind: v15, runtime: .cache/v15-release/V15-RC2-20261001/runtime}` |
| 시작·정지 | `START.cmd` / `STOP.cmd` (→ `scripts/start-current-ui.ps1`, 실행기 `scripts/start-v12-runtime.ps1`) |
| JAR | `.cache/v15-release/V15-RC2-20261001/runtime/server.jar`, sha256 `dc097b8f4b0098ee373d6e1eb3c41a347c2d4aa9b66400b26c6c43aa13b6542f` (commit `b86efa2` 빌드, 코드 기준 `4b77ce1`) |
| DB | `.cache/v15-release/V15-RC1-20260930/runtime/db/aica-local.mv.db` (RC1·RC2가 함께 쓴다. RC2 `runtime.json`의 `database`) |
| receipt | RC2 `migration-receipt.json`: `MIGRATED_V15`, 위 DB 경로·JAR 해시에 묶임. `AUTO_COMPACT_FILL_RATE=0` |
| schema | Flyway V1~V15 (V16 적용 전 기록) |
| SUPER_ADMIN | `admin@example.com` (비밀번호는 사용자에게만 있음, 문서·메모리에 기록 금지) |

### 되돌리기 기준

| 상황 | 방법 |
|---|---|
| 카테고리 데이터 전환(2026-10-01) 이후 문제 | **옛 JAR 단순 rollback 불가.** 옛 JAR(V15 RC1 `302b68ff…`)는 "전체 유형" POSTS 블록을 읽을 수는 있어도 저장하지 못한다. 정상 종료 → DB를 `.cache/v12-release/backups/aica-local.before-category-conversion-20261001-stopped-1790784063899.mv.db`로 덮어쓰기(해시 `60e1767d…` 확인) → 필요한 JAR 선택 → 기동 → 세 역할·공개 API 확인 |
| RC2 JAR 자체 문제(전환 전 상태 필요) | 위 백업 복원 뒤 `scripts/select-v12-runtime.ps1 -Runtime .cache/v15-release/V15-RC1-20260930/runtime -AuthorizeSelection` |
| V15 이전으로 | V13 RC1(`.cache/v13-release/V13-RC1-20260930/runtime`, 자기 DB 그대로, JAR `fe9a3508…`) 선택. V13 이후 운영 변경은 잃는다 |
| 백업 | `.cache/v12-release/backups/`: `before-V15-20260930-…`(V13), `before-V15RC2-20261001-…`(V15, RC2 교체 전), `before-category-conversion-20261001-…`(카테고리 전환 직전) |

운영 절차 규칙: 8095를 멈추는 작업은 사용자 승인 후. 같은 DB를 두 런타임이 동시에 쓰지 않는다(실행기가 거부). 실행기 출력은 파일로 받고 기동은 분리 실행한다(파이프·대기 금지). 검증은 먼저 사본(`.cache/*-rehearsal-*`, 포트 8097)에서 한다.

## 4. React `/admin`

- `/admin/**`이 React 관리자(정적 번들 `static/next-app`, `frontend/`에서 빌드). 기존 Thymeleaf GET 화면은 `/admin/legacy/**`(비교·복구용), 옛 `/admin-next/**`는 302로 `/admin/**`. POST 주소·데이터 API(`/api/admin/next/**`)는 그대로. [REACT_ADMIN_STEP5.md](REACT_ADMIN_STEP5.md), [REACT_ADMIN_TRANSITION.md](REACT_ADMIN_TRANSITION.md).
- 역할: SUPER_ADMIN(모든 권한·사이트 구성), ADMIN(모든 글·페이지, 사이트 구성 쓰기 불가), SUPPORTER(자기 글).

## 5. 사이트 구성 구조 (V13 → V14 → V15)

`site_pages` 한 표가 **영역(area)** 이다. [V14_SITE_COMPOSITION.md](V14_SITE_COMPOSITION.md)

| 열 | 의미 |
|---|---|
| `parent_id` / `sort_order` (V13) | 상하위와 형제 순서. 운영 한도 **최대 3단계**(서비스·화면 규칙). 홈(첫 화면)은 최상위 고정·하위 없음. 위치를 바꿔도 주소(slug)는 안 바뀜 |
| `area_kind` (V14) | `PAGE` = 실제 화면이 있는 페이지. `GROUP` = 화면 없는 묶음(이름만). GROUP은 게시·템플릿·공개 page API·첫 화면·메뉴 대상에서 모든 경로로 제외 |
| `content_type_code` (V14) | 그 콘텐츠 유형의 **대표 작업 영역**(유형당 1개는 서비스 규칙). 다른 페이지의 POSTS 블록이 그 유형 글을 쓰는 것을 막지 않음. GROUP은 연결 불가 |
| `menu_visible` / `menu_label` (V14) | 공개 메뉴 노출과 표시명(비우면 게시 제목·묶음 이름). **메뉴 숨김 ≠ 구성에서 제거** |
| `in_structure` (V15) | FALSE = 구성에서 제거. 작업 중 구성에서 빠지고 다음 구성 게시부터 `/structure`·`/menus`에서 빠진다. 페이지·게시본·글은 남는다. 제거 규칙: 구성에 남은 하위가 있으면 거부, 콘텐츠 작업 연결이 있으면 먼저 해제, 제거된 영역 아래로 이동·추가 금지 |

- 기존 V14 기본값: 모든 기존 페이지는 PAGE·연결 없음·메뉴 숨김·구성 포함.
- 현재 운영 영역: 6c절 참고(홈·인사교 소개·후기·지원·FAQ, 모두 최상위, 메뉴 숨김, 후기만 REVIEW 연결).

### 콘텐츠 작업 사이드바

`bootstrap.contentAreas`(모든 역할)로 만든다: 구성 포함 + PAGE + `content_type_code`가 있는 영역을 구성 순서대로, 상위 이름을 묶음으로 보여 주고, 그 유형에 허용된 주제(`content_type_topics`, 사전 순서)가 하위 항목이다. 주소 `?area=<pageId>&topic=<주제 코드>`. 옛 `…Section` 주소나 없어진 영역은 "전체 콘텐츠 보기 / 현재 사이트 구성 보기" 안내만 보여 준다(유형 추측 없음). **현재 운영 DB에 연결된 영역이 없어 사이드바에는 "전체 콘텐츠"만 보인다.** 카테고리 항목은 사이드바에서 뺐다.

### 구성 게시와 공개 API

- 작업 중 구성(site_pages) → "구성 게시"(전체 페이지 현황의 막대)로 스냅샷 저장(`site_structure_publications`, 영역을 pageId로만 참조, 주소·제목 미저장). 화면이 본 구성과 다르면 409. 오류(없는 상위·순환·3단계 초과·묶음 연결·비활성/중복 유형)는 거부, 경고(메뉴 0개·비공개 메뉴 페이지·빈 묶음·숨긴 상위 아래·게시 시 빠지는 현재 메뉴)는 확인 후 게시. 이력·이전 게시본 다시 게시(작업 중 구성은 그대로).
- 최신 게시본의 **모든 영역**(메뉴 숨김 포함)은 영구 삭제 보호. 삭제 순서: 구성에서 제거 → 구성 게시 → 삭제.
- `GET /api/public/v1/menus`: **구성 게시본이 없으면 기존 `site_menus` 출력 그대로(fallback)**. 첫 게시 뒤에는 게시된 구성의 메뉴 노출 영역(트리, `key`·`parentKey`) + LINK 메뉴(최상위 끝). CATEGORY 메뉴는 그때 빠진다.
- `GET /api/public/v1/structure`: 게시본이 없으면 `publishedAt:null, items:[]`. 링크·이름은 읽을 때 현재 페이지 게시본으로 정하고, 공개되지 않은 페이지·묶음은 공개된 하위가 있으면 링크 없는 이름(`kind:GROUP`), 없으면 뺀다. 없어진 영역은 빼고 하위를 올린다(방어 로직).
- 첫 게시 뒤 메뉴 관리 화면은 LINK만 추가·수정(서버도 거부). [PUBLIC_API_V1.md](PUBLIC_API_V1.md)
- "현재 메뉴에서 가져오기": 기존 PAGE 메뉴의 노출·순서를 작업 중 구성으로 옮김(미리보기 뒤 반영, 게시 안 함).

**첫 구성 게시는 아직 하지 않았다.** 운영 게시본 0건 → 공개 메뉴는 fallback(홈, 인사교 소개×2, 공지사항×2 CATEGORY).

## 6. 레거시 카테고리 퇴출 (진행 중)

[LEGACY_CATEGORY_RETIREMENT.md](LEGACY_CATEGORY_RETIREMENT.md)

- 후기(REVIEW)·FAQ는 콘텐츠 유형이다. 카테고리 이관 대상이 아니다.
- 코드: 카테고리 **신규 사용 차단**(글·POSTS 블록·템플릿에 새로 지정 불가, 기존 값 유지·해제만). 카테고리 행은 읽기 전용(추가·이름·정렬·삭제 거부). 버전 이력은 옛 카테고리를 보여 주되 복원은 되살리지 않음. POSTS 조건에 **전체 유형**(`typeCode: null`) = 옛 "카테고리 없음" 방식과 같은 결과.
- 운영 데이터(2026-10-01 적용 완료): 홈 "사관학교 소식" 블록·인사교 소개 초안 블록·템플릿 2개를 전체 유형으로 전환(홈 재게시, 공개 글 목록 동일), #33 "아아"의 카테고리 해제(GENERAL 유지).
- **남은 category 3행**: #1 공지사항, #2 교육 소식, #33 카테고리1. 삭제하지 않은 이유: #1은 CATEGORY 메뉴 2개(#34·#65, 중복)가 아직 공개 메뉴(fallback)로 쓰고 있고, #2는 #33의 기준 버전 스냅샷(#9)이 참조한다. 참조와 버전 복원 호환이 정리된 뒤 별도 단계에서 삭제한다.
- **CATEGORY 메뉴 2개를 남긴 이유**: 구성 게시 전까지 공개 메뉴의 출처가 `site_menus`이고, 사용자가 첫 게시 전에는 보존하라고 했다. 첫 구성 게시 때 공개 메뉴에서 빠지고, 그 뒤 메뉴 관리에서 행을 삭제할 수 있다.
- **GENERAL_NOTICE**: 주제 #11 "공지", GENERAL에 연결됨(운영 DB). 아직 글·블록·영역 어디에도 쓰이지 않는다. 공지사항 영역을 만들 때 POSTS 조건 `GENERAL + GENERAL_NOTICE`로 쓴다. 추가에 쓴 `POST /api/admin/next/classifications/topics`(추가만)는 과도기 수단 → 정식 기수·주제 관리 UI에 흡수할 후속 과제.

## 6b. V16 콘텐츠 작업 하위 항목 (8095 적용 완료)

[V16_CONTENT_WORK_NODES.md](V16_CONTENT_WORK_NODES.md). 최종 검수 범위로 사용자가 정한 것: 유형 연결만으로 사이드바 하위 항목을 만들지 않고, 관리자가 구성 대화상자에서 하위 항목(이름·기존 주제·순서)을 직접 추가·제거한다. 항목에서 목록·새 글 작성, 항목 제거는 글·주제와 무관. 새 표 `content_work_nodes`, `V16PromotionTool`, `promote-v16-runtime.ps1`. 사본 검증 뒤 8095 적용 완료. 5절의 콘텐츠 작업 사이드바 설명은 이제 "연결된 페이지 + 저장된 하위 항목"이다(주제 자동 하위 항목 없음). **범위 고정**: 페이지별 하위 항목 1단계, 항목당 주제 하나. 다단계 탐색 트리·복합 조건 빌더로 확대하지 않는다. 운영 DB에는 사용자가 만든 #98(후기 연결, 게시됨)이 있고 하위 항목은 0개다.

## 6c. 사이트 구조 화면 단순화 (8095 적용 완료) · 용어 정돈

화면 이름은 이제 **사이트 구조**(옛 전체 페이지 현황)이고, 버튼은 내용 편집·블록 보기·옮기기·설정, 반영은 "홈페이지에 반영 (구성 게시)"이다([SITE_STRUCTURE_UX.md](SITE_STRUCTURE_UX.md) 7절). 이 문서의 다른 절에서 "전체 페이지 현황"은 같은 화면이다. 후기(#98, REVIEW)에는 하위 항목 생활·수업·프로젝트, FAQ(#100, FAQ 연결)에는 준비사항·수업·프로젝트가 있다.

[SITE_STRUCTURE_UX.md](SITE_STRUCTURE_UX.md). 최상위는 고정 구획(홈 완전 고정), `+ 하위 페이지`(빈/모음/템플릿), 끌어서 순서·이동. 프런트만(commit `cad7cde`, JAR `db919f60…`). 최상위 생성·삭제 기능은 백엔드에 남기고 UI에서만 숨김 — 실제 IA가 확정되면 개발 단계에서 운영 데이터로 세팅한다. **현재 8095: V17 RC1**(`.cache/v17-release/V17-RC1-20261001/runtime`, JAR `442977c4…`, 자기 DB, Flyway V1~V17; 되돌리기 대상 V16 RC3 + V16 RC1 DB). [V17_CONTENT_WORK_VISIBILITY.md](V17_CONTENT_WORK_VISIBILITY.md): 페이지 설정의 "콘텐츠 작업에 보이기"를 켠 페이지만 콘텐츠 작업 사이드바에 보인다(글 종류 없어도 됨). 후기·FAQ만 켜져 있다. 최상위: 홈 #1 · 인사교 소개 #65 · 후기 #98(`reviews`, 게시, REVIEW 연결) · 지원 #99(`support`, 초안) · FAQ #100(`faq`, 초안). #97은 삭제됨. 되돌리기 대상 V16 RC1(같은 DB) / 데이터는 백업 `before-site-ux-20261001-…`.

## 7. 아직 하지 않은 것

- 실제 IA 입력(영역·묶음·상하위·콘텐츠 작업 연결·메뉴 노출). IA는 확정되지 않았다. 추측해서 만들지 않는다.
- 첫 구성 게시.
- CATEGORY 메뉴·카테고리 행 정리.
- 사이트 구성 화면 등의 UX·용어·디자인 정돈("콘텐츠 작업 연결" 같은 명칭 포함). 기능 완성 뒤 한 번에 한다. 그 전에는 화면 문구·배치를 손대지 않는다.
- 기수·주제 관리 UI.

## 8. 하면 안 되는 것 / 주의

- 승인 없이 8095 정지·DB 변경·첫 구성 게시·카테고리 행 삭제 금지.
- 카테고리 전환 이후에는 옛 JAR만으로 되돌리지 않는다(백업 복원 필요, 3절).
- 구성 게시는 공개 메뉴를 바꾼다: 첫 게시 순간 CATEGORY 메뉴 2개와 메뉴 노출이 꺼진 페이지가 공개 메뉴에서 빠진다. 게시 전 대화상자의 "지금 메뉴 ↔ 게시 후 메뉴"를 반드시 확인한다.
- V14·V15 migration 파일 수정 금지. schema 변경이 필요하면 V16 이후 + 사본 리허설.
- 운영 DB는 실행 중 잠겨 있다. 읽기 조사는 차가운 사본(백업·`promotion-stage`)이나 익명 공개 API로 한다.
- 제품 코드·migration에 AICA IA 이름을 넣지 않는다.

## 9. 다음 작업 순서

0. **V16 사용 확인**: 사용자가 직접 #98 등에 하위 항목 추가 → 콘텐츠 작업 사이드바·목록·새 글 작성 확인.
1. **사이트 구조 입력**: 사용자에게 실제 IA를 받는다(없으면 사용자가 승인한 임시 구성). 전체 페이지 현황에서 영역·묶음 추가, 상하위·순서, 메뉴 노출·표시명. 운영 데이터 변경이므로 입력 전 백업·승인.
2. **콘텐츠 작업 영역 연결**: 유형별 대표 작업 영역 지정(예: 후기·FAQ·맛집·일반). 해당 페이지에 그 유형의 POSTS 블록(공지사항은 `GENERAL + GENERAL_NOTICE`) 구성·게시. 사이드바에 영역·주제가 나타나는지 확인.
3. **첫 구성 게시 전 확인**: 구성 게시 대화상자의 오류·경고, "지금 메뉴 ↔ 게시 후 메뉴", 빠지는 현재 메뉴(CATEGORY 2개 등)를 사용자와 확인. "현재 메뉴에서 가져오기" 필요 여부 판단.
4. **첫 구성 게시**: 승인 후. 공개 `/menus`·`/structure`·주요 페이지 확인, 정상 종료·재시작 확인.
5. **정리**: 공개 메뉴에서 빠진 CATEGORY 메뉴 행(#34·#65)·중복 PAGE 메뉴(#2) 삭제, 카테고리 참조·버전 복원 호환 확인 뒤 카테고리 행 삭제 여부 결정(별도 승인). 이후 카테고리 관리 화면·공개 `categoryId` 계약 정리 검토.
6. **마지막 UX·용어·디자인 정돈**: 사이트 구성·콘텐츠 작업·메뉴 화면의 명칭·설명·배치를 한 번에 정리. 기수·주제 관리 UI(주제 추가 API 흡수) 포함 여부 결정.

## 10. 개발 도구 메모

- JDK: `트랜스퍼 0930/실행본/AICA_V11_20260929/runtimes/jdk17` (`JAVA_HOME`). Maven: `MAVEN_USER_HOME=.cache/maven-user-home`, `./mvnw.cmd -B -ntp -o test|package`.
- 프런트(`frontend/`): Codex 번들 node(`%LOCALAPPDATA%/OpenAI/Codex/runtimes/cua_node/*/bin/node.exe`)로 `node --experimental-strip-types --test tests/*.test.ts`, `node node_modules/typescript/bin/tsc --noEmit`, `node node_modules/vite/bin/vite.js build`(→ `src/main/resources/static/next-app`, git 제외). 소스 간 값 import는 `.ts` 확장자를 붙인다.
- 미리보기: JAR를 `--spring.profiles.active=design-preview --server.address=127.0.0.1 --server.port=8081`로 실행(메모리 DB, 로그인 1234/1234).
- 런타임 스크립트(`scripts/`): `promote-v15-runtime.ps1`(V13/V14 → V15 사본 적용), `swap-v12-jar.ps1`(같은 schema JAR 교체), `relocate-v12-runtime.ps1`(사본 이관), `select-v12-runtime.ps1`, `start-v12-runtime.ps1`. PowerShell 5.1 스크립트는 한글이 있으면 UTF-8 BOM이 필요하다.
- 비활성화된 임시 계정: #33~#46(각 단계 확인용). 이후 확인은 사용자가 제공한 SUPER_ADMIN 계정으로 했다(비밀번호는 기록하지 않음).
