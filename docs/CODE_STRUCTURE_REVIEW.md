# 코드 구조 점검: 기능별 분리 상태와 정비안 (2026-10-01)

사용자 질문: "코드를 보수하기 편하게 기능별로 분리해 놓았다고 했는데 잘 안 돼 있을 것이다. 파악해서 어떻게 해야 할지 제안해 달라."

방법: 영역 7개(cms 패키지, 레거시·React 공존, 데이터 접근, 패키지 의존, 프런트, 테스트·문서, 변경 이력)를 각각 독립 검토자가 읽고 파일·줄 근거를 붙였다. 반박 검증은 세션 한도 때문에 '변경 이력' 영역 11건만 끝났다(아래 표에 반영). 나머지 영역의 지적은 근거 인용은 있으나 교차 검증은 되지 않았다. 코드는 바꾸지 않았다.

## 1. 진단 한 줄

**규칙은 서비스로 나뉘어 있지만, 폴더·데이터 접근·화면 파일·테스트는 기능이 아니라 '만든 시기'와 '기술 층'을 따른다.** 그래서 한 기능을 고칠 때 열어야 하는 파일이 기능 경계를 넘는다.

잘 된 것(그대로 둘 것):
- 레거시 Thymeleaf와 React가 **같은 서비스 한 벌**을 호출한다. 권한·버전·감사 규칙은 서비스 안에 있다.
- 순수 규칙 모듈이 분리돼 테스트된다: `SiteStructure.java`, `PageHierarchy.java`, `ContentNodeService`(V16, 경계가 주석에 명시), 프런트 `pageHierarchy.ts`·`contentNavigation.ts` 등 19개 모듈.
- 마이그레이션 안전장치(차가운 사본, 해시, receipt)와 통합 테스트 236개는 재배치 작업의 안전망이다.
- `MvpConfiguration`의 `@ComponentScan("...mvp")`와 `classpath:mapper/*.xml` 패턴 덕분에 **패키지 이동과 매퍼 XML 분할에 설정 변경이 필요 없다.**

## 2. 영역별 상태

| 영역 | 상태 | 핵심 |
|---|---|---|
| 백엔드 패키지 의존 | 나쁨 | 14개 중 10개 패키지가 서로 도달 가능한 한 덩어리. 양방향 순환 7쌍(cms↔version 7/7이 최다). 원인은 cms 안의 기반 코드(ActivityService·CmsAccess·CmsRules·CmsStore.lock)를 모두가 쓰기 때문 |
| `mvp/cms` | 나쁨 | 27파일 2,362줄(전체 37%)에 기능 10개 + 기반 코드. `PageService` 7책임, `SiteStructureService` 4책임(공개 API 조립·메뉴 가져오기 포함). `CmsModels` 한 파일에 레코드 20개 |
| 데이터 접근 | 나쁨 | `CmsMapper.xml` 하나에 statement 97개(전체 56%), 글 발행 SQL 13개와 버전 SQL 4개까지 포함. `store.one("문자열", values(...))` 호출 약 173곳: statement 오타·키 오타가 컴파일에 안 잡히고 빠진 키는 NULL로 바인딩. 반면 Post·Account·Version 매퍼는 타입 있는 메서드로 모범 |
| 레거시 ↔ React | 보통 | 서비스는 공유. 그러나 React가 레거시 엔드포인트 3종(`/admin/pages/save-json`, `/admin/media/upload`, `/admin/media/{id}/file`)에 의존해 레거시를 못 지움. **`NextVersionApi`·`NextTemplateApi`는 `NextApiErrors` 대상에서 빠져 409/400이 HTML로 나가고 React는 "로그인 확인"으로 오안내**(코드로 판단, 실행 검증 안 함) |
| 프런트 | 보통 | 로직 .ts 모듈은 좋음. 화면은 `ReadPanels`/`EditPanels`(조회/편집 기준 묶음, 이름과 동작 불일치), `main.tsx` 16-case switch + 패널 import 15개, 편집기 주소 정규식 7곳 복제. CSS 11층이 같은 선택자 220개를 반복 덮어씀. 줄당 최대 4,056자. 컴포넌트 테스트 0 |
| 테스트·문서 | 보통 | 테스트 수와 이름은 좋으나 `integration` 한 폴더에 41개, 클래스명이 단계·버전(NextAdmin, V17…). 페이지 삭제 규칙이 8개 클래스에 분산. 버전 핀 리터럴이 테스트 3곳·스크립트 5곳에 복사 — **`relocate-v12-runtime.ps1`은 V15에서 멈춰 V16·V17 런타임 이관을 거부(실제 드리프트)**. 문서는 작업 일지 위주, '사이트 구조' 현재 설계는 8개 문서 1,230줄에 분산, README·5D 가이드는 V12 기준 |
| 변경 이력 | 보통 | 마이그레이션 커밋(V16·V17)만 33파일로 퍼지고 기능·UX 커밋은 2~7파일로 좁다(검증자 판정: 확산은 '스키마 버전 리터럴·승격 도구 복제·Page 레코드 복제'가 원인, 기능 결합은 아님). 핫스팟: `PagesPanel.tsx` 10회, `PageService.java` 7회, `SecurityConfiguration.java` 5회(무관한 기능 5건) |

### 반박 검증에서 바뀐 것(변경 이력 영역)
- 보안 설정에 경로를 빠뜨리면 SUPER_ADMIN 전용 변경이 노출된다 → **틀림**. 구조 변경 서비스가 모두 `access.structure(principal)`을 자체 호출한다. 파일이 자주 바뀌는 사실만 남는다(낮음).
- 컬럼 1개가 33파일로 퍼진다 → 사실이나 **medium**. 비마이그레이션 변경은 좁다.
- 승격 도구 4벌 복제 → 사실이나 **medium**(복제는 결합이 아니라 유지 비용).
- 글꼴 목록 3곳 복제 → **low**.
- 버전 리터럴 분산, `pageComposition` UPDATE가 4컬럼을 한꺼번에 덮어씀 → **유지**(후자는 medium).

## 3. 정비안

원칙: 스택·버전·eGovFrame·MyBatis·Flyway·세션 인증은 그대로. 멀티모듈·JPA·프레임워크 교체·URL 변경 없음. 각 단계는 **동작 불변**이고 끝에 전체 테스트 + JAR 해시 기록을 남긴다. 운영 DB·receipt 체계와 무관하므로 8095 적용은 JAR 교체로 끝난다.

### 0단계 — 지금 바로 (작음, 반나절, IA·홈페이지와 무관)
1. `scripts/relocate-v12-runtime.ps1:65` 수용 목록을 V17까지(또는 숫자 비교로) 고친다. 실제 드리프트.
2. `NextApiErrors`를 `@RestControllerAdvice(basePackageClasses=NextApiErrors.class)`로 바꿔 next 패키지 전체에 적용. `WebAdvice`는 레거시 뷰 컨트롤러 패키지로 한정. 템플릿·버전 테스트에 JSON `code` 단언 추가.
3. 와일드카드 import 45개·static 와일드카드 26개·본문 완전수식명 16건을 명시 import로(기계적, 한 커밋). 이후 패키지 이동 때 IDE 리팩터링이 정확해진다.
4. 버전 핀 단일 출처: 테스트 3곳의 `containsExactly("1",…,"17")`과 `ClassificationMigrationTest`의 스크립트 목록을 `FileDatabaseSafety.CURRENT_VERSION`에서 생성. `FileRuntimeConfiguration`의 플래그 7줄은 "AICA_*_PROMOTION_ENABLED가 true면 거부"로 일반화. PowerShell 4개는 `MIGRATED_V(\d+)` 숫자 비교.

### 1단계 — 다음 마이그레이션(V18) 전 (중간, 1~2일)
5. 승격 도구 공통화: `SchemaPromotion(from,to,script,허용 변경,사후 검사)` 하나 + 버전별 spec. V11~V17 기존 클래스는 3줄 위임으로 남겨 스크립트·receipt 호환. `promote-runtime.ps1 -From 17 -To 18`로 통합. 마이그레이션 테스트 공통 베이스 클래스.
6. 테스트 fixture: `TestPages.page(id,title)…build()` 빌더, 공용 `AdminHttpTestSupport`(세 역할 계정·login/csrf·표 초기화 한 벌), `composition()` 헬퍼 통합. 프런트 `tests/fixtures.ts`에 `pageRow(overrides)`.
7. `NextWorkspaceApi.PageRow`는 `of(CmsModels.Page)` 한 곳에서 매핑.
   효과: 다음 컬럼 추가가 "SQL + 레코드 + 매퍼 조각 + 화면" 수준으로 줄어든다. 측정: V18 커밋의 파일 수를 V17(33)과 비교.

### 2단계 — 백엔드 기능 경계 (큼, 3~5일, 별도 '정비 단계'로 기준점을 남기고 진행)
순서가 중요하다. 앞 항목이 뒤 항목의 순환을 자동으로 푼다.
8. **기반 코드를 cms 밖으로**: `ActivityService`+`Activity` → `audit`, `CmsAccess`+`lock()` → `access`(또는 security), `CmsRules` → `common.InputRules`, `values()` → common. 이것만으로 account·classification·restaurant·version→cms edge 대부분이 사라진다.
9. `SaveIntent`·`VersionKind` → common. version이 제공하는 `capture/used`는 common의 작은 인터페이스로 두고 version이 구현(Spring DI, 방향 역전). `VersionRestoreService`는 `PostService.restoreSnapshot(...)` 전용 진입점 하나만 호출.
10. `SiteStructureService`는 자기 record를 돌려주고 `PublicSiteService`가 `PublicDocuments`로 변환(글이 이미 이 방식). 메뉴 가져오기(`importMenus`, 56줄)는 `MenuImportService`로 떼어 이관 완료 시 통째로 삭제 가능하게.
11. `CmsMapper.xml` 분할: 글 발행 13개 + 공개 글 조회 4개 → `PostMapper.xml`, 버전 4개 → `VersionMapper.xml`, 나머지 → Page / Media / Site(메뉴·링크·설정·카테고리) / Structure / ContentNode / Activity / Usage. Java는 `PostMapper`·`VersionStore` 방식(타입 있는 메서드, Map 조립은 내부)으로 기능별 Mapper. 과도기엔 `getMappedStatementNames()`와 상수 목록을 대조하는 테스트 하나로 죽은·없는 statement를 잡는다. `pageComposition` UPDATE는 속성별로 나눈다.
12. `PageService` → `PageDocumentService`(문서·발행) / `SiteCompositionService`(place·reorder·compose·membership; `importMenus`도 이것을 경유) / `PageSections`(JSON 파서, 7개 클래스가 PageService 대신 이것만 주입). `SiteService` → 메뉴·링크 / 설정 / 활동 조회.
13. `NextWorkspaceApi`(10기능 255줄)를 기능별 컨트롤러로. 경로는 그대로라 프런트 변경 없음.
14. 마지막에 cms를 `page / structure / site / media / template`로 나누고 레거시 Controller 6개는 `ui.legacy`로. 허용 의존 방향("기능끼리 순환 금지, 바닥은 common·audit·access, 어댑터는 기능을 import하되 역은 금지")을 문서 한 장 + import를 읽는 JUnit 규칙 테스트로 고정.
15. React의 레거시 의존 3종을 Next API로 채운다(`POST /pages`, `upload?imageOnly`, `GET /media/{id}/file`; 같은 서비스 호출이라 동작 동일). 그 뒤에야 레거시 화면 삭제를 논할 수 있다.

### 3단계 — 프런트 (중간, 2~3일; 예정된 UX 정리 패스와 함께)
16. 파일 이동만: `ReadPanels` → Dashboard/Posts/Roles/Activity, `EditPanels` → LinkManager/Settings/Media, `PagesPanel` → PageList/PlacementDialog/CompositionDialog/ContentNodeEditor. 동작 변화 0.
17. Prettier(printWidth 120)로 한 번 전체 포맷, 그 커밋을 `.git-blame-ignore-revs`에. 이유는 취향이 아니라 diff·blame·오류 열 번호·브레이크포인트가 줄 단위이기 때문.
18. `navigation.ts`에 경로·라벨·권한·아이콘·컴포넌트 표 하나(`routes.tsx`), `main.tsx`는 순회만. `postEditorId/pageEditorId` 헬퍼로 정규식 7곳 통합. `PanelProps` 타입 하나. `mediaFileUrl(id)` 헬퍼.
19. CSS: '새 층을 얹어 덮어쓰기'를 멈추고, 셸부터 화면 단위로 중복 선택자를 제거해 각 선택자가 한 파일에만 있게. `:is()` 묶음은 공통 버튼 기본값 한 규칙만. UX 패스에서 화면을 손볼 때 그 화면의 CSS를 정리하는 방식으로 진행.
20. 테스트: `useWorkspaceRoutes`의 순수 부분을 함수로 빼 node:test. 컴포넌트 테스트(vitest+jsdom, devDependency만)는 CSS·파일 정리 전에 넣는 것이 안전하지만 별도 결정.

### 4단계 — 테스트·문서 (중간, 1~2일)
21. JUnit `@Tag`(site-structure, page-delete, post, migration, public-api, legacy-screen) + surefire `groups`. 클래스명을 기능으로(NextAdmin→PagePublishApi…, migration은 `V17…` 접두어). `BackofficeIntegrationTest`(553줄 11기능)는 5개로 분할. 테스트를 `mvp/<기능>/` 아래로 이동.
22. 문서를 둘로: `docs/features/<기능>.md`(현재 설계·용어 대응표·돌릴 테스트 목록만, 버전이 바뀌면 그 문서를 고침)와 `docs/log/`(날짜 박힌 검증·적용 기록). README와 5D 가이드 첫 줄은 "현재 기준은 CURRENT_HANDOFF.md"로. CURRENT_HANDOFF §3은 현재 RC 표 하나만.

## 4. 하지 말 것
- Maven 멀티모듈, JPA, 새 프레임워크, 별도 백엔드. 지금 규모(백엔드 6,500줄, 프런트 3,300줄)에 맞지 않고 고정 스택 조건과도 어긋난다.
- 한 번에 다 옮기는 빅뱅. 단계마다 전체 테스트와 기준점.
- URL·DB·Flyway·receipt 형식 변경. 전부 Java/TS 소스 안의 일이다.
- 이미 수행된 V11~V17 승격 도구 삭제(운영 영수증 호환). 위임 클래스로 남긴다.
- 레거시 화면 삭제를 15번보다 먼저 하는 것.

## 5. 시점
- 0단계는 지금. 1단계는 V18이 생기기 전. 둘 다 IA·홈페이지 연결과 무관하고 작다.
- 2단계는 **IA 데이터 입력과 홈페이지 연결 전**이 낫다. 연결 뒤에는 공개 API 변환(10번)과 매퍼 분할(11번)의 검증 범위가 넓어진다. 사용자의 "진행 중인 단계는 기준점을 남기고 닫은 뒤 새 단계" 원칙에 맞춰 '구조 정비' 단계로 잡는다.
- 3단계는 예정된 UX 정리 패스와 같은 시기. 파일 이동(16)은 언제든 되고, CSS(19)는 화면을 손볼 때마다.
- 4단계는 2단계와 같이 하거나 바로 뒤.

## 6. 진행 기록

- 2026-10-01: 사용자 결정으로 큰 정리(②③④)는 모든 기능 수정이 끝난 뒤 '구조 정비' 단계에서 한 번에 한다. 그때까지 CSS 층을 더 얹지 않고 `cms`·`CmsMapper.xml`에 새 기능을 넣지 않는다.
- 2026-10-01: 0단계 중 세 가지를 먼저 적용했다 — `relocate-v12-runtime.ps1` 수정(V15 고정 목록 → `MIGRATED_V12` 이상 숫자 비교; select·swap·start 스크립트도 같은 방식), `NextApiErrors`에 `NextVersionApi`·`NextTemplateApi` 추가 + 누락을 잡는 `NextApiErrorsCoverageTest` + 409/400 JSON `code` 단언, 버전 핀 단일 출처(`FileRuntimeConfiguration` 반복 검사, 테스트 3곳은 `SchemaVersions.applied()`). `ClassificationMigrationTest`의 스크립트 목록은 검토 게이트라 그대로 둔다. 명시 import 전환은 정비 단계로 미룬다. 관련 테스트 57개 통과.

## 7. 첫걸음(남은 것)
0단계 네 가지(relocate 스크립트, NextApiErrors 범위, 명시 import, 버전 핀 단일 출처)를 한 커밋씩 네 개로. 각각 반나절 안쪽이고 동작 변화가 없으며, 2단계의 전제(정확한 import 추적)와 1단계의 전제(버전 단일 출처)를 만든다.
