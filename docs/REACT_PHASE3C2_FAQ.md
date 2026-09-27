# AICA 3C-2 FAQ 연결 결과

2026-09-27. `지원 전 Check!! → FAQ`를 기존 posts 목록과 공통 ContentEditor에 연결했다. FAQ 전용 테이블·컬럼·페이지·정렬 모델은 추가하지 않았다. 원본 V3와 schema migration V4~V6는 변경하지 않았다.

## 환경과 보존

- 이번 검증: **http://127.0.0.1:8084/admin-next/posts?view=structure&faqSection=all&typeCodes=FAQ**
- FAQ 사본: `.cache/react-phase3b2a-data/aica-phase3c2.mv.db`
- 실행 JAR: `.cache/react-phase3c2-test.jar`; 재실행 스크립트 `scripts/run-faq-copy.ps1`(기본 8084).
- 기존 후기 검증 서버 8083은 보존했다. 정상 종료한 뒤 사본/기준점을 만들고 같은 기존 JAR/DB로 다시 실행했다.
- 기준점: `.cache/checkpoints/20260927-124052-react-phase3c2/`; 포인터 `.cache/react-phase3c2-checkpoint.txt`.
- 작업 전 소스 207개(수정·미추적 포함), Git 이력/patch, 후기 검증 DB와 대응 JAR를 함께 보존했다. 원본 파일의 SHA-256도 작업 전후 동일하다.
- 완료 후에도 8084를 정상 종료해 FAQ DB와 실행 JAR를 함께 백업했다. 재시작 후 FAQ 4건의 문서·발행본과 전체 17건이 유지됨을 확인했다. 기준점의 `RESTORE.md`에 두 묶음의 복구 절차가 있다.

## 1. 사이트 구조 연결

```text
지원 전 Check!!
└ FAQ                  → typeCodes=FAQ
  ├ 준비사항            → FAQ AND FAQ_PREPARATION의 topic ID
  ├ 지원·선발           → FAQ AND FAQ_APPLICATION의 topic ID
  ├ 수업                → FAQ AND FAQ_CLASS의 topic ID
  ├ 생활                → FAQ AND FAQ_LIFE의 topic ID
  ├ 취업                → FAQ AND FAQ_EMPLOYMENT의 topic ID
  ├ 지원금              → FAQ AND FAQ_ALLOWANCE의 topic ID
  └ 프로젝트            → FAQ AND FAQ_PROJECT의 topic ID
```

각 위치는 기존 `/posts` 목록에 필터를 적용하는 관리자 탐색 설정이다. 개별 질문은 오른쪽 목록에만 표시하며 왼쪽 트리에 넣지 않는다. 홈페이지 메뉴/페이지나 topic에 `지원 전 Check!!`, `FAQ`라는 위치를 생성하지 않았다. 기존 메뉴와 페이지 연결을 그대로 유지한다.

후기와 FAQ는 `contentNavigation.ts` / `ContentTree.tsx`로 같은 경로 생성·검증 코드를 사용한다. code와 허용 관계로 실제 topic ID를 찾으며 표시 이름이나 숫자를 하드코딩하지 않는다. 사전이 누락·비활성·미허용이면 비활성/오류로 표시한다. 후기와 FAQ 문맥을 동시에 지정한 잘못된 URL도 차단한다.

`faqSection`은 화면 탐색 문맥으로만 사용하고 API/DB 분류에 저장하지 않는다. 유형·하위 위치의 주제 조건은 고정하며, 기수/검색/상태/기존 category로 추가 필터링할 수 있다. FAQ 전체에서는 주제를 복수 선택할 수 있다. 서로 다른 기준은 AND, 같은 기준의 복수 값은 OR다. 목록은 이전 단계와 동일하게 **초안 기준**이다.

## 2. 사전과 검증 콘텐츠

| code | 표시명 | 이번 사본 topic ID |
| --- | --- | --- |
| FAQ_PREPARATION | 준비사항 | 4 |
| FAQ_APPLICATION | 지원·선발 | 5 |
| FAQ_CLASS | 수업 | 6 |
| FAQ_LIFE | 생활 | 7 |
| FAQ_EMPLOYMENT | 취업 | 8 |
| FAQ_ALLOWANCE | 지원금 | 9 |
| FAQ_PROJECT | 프로젝트 | 10 |

FAQ에는 위 7개만 허용한다. 기존 REVIEW에는 기존 3개만 허용한다. 기수 사전은 기존 COHORT_06/07을 유지하고 FAQ 초안은 기본 선택 없음이다. FAQ 주제도 0개 이상 선택할 수 있다.

`workbench/faq-candidate/vocabulary.sql`은 운영 후보 사전만 담은 수동 SQL이다. migration/앱 부팅과 분리되어 있으며 원본에 등록하지 않는다. `seed-faq-copy.ps1`은 정지된 `aica-phase3c2*.mv.db` 검증 사본만 허용한다. 같은 code가 있으면 덮어쓰지 않으며 반복 실행의 중복 방지를 테스트했다. 다른 환경에 예상치 못한 사전이 있다면 자동 삭제하지 않고 먼저 검토한다.

검증 콘텐츠는 SQL과 분리해 기존 API/공통 편집기로 4건만 생성했다. 제목은 요청한 질문을 그대로 사용하고 답변에는 `[검증용 답변]`, 실제 지원 안내가 아니라는 표시를 넣었다. 확정되지 않은 지원 정책을 답변으로 만들지 않았다.

| 콘텐츠 ID | 질문 | 최종 주제 |
| --- | --- | --- |
| 101 | 포트폴리오가 꼭 필요한가요? | 준비사항 |
| 102 | 코딩테스트 많이 어렵나요? | 준비사항 |
| 103 | 주차 공간이 있나요? | 생활 |
| 104 | 팀 구성은 어떻게 하나요? | 프로젝트 |

기존 13건(후기 검증 4건 포함)과 별개로 총 17건이다. 원본에는 사전/검증 글 모두 없다.

## 3. ContentEditor와 실제 저장 필드

문서의 `posts.body`와 달리 현재 실제 DB/Java/API는 **`content`**를 사용하며 서식 문서는 `rich_content` / `richContent`로 저장한다. 이름을 문서에 맞추려고 컬럼이나 API를 변경하지 않았다.

| 의미 | 일반 UI | FAQ UI | 기존 저장 방식 |
| --- | --- | --- | --- |
| 제목 | 제목 | 질문 | posts.title / API title |
| 본문 | 본문 | 답변 | posts.content / API content |
| 본문 서식 | 공통 편집기 | 같은 공통 편집기 | posts.rich_content / API richContent |

`contentPresentation`으로 UI 용어만 분기한다. FAQ에서 다른 유형으로 전환하면 제목/본문으로 돌아가며 RichEditor를 새로 만들지 않고 접근성 라벨도 갱신한다. 질문/답변 입력, 서식, 첨부 데이터는 그대로 유지한다. 사진·파일 기능을 없애거나 필수로 만들지 않았다.

FAQ 질문에는 기존 제목의 필수·200자 제한을 적용한다. Spring PostService에서도 FAQ의 제목/일반 본문 검증 문구를 질문/답변으로 표시한다. RichTextService의 기존 서식/길이/미디어 검증은 공통이다. 빈 답변의 **초안**은 기존과 같이 허용하며, 발행 시 본문 또는 미디어가 필요하다는 기존 정책은 유지한다. 별도 FAQ 발행 정책이나 필수 기수/대표 이미지 정책은 추가하지 않았다.

유형 변경 시 다른 유형의 주제 ID를 조용히 삭제하지 않는다. 기존 ClassificationFields의 경고·명시 해제·저장/자동저장 대기를 그대로 사용한다. 분류, 질문, 답변 모두 기존 dirty fingerprint 및 revision에 포함된다.

## 4. 생성과 공통 ID

3C-1의 `POST /api/admin/next/posts`를 그대로 사용한다. 이전 CreateReview의 초안 생성 대화상자를 `CreateContentDraft`로 공통화했다. 유형은 FAQ로 지정하고, 위치에 따른 주제 초기값을 표시한다. 생성 전에 운영자가 주제를 해제하거나 복수 선택할 수 있다. 기수는 선택 없음으로 시작한다.

`초안 만들기`를 명시 실행한 뒤 반환된 ID로 같은 ContentPanel/ContentEditor에 진입한다. 별도 FAQ 작성기나 저장 로직은 없다. 생성 취소 시 글이 생기지 않는다. 명시 생성 후 나가면 일반 초안이 남는 기존 방식도 유지한다.

사이트 관리 > 콘텐츠 목록 > ID 101과 사이트 구조 > FAQ > 준비사항 > ID 101 모두 `/admin-next/posts/101/edit`의 같은 편집기를 연다. query의 문맥이 달라도 React editor key와 저장 ID는 101이다. 브라우저에서 두 경로와 보기 전환 입력 유지까지 확인했다.

## 5. 목록 결과와 REVIEW 격리

| FAQ 조회 | 실제 ID | API total / 화면 확인 |
| --- | --- | --- |
| 전체 | 104, 103, 102, 101 | 4 / 4 |
| 준비사항 | 102, 101 | 2 / 2 |
| 지원·선발 | 없음 | 0 / 0 |
| 수업 | 없음 | 0 / API·자동 검사 |
| 생활 | 103 | 1 / 1 |
| 취업 | 없음 | 0 / API·자동 검사 |
| 지원금 | 없음 | 0 / API·자동 검사 |
| 프로젝트 | 104 | 1 / 1 |

준비사항 OR 생활은 3건이다. 복수 주제에 속한 같은 글의 중복 제거는 FAQ 통합 테스트에서도 확인했다. SQL과 COUNT는 기존 PostService/PostMapper의 EXISTS 필터를 그대로 사용한다.

`REVIEW_LIFE=1`, `FAQ_LIFE=7`로 서로 다른 ID다. 두 표시명은 모두 생활이다. FAQ+REVIEW_LIFE, REVIEW+FAQ_LIFE 교차 조건은 각각 0건이다. 브라우저에서도 FAQ 생활은 주차 질문(ID 103), 후기 생활은 기존 후기(ID 99)만 보인다.

후기는 전체 4, 생활 1, 수업 2, 프로젝트 2건으로 3C-1과 같다. 기존 콘텐츠 13개 전체 문서, 발행본 97, 페이지 65/블록, 메뉴/category/권한 API 응답도 비교해 동일함을 확인했다.

## 6. 초안·발행본·기존 관리자

ID 101에 대해 브라우저로 다음을 확인했다.

1. React에서 질문과 답변 1 저장, 새로고침 후 재조회.
2. 기존 Thymeleaf에서 발행본 저장.
3. React에서 답변 2로 초안 수정. 발행본은 답변 1 그대로 유지.
4. Thymeleaf 임시저장 후 FAQ 유형·준비사항 주제·기수 없음과 초안 답변 2 보존. 발행본은 여전히 답변 1.
5. Thymeleaf 재발행 후 발행본이 답변 2와 현재 분류로 변경.

FAQ 통합 테스트에서는 주제 변경과 질문 변경까지 같은 발행본 분리, legacy 저장/재발행, stale revision 409를 확인했다. 두 관리자 모두 기존 PostService 트랜잭션·분류 검증·권한·CSRF를 사용한다. Thymeleaf 분류 편집 UI를 중복 추가하지 않았다.

## 7. 변경 코드와 API

**신규 API 없음.** 기존 POST 생성 / GET 조회 / PUT 초안 저장 / preview / publication / classifications / 목록 API를 그대로 사용한다. Mapper·API DTO·DB 구조·인증·역할/발행 권한은 바꾸지 않았다.

| 파일/영역 | 변경 |
| --- | --- |
| contentNavigation.ts, ContentTree.tsx | 후기/FAQ 공통 탐색 설정과 필터 연결 |
| reviewNavigation.ts, ReviewTree.tsx | 기존 후기 경로를 공통 코드에 위임 |
| main.tsx, ReadPanels.tsx | FAQ 트리/현재 위치/공통 목록/질문 검색 표시 |
| CreateContentDraft.tsx | 기존 CreateReview를 공통화, 생성 전 주제 확인·수정 |
| contentPresentation.ts, ContentEditor.tsx | FAQ 질문·답변 라벨과 기존 편집기 유지 |
| RichEditor.tsx | 유형 전환 시 접근성 라벨 갱신; 편집 내용 유지 |
| workspace.css | 생성 대화상자 주제 선택과 필드 라벨 |
| PostService.java | FAQ 검증 메시지 명칭; 조건·저장 로직은 유지 |
| FAQ 사전 SQL/seed-faq-copy/run-faq-copy | 복사 DB에서 수동 등록·실행 |
| run-classification-copy.ps1 | 공통 실행 안내 문구에서 특정 이전 단계명 제거 |
| FaqWorkflowIntegrationTest, faqNavigation.test.ts | FAQ 흐름·격리·검증·공통 경로 테스트 |
| ReviewWorkflowIntegrationTest | 사전 SQL fixture를 UTF-8로 명시 읽기 |

CreateReview는 보존된 기준점에 포함되어 있으며, 완료 소스에서는 공통 CreateContentDraft로 옮겼다. 기존 Thymeleaf 작성·편집 화면이나 미완성 작업을 삭제하지 않았다.

## 8. 테스트와 증거

- 최종 Spring `clean verify`: **77개 통과, 실패/오류/제외 0**. FAQ 4개 및 기존 후기/인증/미디어/페이지/분류/migration 회귀 포함.
- 프런트 테스트: **15개 통과**. 기존 분류 6개, 후기 4개, FAQ 5개.
- TypeScript/Vite 빌드 성공. 기존 번들 크기와 서버 제공 CSS 경로 안내는 유지.
- 사전 등록 후 기존 22개 테이블과 Flyway 이력이 등록 전 후기 사본과 같음.
- 새 V3 사본의 LocalCopyClassificationMigrationTest도 제외 없이 실행했다. 연결 유지→migrate→재실행→완전 재접속 검사를 통과했다.
- 브라우저에서 초기 주제 선택과 생성 전 변경, FAQ 생성/질문·답변/미리보기/저장/재조회, 유형 변경 경고, 보기 전환, 두 경로 같은 ID, legacy 임시저장/발행/재발행, 실제 목록과 동명 주제 격리를 확인했다.
- 첫 전체 검사에서 한글 사전 이름 비교 1건이 실패했다. Windows 기본 인코딩으로 읽던 테스트 fixture를 UTF-8로 명시해 수정했고, 관련 8개와 최종 전체 77개를 재실행해 통과했다. 실제 수동 등록 DB의 한글 사전은 정상이다.

근거: `.cache/react-phase3c2-final-verify.log`, `react-phase3c2-targeted.log`, `react-phase3c2-pre-edit-audit.json`, `react-phase3c2-runtime-baseline.json`, `react-phase3c2-runtime-audit.json`, `react-phase3c2-draft-publication.json`, `react-phase3c2-legacy-save.json`, `react-phase3c2-republication.json`, `react-phase3c2-final-audit.json`. 화면은 `.cache/react-phase3c2-screenshots/`.

## 9. 3C-3 맛집 전 의견

현재 목표가 맛집 소개 글의 작성·목록·분류 연결이라면 기존 제목/본문/이미지와 RESTAURANT 유형으로 먼저 검증할 수 있다. 추가 DB 필드는 필수가 아니다.

주소를 지도에 연결하거나 지역/거리 검색·공통 식당 카드에 사용할 요구가 확인되면 주소·좌표처럼 기계적으로 다뤄야 하는 값은 본문과 분리할 필요가 있다. 그때 최소 필드와 검증, 초안/발행본 snapshot, 기존 관리자 요청 시 보존 방식을 함께 설계하는 것이 좋다. 별점·작성자 프로필·영업시간·연락처 등을 이번 결과만으로 임의 확정하지 않는다.

파일 DB 연결 수명 문제와 H2/Flyway 호환 경고는 [별도 위험 기록](DB_MIGRATION_RISKS.md)에 남겼다. 복사 DB의 FAQ 기능은 정상 동작하며, 원본 migration 전 재확인 조건은 계속 유지한다.
