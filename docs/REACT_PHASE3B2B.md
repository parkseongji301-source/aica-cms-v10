# AICA 3B-2B 구현·검증 결과

2026-09-27. 3B-2A의 분류를 React 공통 ContentEditor와 콘텐츠 목록에 연결했다. **원본 DB에는 V4~V6를 적용하지 않았다.** 기존 3B-2A 복사 DB도 그대로 보존하고, 별도의 V6 사본에서 검증했다. 새 migration이나 테이블 변경은 없다.

## 실행 환경과 보존

| 환경 | 주소 | DB / 실행 파일 |
| --- | --- | --- |
| 원본 | `http://127.0.0.1:8081/admin-next` | 기존 V3 `.local-data/aica-local` + 보존된 3A JAR. 중지·교체·마이그레이션하지 않음 |
| 3B-2B 검증 | `http://127.0.0.1:8082/admin-next/posts?view=manage` | `.cache/react-phase3b2a-data/aica-phase3b2b.mv.db` + `.cache/react-phase3b2b-test.jar` |
| 대표 편집 대상 | `http://127.0.0.1:8082/admin-next/posts/33/edit?view=manage` | 기존 콘텐츠 ID 33. 구조 보기에서도 동일 ID와 편집기 |

작업 전 기준점은 `.cache/checkpoints/20260927-004043-react-phase3b2b/`이다. 수정·미추적 파일을 포함한 소스 189개, manifest, Git bundle/patch/status, 정지 상태의 3B-2A 복사 DB와 일치하는 실행 JAR를 보존했다. 포인터는 `.cache/react-phase3b2b-checkpoint.txt`다. 기존 파일은 삭제하지 않았다.

보존된 3B-2A 복사 DB의 SHA-256은 `9b98d1f30f5a872e11b72e725586b21426ea862eadb855f7a59f2b83846d290f`이며 작업 후에도 동일하다. 원본은 계속 실행 중이므로 실행 중 H2 파일의 바이트 해시를 데이터 보존 근거로 삼지 않았다. 원본 실행 JAR, 기존 콘텐츠 ID/상태, 원본 API에 신규 분류 확장이 없는 것을 읽기 전용으로 확인했다.

검증 재실행은 `scripts/run-classification-copy.ps1`을 사용한다. 기본 DB는 이번 3B-2B 사본으로 변경했고 `.local-data` 접근 금지와 명시적인 copy-validation 설정은 유지했다. 예전 3B-2A 사본을 열려면 `-Database .cache/react-phase3b2a-data/aica-phase3b2a.mv.db`를 명시한다. 기준점 복원은 검증 서버만 중지한 뒤 **새 복사 경로**로 복원하며 원본에 복사하지 않는다.

## 1. React UI 변경

- ContentEditor에 유형 단일 선택, 기수·주제 복수 선택을 추가했다. 기수/주제 0개도 유효하다.
- 주제 선택지는 `content_type_topics`에 허용된 ID만 표시한다. 이름으로 매칭하지 않는다.
- 유형 변경으로 부적합해진 기존 주제는 별도의 확인 영역에 그대로 남는다. 운영자가 각 항목의 `해제` 버튼을 누르거나 유형을 되돌려야 한다. 새 유형의 같은 이름 주제를 자동 선택하지 않는다.
- 부적합 선택이 남으면 수동 저장·Ctrl+S·자동저장을 모두 막는다. 입력은 보존되며 상단에 저장 대기 상태가 표시된다.
- 기존 1.8초 자동저장 정책은 변경하지 않았다. 유형·기수·주제 ID를 dirty fingerprint, 저장 payload, 미리보기 입력에 포함했다. ID 순서만 달라지면 수정으로 보지 않는다.
- 두 보기에서 같은 ContentEditor를 계속 사용한다. 보기 전환 시 입력·선택과 목록 필터가 유지된다.
- 기존 category 선택은 `기존 카테고리`로 표시한다. 사이트 구조의 기존 category 탐색 이름도 일치시켰다. 메뉴/페이지/블록 연결은 변경하지 않았다.

주요 React 파일: `ContentEditor.tsx`, `ContentPanel.tsx`, `ClassificationFields.tsx`, `classification.ts`, `classification.css`, `contentDocument.ts`, `ReadPanels.tsx`, `types.ts`, `api.ts`, `main.tsx`.

## 2. 사용한 API

| API | 사용 방식 |
| --- | --- |
| `GET /api/admin/next/classifications` | 3B-2A 사전·유형별 허용 관계 조회 |
| `GET /api/admin/next/posts/{id}` | 동일 ID의 현재 초안과 classification 조회 |
| `PUT /api/admin/next/posts/{id}` | 기존 제목/본문/category/media/revision과 classification 저장 |
| `POST /api/admin/next/posts/{id}/preview` | 현재 입력된 classification까지 검증; DB 쓰기 없음 |
| `GET /api/admin/next/posts/{id}/publication` | 기존 발행본의 분류 snapshot 조회 |
| `GET /api/admin/next/posts` | 기존 목록 API에 `typeCodes`, `cohortIds`, `topicIds` 필터 추가. 행에 초안 classification 요약 추가 |

새 URL의 저장·발행 API를 만들지 않았다. 기존 PostService, 권한/소유권, CSRF, revision 충돌 검사, 검증·미디어·트랜잭션 처리를 사용한다. 신규 분류의 누락/명시적 null/빈 배열 계약도 3B-2A와 같다.

```json
"classification": {
  "typeCode": "FAQ",
  "cohortIds": [1, 2],
  "topicIds": [4, 5]
}
```

위 ID는 이번 검증 사본 예시다. 일반 호출자는 항상 사전 조회에서 받은 ID를 사용해야 한다. 분류를 수정하는 PUT에는 기존 다른 필드와 현재 revision도 함께 보낸다.

## 3. 분류 필터 동작

관리 목록과 그 요약은 **현재 초안 기준**이다. `발행` 탭도 “발행본이 있는 콘텐츠 중 현재 초안 분류가 조건에 맞는 것”을 뜻하며, 공개 사이트의 발행 분류 검색과 다르다. 화면에 초안 기준임을 명시했다.

예: `typeCodes=REVIEW,FAQ&cohortIds=1,2&topicIds=1,5`는 `(후기 OR FAQ) AND (6기 OR 7기) AND (후기 생활 OR FAQ 생활)`이다. 기존 category, 검색어, 상태, 소유권 제한도 AND로 적용한다. 빈 필터는 해당 축을 제한하지 않는다는 의미다.

쉼표 목록과 반복 파라미터를 모두 받는다. 필터를 바꾸면 첫 페이지로 이동하며 URL에 조건을 유지한다. 모든 주제 필터는 `생활 · 후기`, `생활 · FAQ`처럼 허용 유형을 함께 표시해 이름 충돌을 구분한다. 목록 필터의 유형 변경은 주제 필터를 자동 제거하지 않는다. 맞지 않는 조합은 0건이다.

`PostMapper.xml`의 목록/COUNT는 동일한 조건 조각을 사용한다. 기수·주제는 관계 테이블을 직접 복수 JOIN하지 않고 `EXISTS`로 검사한다. 한 콘텐츠가 선택한 여러 기수·주제에 동시에 걸려도 한 행, count 1이다. 별도 목록 저장소나 분류별 콘텐츠 복사본은 없다.

Java 변경: `ClassificationModels.Filter`, `ClassificationService.filter`, 기존 PostService.list/PostMapper.list/count 오버로드, `NextWorkspaceApi`의 조회 파라미터/분류 요약. 저장·발행 분류 테이블과 SQL은 변경하지 않았다.

## 4. 초안/발행본 표시

미리보기 옆에 다음 두 영역을 분리했다.

- `작성 중 분류 · 미저장` 또는 `저장된 초안 분류`: 현재 편집 입력의 유형·기수·주제. 부적합 선택도 입력에서 사라지지 않는다.
- `현재 발행본 분류`: 서버에서 읽은 발행 snapshot. 발행 당시 이름을 그대로 표시한다. 현재 공개본이 없으면 없다고 표시한다.

분류가 다르면 “초안 저장만으로 발행본은 바뀌지 않습니다”라고 표시한다. ID 또는 표시 이름이 다르면 차이를 감지한다. 발행본 조회 중/실패 상태도 구분하며 조회 실패를 “발행본 없음”으로 감추지 않는다. 재조회, 창으로 복귀, 발행본 분류 새로고침으로 다시 확인할 수 있다.

별도 작성기를 만들거나 발행 정책을 변경하지 않았다. 기존 화면에서 저장·발행한 뒤 편집을 계속하려면 `다시 조회`로 최신 revision을 받는다. 오래된 revision 저장은 기존처럼 충돌로 차단된다.

## 5. 기존 관리자 호환

Thymeleaf `posts/form.html`에는 현재 초안 분류 한 줄 요약과 같은 콘텐츠 ID의 React 편집 링크만 추가했다. 유형/기수/주제 입력이나 숨겨진 저장 필드는 추가하지 않았다. PostController는 권한이 확인된 기존 PostService에서 요약을 읽는다.

기존 HTML 폼과 save-json은 신규 필드를 보내지 않으므로 서버가 현재 분류를 보존한다. 기존 화면에서 발행해도 현재 분류를 발행본에 복사한다. 자동 테스트에서 HTML/JSON 저장·발행과 3A 요청의 누락 호환을 계속 확인했고, 브라우저에서도 확인했다.

## 6. 테스트 결과와 검증 데이터

| 검증 | 결과 |
| --- | --- |
| Spring 전체 `clean verify` | 69개 중 68개 통과, 오류/실패 0. 실제 V3 사본 경로가 필요한 3B-2A migration 테스트 1개는 기본 조건에 따라 미실행 |
| Thymeleaf 요약 추가 후 호환 재검증 | ClassificationIntegrationTest + NextPostIntegrationTest 23개 통과(위 테스트와 중복) |
| React 상태 로직 테스트 | Node 내장 test runner 6개 통과 |
| TypeScript/Vite | 타입 검사·빌드 통과 |
| 목록 SQL | 다중 OR/축 간 AND, category·검색·상태·권한 조합, 13건의 10+3 페이지 분할과 중복 없는 count 확인 |
| 기존 처리 | category/메뉴/POSTS 블록, 초안/발행본 분리, 잘못된 주제·중복·충돌·CSRF 차단 및 실패 시 롤백 기존 테스트 통과 |
| 브라우저 | 조회·수정·자동저장·재조회, 두 보기 전환, 빈 선택, 복수 선택, 부적합 주제 유지, 분류 비교, 목록 필터와 count, Thymeleaf 저장/발행 확인 |

`frontend/tests/classification.test.ts`는 부적합 주제를 남긴 유형 전환과 명시 해제, 같은 이름/다른 ID, 빈·복수 선택, 순서 정규화, 발행 이름 보존, 비활성 항목 및 실제 dirty fingerprint를 검사한다. 새 테스트 라이브러리는 추가하지 않았다.

실제 브라우저에서 ID 33에 후기/6·7기/생활·수업을 저장하고 Thymeleaf로 발행했다. FAQ로 바꾸면 기존 후기 주제가 남아 자동저장이 멈췄고, 다른 화면에서 재조회해도 DB는 후기 상태였다. 직접 해제한 뒤 기수·주제 모두 0개인 FAQ를 저장·재조회했다. 이후 FAQ/6·7기/준비사항·생활로 저장했고, 발행본은 후기/6·7기/생활·수업을 유지했다. 기존 Thymeleaf에서 다시 초안 저장해도 FAQ 분류가 보존됐다.

검증 사본의 `생활` ID는 후기 **1**, FAQ **5**로 다르다. FAQ + 후기 생활 필터는 0건, FAQ 생활을 OR로 추가하면 1건이었다. 두 기수와 복수 주제를 선택해도 ID 33의 행과 total은 1이었다. 원본과 사본의 posts 수는 각각 기존 9개를 유지했다.

검증용 기수 2개/주제 5개만 `workbench/classification-validation/phase3b2b-fixture.sql`과 `scripts/seed-classification-copy.ps1`로 **정지된 새 사본**에 등록했다. Flyway/startup에 연결하지 않았다. 타입 사전은 기존 5개를 유지하고 GENERAL/REVIEW/FAQ로 검사했다. 콘텐츠·페이지·IA는 생성하지 않았다. 이 fixture를 원본 초기 데이터로 사용하지 않는다.

증거 파일:
- `.cache/react-phase3b2b-verify.log`, `.cache/react-phase3b2b-compatibility.log`
- `.cache/react-phase3b2b-empty-selection.json`, `.cache/react-phase3b2b-runtime-check.json`
- `.cache/react-phase3b2b-final-audit.json`

## 7. 원본 V4~V6 적용 전 마지막 확인사항

1. 적용 시점의 원본 DB와 맞는 실행 파일을 다시 보존하고, **그 최신 원본의 새 사본**에서 V3→V6 및 기존 데이터 비교를 재실행한다. 현재 3B-2B 사본을 원본 위에 덮어쓰지 않는다.
2. 실제 환경의 Flyway 이력·검색 경로와 V1~V6 checksum을 확인한다. 미완성 Java V4는 계속 격리하고, 검증 fixture가 배포 초기화 경로에 들어가지 않게 한다.
3. 실제 기수·주제 code/ID, 유형별 허용 관계, 동명 주제의 공유 여부를 확정한다. 기존 category 이름을 보고 추정 변환하지 않는다. 기존 데이터는 GENERAL + 빈 기수/주제로 시작한다.
4. 배포 시 신규 분류를 저장하는 서버와 UI를 함께 적용해야 한다. V6에서 분류를 작성한 DB에 옛 발행기를 연결하지 않는다. 복구는 DB/JAR가 일치하는 기준점으로 수행한다.
5. 원본 차단 장치는 이번에 유지했다. 원본 적용을 승인받은 다음 실제 배포 경로에 한해 별도로 검토해야 한다. 현재 SUPPORTER 권한, 승인/반려, 자동저장 정책은 임의 변경하지 않는다.

원본 적용, 전체 실제 분류/IA, 유형별 전용 입력 필드, 신규 분류로 페이지 자동 노출, category 제거, 블록 저장 형식, 템플릿 및 승인/반려는 이번 작업에 포함하지 않았다.
