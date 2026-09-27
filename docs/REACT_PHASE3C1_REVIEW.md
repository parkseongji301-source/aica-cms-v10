# AICA 3C-1 후기 연결 결과

2026-09-27. 기존 REVIEW 분류와 공통 콘텐츠 목록/ContentEditor를 사이트 구조의 후기 경로에 연결했다. 후기 전용 필드·저장소·테이블·페이지는 추가하지 않았다. **원본 V3, schema migration V4~V6 및 원본 적용 차단 장치는 그대로 유지했다.**

## 실행 환경과 기준점

- 원본: `http://127.0.0.1:8081/admin-next` — 기존 3A JAR / 원본 V3. 이번 작업에서 종료·교체·migration하지 않음.
- 기존 3B 검증: `http://127.0.0.1:8082/admin-next` — 기존 B2B fixture 포함 사본. 변경하지 않음.
- **이번 후기 검증: `http://127.0.0.1:8083/admin-next/posts?view=structure&reviewSection=all&typeCodes=REVIEW`**.
- 완료 시점에는 8083만 실행 중이다. 8081/8082는 접속되지 않아 실행 중인 화면으로 안내하지 않는다. 원본과 이전 사본의 파일은 보존되어 있다.
- DB: `.cache/react-phase3b2a-data/aica-phase3c1.mv.db`. 3B 최종 검증의 사전이 빈 V6 사본을 다시 복제했다. 8082의 B2B fixture를 운영 후보 데이터로 변환하지 않았다.
- 실행 JAR: `.cache/react-phase3c1-test.jar`. 재실행은 `scripts/run-review-copy.ps1` 또는 기존 `run-classification-copy.ps1`에 8083/위 사본 경로를 명시한다.
- 작업 전 기준점: `.cache/checkpoints/20260927-120251-react-phase3c1/`. 수정·미추적 소스 197개, Git 이력/patch/status, 사전 등록 전 V6 DB와 그에 맞는 실행 JAR를 보존했다. 포인터는 `.cache/react-phase3c1-checkpoint.txt`.

## 1. 실제 사이트 구조 연결 방식

```text
선배들의 SSUL
└ 후기          → 기존 콘텐츠 목록 + typeCodes=REVIEW
  ├ 생활        → REVIEW AND REVIEW_LIFE의 실제 topic ID
  ├ 수업        → REVIEW AND REVIEW_CLASS의 실제 topic ID
  └ 프로젝트    → REVIEW AND REVIEW_PROJECT의 실제 topic ID
```

이 부분은 사용자가 지정한 IA 일부의 **관리자 탐색 설정**이다. 홈페이지 메뉴 DB나 페이지를 자동 생성하지 않았고, 선배들의 SSUL/후기를 topic으로 저장하지 않았다. 기존 홈페이지 메뉴와 메뉴 밖 페이지/category 탐색도 유지한다. 방문자 홈페이지나 새로운 노출 블록은 만들지 않았다.

`reviewNavigation.ts`는 `/classifications`의 code→ID 및 `content_type_topics` 허용 관계를 확인해 경로를 만든다. 예를 들어 현재 사본의 생활 주제 ID는 1이지만 코드에 숫자 1을 고정하지 않았다. 같은 이름의 FAQ 생활과 이름으로 연결하지 않는다. 사전이 없거나 비활성·허용 관계가 빠졌으면 해당 항목을 비활성화하고, 잘못된 직접 URL도 전체 콘텐츠로 넓혀 보여주지 않는다.

`reviewSection=all|life|class|project`는 화면의 탐색 문맥이다. DB에는 저장하지 않으며 목록 API로도 전달하지 않는다. 실제 조회에는 기존 `typeCodes`, `cohortIds`, `topicIds`, 검색/상태/category 파라미터를 사용한다.

선택한 위치의 유형과 주제 조건은 고정된다. 기수·검색어·기존 category·상태를 추가로 좁힐 수 있다. 후기 전체에서는 후기 주제를 복수로 필터링할 수 있다. 필터 초기화도 선택 위치의 기본 조건은 유지한다. 관리 목록은 이전 단계와 동일하게 **초안 기준**이다.

편집은 두 경로 모두 `/admin-next/posts/{id}/edit`와 같은 ContentPanel→ContentEditor다. 예를 들어 사이트 구조에서 만든 ID 97을 사이트 관리 전체 목록에서도 ID 97로 열었다. `reviewSection`이 붙어도 editor의 React key와 저장 ID는 바뀌지 않는다. 보기 전환은 기존 편집기를 유지하고, 목록으로 돌아갈 때 필터 문맥을 유지한다.

## 2. 등록한 사전과 검증 콘텐츠

| 대상 | code | 표시명 | 이번 사본 ID |
| --- | --- | --- | --- |
| 기수 | COHORT_06 | 6기 | 1 |
| 기수 | COHORT_07 | 7기 | 2 |
| REVIEW 주제 | REVIEW_LIFE | 생활 | 1 |
| REVIEW 주제 | REVIEW_CLASS | 수업 | 2 |
| REVIEW 주제 | REVIEW_PROJECT | 프로젝트 | 3 |

REVIEW 허용 관계는 위 주제 3개뿐이다. 기수/주제 선택은 여전히 0개 이상이다. GENERAL 및 다른 등록 유형은 기존 사전을 유지하며 FAQ·맛집·인터뷰의 주제나 전용 필드는 이번에 등록하지 않았다.

`workbench/review-candidate/vocabulary.sql`에 후보 사전을 두고 `scripts/seed-review-copy.ps1`로 **정지된 새 복사 DB에만** 수동 등록했다. schema migration/앱 부팅에 연결하지 않았다. 같은 파일을 두 번 적용해도 code/ID가 중복되지 않는 것을 자동 테스트했다. 기존 사전 내용을 삭제·교정하는 스크립트가 아니다.

이 code들은 운영 후보 사전이고, 3B-2B의 `B2B_*`는 별도 검증 fixture다. 현재 사본의 숫자 ID도 원본으로 복사하지 않는다. 원본 등록은 계속 보류다.

동선 확인용 콘텐츠 4건은 제목에 `[검증]`, 본문에 실제 수료생 후기가 아님을 표시했다. 사전 SQL에는 들어 있지 않으며 API/공통 편집기로 명시 생성했다.

| ID | 검증 용도 / 최종 초안 분류 |
| --- | --- |
| 97 | 브라우저 생성·발행·분류 변경·재발행. 최초 7기/생활 → 최종 6기+7기/프로젝트 |
| 98 | 6기와 7기 수업 비교. 두 기수/수업 |
| 99 | 복수 주제. 6기/생활+수업+프로젝트 |
| 100 | 기수·주제 모두 선택 없음 |

기존 콘텐츠 9건은 그대로이며, 검증용 4건을 합쳐 전체 13건이다. 기존 글을 후기라고 추정 변환하지 않았다. 새 후기 초안은 `새 후기 → 제목 입력 → 초안 만들기`를 명시적으로 실행한 때에만 생성한다. 본문·사진·기수·주제 편집은 생성 직후 공통 ContentEditor에서 한다. 생활 등 하위 위치에서 시작하면 그 주제를 초깃값으로 안내하고 전달하며, 이후 편집기에서 자유롭게 해제·복수 선택할 수 있다. 기수는 자동 추정하지 않는다.

## 3. 후기 목록 필터 결과

| 선택 | 최종 초안 결과 ID | API total / 화면 행 수 |
| --- | --- | --- |
| 후기 전체 | 100, 99, 98, 97 | 4 / 4 |
| 생활 | 99 | 1 / 1 |
| 수업 | 99, 98 | 2 / 2 |
| 프로젝트 | 99, 97 | 2 / 2 |
| 6기 OR 7기 AND 생활 OR 수업 OR 프로젝트 | 99, 98, 97 | 3 / 3 |

표의 마지막 행은 `(6기 OR 7기) AND (생활 OR 수업 OR 프로젝트)`이며 REVIEW 조건도 AND다. ID 99가 세 주제에 속해도 같은 조회 결과에서 중복되지 않는다. 97의 생활→프로젝트 초안 변경 후 생활 목록에서 빠지는 것도 확인했다. 발행본 분류는 별도로 유지된다.

SQL/COUNT는 3B의 공통 PostService.list/PostMapper EXISTS 조건을 그대로 사용한다. 후기 전용 조회 테이블이나 복사본을 만들지 않았다.

## 4. 재사용한 편집 기능과 저장 처리

- 기존 ContentEditor의 제목, Quill 본문, 이미지/첨부, category, 유형/기수/주제, dirty fingerprint, 자동저장, 다시 조회, 미리보기 및 발행본 비교.
- 기존 PostService.saveDocument→save 트랜잭션, 작성자/소유권, 미디어 검증, 분류 검증, revision, 기존 category, 활동 이력.
- 기존 CSRF·세션·역할 검사. SUPPORTER는 기존 본인 글 관리 제한을 유지한다.
- 발행은 기존 Thymeleaf와 PostService의 발행본 snapshot 처리를 사용한다. React 별도 발행기를 만들지 않았다.

브라우저에서 제목을 비워 저장이 진행되지 않는 상태로 본문·기수·category를 입력하고 사이트 관리↔구조를 전환했을 때 입력이 유지됐다. 이후 제목을 복구하고 저장·재조회해 동일 값을 확인했다.

ID 97을 7기/생활로 기존 관리자에서 발행한 뒤 React에서 6기+7기/프로젝트로 초안을 수정했다. 발행본은 7기/생활을 유지했다. Thymeleaf 임시저장 후에도 새 초안 분류가 보존됐고, 다시 발행하자 발행본도 6기+7기/프로젝트로 바뀌었다. 기존 category는 교육 소식(ID 2)을 유지했다.

## 5. 신규 코드/API 범위

| 변경 | 역할 |
| --- | --- |
| `frontend/src/reviewNavigation.ts` | 제한된 IA 위치→기존 목록 필터 변환, code/허용 관계 확인, 공통 편집기 경로 |
| `ReviewTree.tsx`, `main.tsx` | 구조 모드의 후기 탐색과 현재 위치 표시. 공통 편집기 유지 |
| `ReadPanels.tsx` | 기존 목록의 후기 문맥 제목·고정 조건·새 후기 진입 |
| `CreateReview.tsx`, `api.ts` | 제목으로 초안을 만든 뒤 같은 ContentEditor로 넘기는 작은 생성 대화상자 |
| `workspace.css` | 후기 하위 항목 들여쓰기·정렬 및 생성 대화상자 |
| `NextPostApi.java` | 공통 콘텐츠 초안 생성 HTTP adapter 1개 |
| 후보 사전/스크립트 | 복사 DB의 수동 최소 사전 등록과 실행 |
| 자동 테스트 | 탐색 변환, 사전 재실행, 후기 저장/발행/필터/권한 흐름 |

추가 API는 **`POST /api/admin/next/posts`** 하나다. 응답은 `201`과 기존 PostDocument다. 요청 필드는 기존 저장 요청과 같으며 새 초안이므로 revision은 생략한다. 저장 작업은 항상 `save`로 지정하고 로그인한 계정을 작성자로 사용한다. 요청으로 임의 작성자나 발행을 지정하지 않는다.

```json
{
  "title": "후기 제목",
  "content": "",
  "richContent": null,
  "categoryId": null,
  "mediaIds": [],
  "classification": {"typeCode": "REVIEW", "cohortIds": [], "topicIds": []}
}
```

조회·수정·미리보기·발행본 조회·목록·분류 사전 API는 기존 경로 그대로다. 후기 전용 저장 업무 로직을 추가하지 않았다. Java의 PostService/Mapper/보안 설정 및 DB migration은 변경하지 않았다.

## 6. 검증 결과

- 최종 Spring 전체 `clean verify`: **73개 통과, 실패 0, 오류 0, 제외 0**. 새 후기 통합 검사 4개를 포함한다.
- 프런트 테스트: **10개 통과**. 신규 탐색 검사 4개와 기존 분류 상태 검사 6개.
- TypeScript 검사/Vite 빌드 통과. 기존 번들 크기 경고와 서버 제공 CSS 경로 안내는 남아 있다.
- 후보 사전 등록 직후 기존 16개 테이블의 모든 기존 컬럼/행 수가 3B 기준점과 동일함을 읽기 전용 비교했다.
- 최종 확인에서 원본 DB의 새 사본을 읽기 전용으로 검사해 V3와 기존 16개 테이블의 전체 데이터 동일성을 재확인했다(`react-phase3c1-original-readback.json`). 원본 파일에 DB 연결/migration을 수행하지 않았다.
- 실제 API에서 기존 ID 33 문서, ID 65 페이지/블록, 메뉴·category·권한 응답이 3B 기준점과 같음을 재확인했다.
- 브라우저: 후기 생성→공통 편집, 7기 단일/6·7기 복수, 생활→프로젝트 변경, 저장·재조회·보기 전환, 관리/구조 동일 ID, 구 화면 임시저장·발행·재발행, 네 구조 목록의 결과/개수와 선택 위치를 확인했다.
- 빈 기수/주제와 복수 주제는 실제 생성 데이터 및 API 조회에서 확인했고, 자동 테스트에서도 모두 검증했다. 이미지/첨부·미리보기·권한·충돌·기존 category/메뉴/페이지의 기존 회귀 검사도 통과했다.

### 이번에 발견하고 보강한 검사

기존 실제 V3 파일 사본 migration 테스트에서 짧은 연결을 연속해 열고 닫을 때 두 번째 migrate가 0 대신 3을 보고하는 현상이 두 번 재현됐다. 원본 DB나 schema SQL을 고치지 않았다.

`ClassificationMigrationTest`에서 실제 서버의 Hikari처럼 검사 동안 DB 연결을 유지하도록 했다. 같은 연결 수명 내 재실행 0을 확인하고, **연결을 완전히 닫은 뒤 새 Flyway/연결에서도 V6·재실행 0과 기존 데이터 동일성**을 추가 확인했다. 보강 후 독립 새 V3 사본 검사와 전체 73개 검사가 모두 통과했다. 검사를 제외하거나 기대값을 3으로 완화하지 않았다. H2/Flyway/파일 환경 중 정확한 내부 원인을 이번 결과만으로 단정하지 않는다.

근거: `.cache/react-phase3c1-final-verify.log`, `react-phase3c1-migration-lifetime.log`, `react-phase3c1-pre-edit-audit.json`, `react-phase3c1-created-examples.json`, `react-phase3c1-draft-publication.json`, `react-phase3c1-runtime-audit.json`, `react-phase3c1-final-audit.json` 및 기준점의 보존 로그/테스트 보고서.

## 7. 3C-2 FAQ 전에 확인할 사항

1. FAQ 생활을 후기 생활과 다른 code/ID로 유지해야 한다. 이번 사전에는 FAQ 주제가 없으며 `지원 전 Check!!` 하위 탐색·FAQ 내용 구조도 아직 구현하지 않았다.
2. 이번 후기 탐색 설정은 지정된 위치에 기존 필터를 적용하는 최소 구현이다. FAQ도 같은 공통 목록/편집기를 쓰되 IA 위치와 콘텐츠 분류를 따로 연결해야 한다. 홈페이지 메뉴/페이지 생성으로 확대하지 않는다.
3. 새 초안 생성 후 내용을 쓰지 않고 나가면 제목만 있는 초안이 남는다. 이는 명시적으로 만든 일반 임시저장 콘텐츠이며 자동 삭제하지 않는다. 현재 정책은 유지했다.
4. 기존 화면에서 저장/발행한 후 React 편집을 이어갈 때는 `다시 조회`로 최신 revision을 받아야 한다. 오래된 수정은 기존 충돌 검사로 차단된다.
5. 원본 적용은 계속 보류다. H2/Flyway의 기존 버전 호환 경고와 이번 파일 연결 조건은 원본 적용 전 재검증 항목으로 남긴다. FAQ 개발을 위해 원본을 업그레이드하지 않는다.

후기 전용 작성자 프로필/별점/추가 필드, FAQ/맛집/인터뷰 전용 기능, 전체 IA/기수 등록, 블록·템플릿·승인 정책 변경은 수행하지 않았다.
