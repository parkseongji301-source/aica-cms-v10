# 5D-1 운영 데이터·IA 적용안

**권장안은 기수 2개, 주제 10개, 유형–주제 연결 10개만 먼저 확정 후보로 두는 것이다.** 페이지·블록·메뉴 생성과 기존 콘텐츠 재분류는 이 사전 등록과 분리한다. 이번 단계의 실제 DB 변경은 0건이다.

원본 V10 서버를 종료·재시작하지 않고 기존 조회 API로 확인했다. 기준 관찰 시각은 **2026-09-27T23:49:46.227775+09:00**, PID 9788, `127.0.0.1:8095`다. 승인 RC, schema V10, 기능과 dependency를 변경하지 않았다. 로그인 이외 요청은 GET뿐이며 저장·발행·baseline·migration·사전 등록 요청을 보내지 않았다.

## 실제 V10과 요구사항의 차이

| 대상 | 원본에서 확인한 현재 값 | 이번 적용안의 의미 |
|---|---|---|
| 콘텐츠 유형 | GENERAL / REVIEW / RESTAURANT / INTERVIEW / FAQ, 모두 활성 | 기존 5종 유지 |
| 기수 / 주제 / 허용 연결 | 모두 0건 | 승인 후 2 / 10 / 10건 등록 후보 |
| 콘텐츠 | 9건, 모두 DRAFT·GENERAL, 기수/주제 없음 | 본문 검토 후에도 자동 분류 대상 없음 |
| 페이지 | 홈 1, 인사교 소개 65 | 두 ID 유지. 나머지 IA의 실제 페이지는 없음 |
| 블록 | 홈 HERO·POSTS, 소개 HERO, 총 3개 | 나머지 IA와 연결할 block ID는 아직 없음 |
| 메뉴 | PAGE→1, PAGE→65 두 개 | 전체 IA의 방문자 메뉴가 이미 구현된 상태가 아님 |
| 미디어 / 공용 템플릿 | 모두 0건 | 검증 사본의 fixture를 운영 원본으로 가져오지 않음 |

기술적으로 준비된 기능과 현재 입력된 데이터를 구분해야 한다. 후기/FAQ/맛집 편집·필터 기능은 있지만 실제 운영 글·사전이 이미 들어 있는 것은 아니다.

## 1. 콘텐츠 유형 ↔ IA

| 기존 유형 | 실제 IA 대응 후보 | 원본 내용과 관리 방식 |
|---|---|---|
| GENERAL | 전용 유형으로 분류하기 어려운 일반 게시물 | title/content/richContent와 기존 미디어. 고정 페이지의 모든 문구를 GENERAL 글로 만들지는 않음 |
| REVIEW | 선배들의 SSUL → 후기 | 동일 posts·ContentEditor. 기수와 후기 주제를 선택적으로 복수 지정 |
| RESTAURANT | 인사교 Real Life → 인사교 꿀팁 → 근처 식당(현 관리자 명칭: 근처 맛집) | 식당명·소개·미디어 및 기존 1:1 주소. 기수 기본 0개, 신규 맛집 주제 없음 |
| INTERVIEW | 선배들의 SSUL → 인터뷰 | 공통 필드 재사용. 인터뷰 글/동영상을 자동 구분하는 필드·필터는 현재 없음 |
| FAQ | 지원 전 Check!! → FAQ | title=질문, content/richContent=답변. 기수 강제 없음. 질문 한 건은 post 한 건 |

새 유형은 만들지 않는다. INTERVIEW 유형은 등록돼 있고 전체 콘텐츠 목록의 유형 필터로 조회할 수 있지만, **현재 고정 사이트 구조 트리에는 INTERVIEW 진입점이 없다.** 이를 데이터 등록만으로 연결 완료했다고 표시하지 않는다.

코드 근거: [등록형 유형과 검증](../../../src/main/java/egovframework/backoffice/mvp/classification/ClassificationService.java:15), [유형 baseline](../../../src/main/resources/db/migration/h2/V5__content_classification_baseline.sql:1).

## 2. 운영 기수 후보

| code | 표시 이름 | sort_order 후보 | active 후보 | 현재 허용 유형 |
|---|---|---:|---|---|
| COHORT_06 | 6기 | 6 | true | 5종 모두 선택 가능 |
| COHORT_07 | 7기 | 7 | true | 5종 모두 선택 가능 |

V10에는 기수별 허용 유형을 제한하는 연결 테이블이 없다. 5종 모두 선택 가능하다는 것은 **현재 기술적 허용 범위**이며 새 권한/정책을 만드는 것이 아니다. REVIEW/INTERVIEW에서 실제 관련 기수가 있을 때 사용하고 FAQ/맛집은 기본 선택 없음으로 운영한다. 모든 유형에서 기수 0개·복수 선택은 유지한다.

사전 선택목록의 6→7 순서와 ‘기수별 스토리’의 방문자 표시 순서는 별개다. 스토리의 7→6 탐색 순서를 위해 기수 원본이나 코드를 바꾸지 않는다. COHORT_08 이상을 추가할 수 있는 기존 구조는 유지하되 이번 파일에는 넣지 않는다. **3~5기 및 2027은 등록하지 않는다.**

코드 근거: [기수 테이블](../../../src/main/resources/db/migration/h2/V4__content_classification_schema.sql:7), [기수/주제 선택 검증](../../../src/main/java/egovframework/backoffice/mvp/classification/ClassificationService.java:57).

## 3~4. REVIEW / FAQ 주제 후보

모두 active=true 후보이며, 다음 순서는 기존 검증 후보와 현재 탐색 순서를 유지한 제안이다. topic ID는 환경마다 새로 조회/발급한다.

| 허용 content type | code | 표시 이름 | sort_order 후보 | IA 위치 |
|---|---|---|---:|---|
| REVIEW | REVIEW_LIFE | 생활 | 0 | 선배들의 SSUL → 후기 → 생활 |
| REVIEW | REVIEW_CLASS | 수업 | 1 | 선배들의 SSUL → 후기 → 수업 |
| REVIEW | REVIEW_PROJECT | 프로젝트 | 2 | 선배들의 SSUL → 후기 → 프로젝트 |
| FAQ | FAQ_PREPARATION | 준비사항 | 10 | 지원 전 Check!! → FAQ → 준비사항 |
| FAQ | FAQ_APPLICATION | 지원·선발 | 11 | 지원 전 Check!! → FAQ → 지원·선발 |
| FAQ | FAQ_CLASS | 수업 | 12 | 지원 전 Check!! → FAQ → 수업 |
| FAQ | FAQ_LIFE | 생활 | 13 | 지원 전 Check!! → FAQ → 생활 |
| FAQ | FAQ_EMPLOYMENT | 취업 | 14 | 지원 전 Check!! → FAQ → 취업 |
| FAQ | FAQ_ALLOWANCE | 지원금 | 15 | 지원 전 Check!! → FAQ → 지원금 |
| FAQ | FAQ_PROJECT | 프로젝트 | 16 | 지원 전 Check!! → FAQ → 프로젝트 |

후기 3개와 FAQ 7개는 사용자가 전달한 해당 하위 IA 및 현재 `contentNavigation.ts`의 코드와 **각각 1:1로 대응**한다. 사전과 허용 연결이 들어오면 코드로 해당 환경의 실제 topic ID를 찾아 동일 콘텐츠 목록에 필터를 적용한다. 현재는 사전이 없어 하위 항목이 ‘사전 연결 전’ 상태다. 유형 전체 목록은 이미 조회할 수 있다.

REVIEW_LIFE/FAQ_LIFE, REVIEW_CLASS/FAQ_CLASS뿐 아니라 REVIEW_PROJECT/FAQ_PROJECT도 서로 다른 topic이다. 이름으로 합치지 않는다. 별도 namespace 컬럼을 추가하지 않고 이미 사용 중인 code 구분과 content_type_topics 연결을 그대로 사용한다.

지원금·특강·기업탐방·멘토링 등 후기 상세 이야기는 별도 필터/검색 요구가 없으므로 추가 topic으로 만들지 않는다. FAQ 질문별 topic/page도 만들지 않는다. RESTAURANT·INTERVIEW·GENERAL에 새 주제를 연결하지 않는다.

코드 근거: [현재 탐색 코드](../../../frontend/src/contentNavigation.ts:4), [사전 미연결 처리](../../../frontend/src/ContentTree.tsx:6), [주제 code와 유형 연결 키](../../../src/main/resources/db/migration/h2/V4__content_classification_schema.sql:12).

## 5. 전체 IA 매핑안과 연결 한계

[전체 IA 매핑표](operating-data/IA_MAPPING.md)에 받은 항목 전체를 55행(HOME와 초기 위치 미정 묶음 포함)으로 정리했다. [기계가 읽을 수 있는 매핑 초안](operating-data/ia-mapping.proposed.json)도 같은 내용을 담는다. 이 파일은 **V10이 읽는 실행 설정이나 별도 IA 저장소가 아니다.**

추천한 기본 분담은 다음과 같다.

| IA 영역 | 추천 관리 대상 | 현재 연결 상태 |
|---|---|---|
| 인사교 알아보기 | 기존 page 65 재사용 후보. 설명은 TEXT/IMAGE 등 페이지 블록 | 최종 이름/내용 범위 결정 전 현재 제목·HERO 유지 |
| 2027 인사교 | 소개 페이지의 설명 block 후보 | 실제 block ID 없음. 기수로 해석하지 않음 |
| 인사교 TMI | 지면/탐색상 묶음 | 별도 페이지나 topic 불필요. 임의 IA 그룹 레코드 생성 안 함 |
| 무엇을 배우는지 및 4개 하위 항목 | 상위 설명 block와 그 안의 소제목/본문 | 각각 별도 page/topic으로 만들지 않음 |
| 어떤 걸 하는지 및 6개 하위 활동 | 활동 소개 block와 소제목/본문 | 상세 콘텐츠 목록이 실제 필요해질 때 원본 참조 검토 |
| 취업 서포터 활동 및 3개 하위 항목 | 지원 프로그램 설명 block | 계정 역할 SUPPORTER와 다른 개념 |
| 관련 기업 | TEXT/IMAGE 최소안 후보 | 기업 목록/로고/관계 데이터 형식 결정 필요 |
| 선배들의 SSUL | 스토리·후기·인터뷰의 탐색 묶음, 상위 menu 후보 | 홈페이지에 landing page가 필요한지는 아직 미정 |
| 기수별 스토리 → 7기 / 6기 | content-list + cohort filter | 목록 필터는 가능. 포함 유형과 고정 트리 연결은 미정 |
| 기수별 스토리 → 3~5기 | content-list 후보 | 실제 분류 방식 미정. 코드/ID 배정 안 함 |
| 후기 및 생활/수업/프로젝트 | 기존 content-list + REVIEW/topic filter | 코드 경로 준비됨. 하위 사전 승인 대기 |
| 인터뷰 및 인터뷰 글/동영상 | INTERVIEW content-list | 유형 전체 필터 가능. 고정 트리와 글/영상 분리 조건은 없음 |
| 인사교 Real Life | 생활 안내 page 후보 + 내부 시설/주거/문화·여가/오시는 길 block | 실제 page/slug 없음. 새 페이지 생성 확정 아님 |
| 인사교 꿀팁 → 근처 식당 | 기존 RESTAURANT content-list | ‘근처 맛집’ 진입점 재사용 후보. 최종 명칭만 확인 |
| 지원 전 Check!! | 지원 안내 page 후보 + 자격·일정·절차/혜택 block | 실제 page/slug 없음 |
| FAQ 및 7개 하위 주제 | 기존 content-list + FAQ/topic filter | 코드 경로 준비됨. 하위 사전 승인 대기 |

‘block 안의 소제목’은 독립 block ID 대상이 아니다. 해당 소제목까지 직접 이동하려면 나중에 독립 블록으로 나눌 필요가 있는지 판단해야 한다. 현재 V10은 page→평면 blocks 구조이므로 IA의 깊이만큼 중첩 블록을 임의 생성하지 않는다.

### 실제로 연결 가능한 ID

| 현재 원본 | 실제 ID | 이번 판단 |
|---|---|---|
| 홈 | page 1 / slug home / menu 1 | 유지 |
| 홈 대표 문구 | block_113f930f-0a78-41db-8f74-3b5fc5a5d484 | 기존 HERO 유지 |
| 홈 사관학교 소식 | block_e96cd216-6d06-46d6-805f-97d9cc927832 | 기존 POSTS 유지 |
| 인사교 소개 | page 65 / slug about / menu 2 | 소개 영역 재사용 후보 |
| 소개 페이지의 ‘테스트’ | block_44c17a08-32c1-4bdd-a42c-a4266a9ad033 | 실제 HERO지만 ‘무엇을 배우는지’ 등에 추측 연결하지 않음 |

향후 적용 직전에 다시 실제 ID와 내용을 확인한다. 아직 없는 대상은 매핑 파일에서도 null/미정으로 남겼다. 신규 페이지·블록·메뉴 ID를 검증 DB에서 가져오거나 이름·순서로 추측하지 않는다.

### 사전 데이터만으로 할 수 없는 부분

1. **방문자 메뉴 계층:** site_menus는 PAGE/CATEGORY/LINK와 순서·노출만 저장하며 parent_id, block_id, 유형/기수/주제 필터 칼럼이 없다. `CATEGORY`는 기존 categories이지 신규 topics가 아니다.
2. **관리자 전체 IA:** 현재 트리는 등록된 REVIEW/FAQ/RESTAURANT 탐색 + 실제 메뉴/페이지/블록 조합이다. 별도 전체 IA 레코드를 입력해 4단계 계층을 만드는 기능은 없다.
3. **페이지와 메뉴 이름:** PAGE 메뉴명은 연결 페이지 제목에서 정한다. page 65를 ‘인사교 알아보기’로 보이게 하려고 메뉴 label만 별도로 저장하는 안은 현재 동작과 맞지 않는다.
4. **기수별 스토리:** 관리자 콘텐츠 목록은 복수 유형 필터를 지원하지만 POSTS query는 유형 하나다. REVIEW와 INTERVIEW를 한 목록으로 합쳐 공개할 것인지 미정이다. 승인 없는 유형 선택이나 임의 목록 생성은 하지 않는다.
5. **공개 연결:** 관리자 `content-list` URL은 방문자 화면 URL이 아니다. 공개 API의 일반 posts 목록은 category 기준이고 신규 분류 조건은 페이지 발행 POSTS 블록을 통해 조회한다. 홈페이지 라우터/실제 페이지가 없는 상태에서 관리자 URL을 방문자 메뉴에 넣어 해결하지 않는다.

이번 동결 조건에서는 위 항목을 억지로 DB 데이터로 우회하지 않는다. 전체 IA를 그대로 표현하려면 필요한 탐색/홈페이지 연결 범위를 별도로 승인해야 한다. 지금 사전 후보 승인과 전체 IA 구현 승인은 다른 결정이다.

근거: [메뉴 schema](../../../src/main/resources/db/migration/h2/V2__cms.sql:50), [메뉴 종류·명칭 결정](../../../src/main/java/egovframework/backoffice/mvp/cms/SiteService.java:50), [페이지에서 파생하는 블록 탐색](../../../src/main/java/egovframework/backoffice/mvp/cms/PageService.java:29), [현재 사이드바 조합](../../../frontend/src/main.tsx:89), [POSTS 조건 제약](../../../src/main/java/egovframework/backoffice/mvp/cms/PublishedPostQueryService.java:29).

## 6~7. 중복·모호한 항목과 사람이 결정할 것

| 항목 | 유지할 구분 / 결정 필요 사항 |
|---|---|
| 인사교 알아보기 | 최종 명칭 미확정. page 65 재사용 범위와 page/메뉴 제목 변경 여부 확인 |
| 초기 ‘간단 소개/인프라/후기/기수별 이미지’ 묶음 | 최종 위치 미정. 소개/Real Life/SSUL에 이름만 보고 흡수하지 않음 |
| 3~5기 | 실제 3·4·5기 각각과 OR 탐색 묶음인지, 하나의 집계 기록인지 결정. 이번 등록 0건 |
| 2027 인사교 | 연도별 소개인지 특정 기수 안내인지 결정. COHORT_07이나 새 기수로 자동 변환 금지 |
| 주거 / 문화·여가 | 설명 블록으로 충분한지, 개별 원본·반복 목록 관리가 필요한지 결정 |
| 관련 기업 | 기업명·로고·관계/참여 정보의 실제 시안 필요. 새 유형/상세 필드 제안 확정 안 함 |
| 인터뷰 동영상 | 공통 필드로 충분한지, 영상 URL·썸네일·인터뷰이 구조화와 글/영상 구분이 필요한지 결정 |
| 근처 식당 / 근처 맛집 | 같은 RESTAURANT 영역으로 보는 후보. 최종 표시명 확정 필요. 새 topic을 만들 이유는 없음 |
| 내부 시설 / 인사교 인프라 | 소개성 설명과 실제 생활 가이드의 역할 확인. 본문 두 벌 자동 생성 금지 |
| 수업·프로젝트·생활 | REVIEW와 FAQ는 의미/유형이 달라 코드와 ID 분리. TMI 활동 소개는 topic 자체가 아님 |
| 지원 혜택·지원금·받아본 후기 | 공식 안내 block, FAQ_ALLOWANCE 답변, REVIEW_LIFE 경험을 구분. 같은 내용을 세 저장소에 자동 복사하지 않음 |
| 특강 / 멘토링 / 기업탐방 | 상세 필터 요구 전까지 본문/소제목으로 관리. 신규 주제 추가 안 함 |
| 기수별 스토리 | 포함할 유형 범위, 7→6 표시 순서, 공개 페이지의 목록 구성 결정 |
| 상위 4개 메뉴 | 방문자 landing page 유무, 실제 URL, 하위 메뉴 노출 깊이 결정. flat DB에 가짜 계층 행 입력 금지 |
| 운영 사전 22행 | 이름·설명·정렬·활성·유형 허용값을 최종 검토하고 별도 적용 승인 |

원본의 두 가지 운영 콘텐츠 정리 사항도 확인했다. page 65는 현재 제목만 소개이고 HERO 본문/버튼이 ‘테스트’, 링크는 `http://127.0.0.1:8081/admin/pages/65/edit`이다. 실제 공개 사용 전 문구와 연결을 운영자가 확정해야 한다. 홈 POSTS는 sourceMode/categoryId가 모두 null인 기존 방식이어서 **전체 발행 콘텐츠**를 조회한다. 향후 FAQ/후기 등을 발행할 때 홈 소식에 어떤 유형을 보여줄지 먼저 정해야 한다. 이번에는 둘 다 수정하지 않았다.

## 8. 운영용 사전 적용 파일 초안과 idempotent 절차

[운영 사전 후보 JSON](operating-data/operating-dictionary.proposed.json)에 code/name/description/sortOrder/active/허용 유형을 모두 명시했다. status는 `PROPOSED_NOT_APPROVED_NOT_APPLIED`, executable=false다. **현재 CMS가 자동으로 읽는 설정이나 이미 구현된 importer가 아니라 검토용 선언형 적용 파일**이다. V1~V10 경로 밖에 두었고 실행 SQL을 보내지 않았다.

현재 분류 API는 GET만 제공한다. 사전 관리 UI/API가 있다고 가정하지 않는다. 확정 후 기존 H2 유지보수 실행 경로를 이용한 통제된 DML 작업으로 다루며, 이번에 새 CMS 기능이나 runner를 구현하지 않는다. 과거 `seed-*-copy.ps1`과 smoke fixture는 사본용이므로 원본 등록 도구로 전용하지 않는다.

후속 적용 절차는 다음과 같다.

1. 후보 파일과 사람이 승인한 diff를 고정한다. 유형 5종은 기존 값을 검사만 하고 INSERT/UPDATE하지 않는다. 사전 2/10/10 외 변경은 거부한다.
2. 운영 쓰기를 중단하고 V10 서버를 정상 종료한다. PID·Hikari·파일 잠금 해제를 확인한 뒤 **적용 직전 V10 DB + 현재 V10 runtime/receipt/설정**을 새로 백업한다. 이번 데이터 등록의 복구 기준은 V3가 아니다.
3. 새 V10 사본에서 code/name/description/active/sort_order와 허용 연결을 읽고 diff를 만든다. 신규 code는 추가, 기존 code의 모든 값이 같으면 no-op, 값이나 허용 범위가 다르면 STOP한다. 비활성 값을 자동 활성화하거나 이름·정렬을 덮어쓰지 않는다.
4. 사본의 단일 트랜잭션에서 필요한 행만 추가한다. cohorts/topics INSERT에 숫자 ID를 넘기지 않는다. 이후 **대상 DB에서 code로 실제 id를 조회**하여 content_type_topics를 추가한다. 이미 동일 연결이 있으면 no-op이다.
5. REVIEW는 정확히 3개 후보, FAQ는 정확히 7개 후보 허용 관계인지 확인한다. 다른 유형에 후보 topic이 연결됐거나 예상 외 주제가 허용돼 있으면 자동 삭제하지 말고 STOP한다. 같은 이름의 서로 다른 code는 정상이다.
6. 같은 승인 입력으로 사본에 다시 실행한다. 추가/수정/삭제 0건, 기존 숫자 ID와 지문 유지, 중복 0건을 확인한다. 대상 글·발행본·페이지·메뉴·version 테이블은 변경되지 않아야 한다.
7. 2·10·10개와 허용 링크, code 고유성, 같은 이름의 별도 ID, 0개/복수 선택, 유형별 검증 및 REVIEW/FAQ 필터 격리를 사본에서 검사한다. 검증 글/계정은 사본에만 만든다.
8. 별도 원본 적용 승인 후, 원본이 백업 당시 V10/hash/fingerprint 그대로인지 확인하고 동일 DML/트랜잭션을 적용한다. 모든 사전 값과 관계를 검증한 뒤에만 commit한다. 실패 시 트랜잭션 rollback; 종료/재기동 검사까지 실패하면 보존 후 **직전 V10** 백업·대응 runtime 복구 절차로 처리한다.
9. V10 `serve`/validate-only, `AUTO_COMPACT_FILL_RATE=0`을 그대로 사용한다. cutover/migration 승인 flag를 다시 열지 않는다. 결과 code→숫자 ID 매핑, 반영 파일 hash, 실행자·시각·전후 diff를 별도 운영 증거로 남긴다. 현재 사전에는 전용 감사 저장 API가 없으므로 활동 이력이 자동 생긴다고 주장하지 않는다.

이후 값 변경은 새로운 승인된 변경 묶음으로 처리한다. 같은 초안을 반복 실행한다고 운영자가 나중에 바꾼 이름/활성/정렬을 덮어쓰면 안 된다. 사전 code는 탐색 식별자라서 변경하지 않는 기준을 추천한다. label이 코드에 등록된 탐색명과 달라질 경우 명칭 일관성도 별도로 검토한다.

근거: [읽기 전용 사전 API](../../../src/main/java/egovframework/backoffice/mvp/next/NextClassificationApi.java:13), [사전 조회와 정렬](../../../src/main/resources/mapper/ClassificationMapper.xml:4).

## 9. 기존 9개 콘텐츠 분류 제안

전부 실제 본문을 읽었다. ID 1~8은 본문에 ‘디자인 검토를 위한 예시 콘텐츠입니다’와 화면 확인용 공통 설명이 있다. ID 33은 제목/본문 모두 ‘아아’다. 제목에 프로젝트·특강·교육 공간이 있다고 후기/주제/기수를 추측할 근거가 되지 않는다.

공통 현재 값은 DRAFT, revision 0, GENERAL, 기수 [], 주제 [], richContent=null, 미디어 없음이다.

| ID | 제목 | 기존 category_id | 추천 운영 분류 | 근거 / 확정 필요 |
|---:|---|---|---|---|
| 1 | 인공지능 사관학교 교육 공간을 소개합니다 | null | GENERAL / [] / [] 유지 | 예시 본문. 실제 시설 소개로 사용할지 내용 확정 필요 |
| 2 | 현업 멘토와 함께하는 AI 프로젝트 이야기 | null | GENERAL / [] / [] 유지 | 실제 후기·멘토링 기록 없음. REVIEW_PROJECT 자동 부여 금지 |
| 3 | 교육생을 위한 학습 자료 이용 안내 | null | GENERAL / [] / [] 유지 | 예시 본문. 실제 안내 원문인지 확인 필요 |
| 4 | 함께 성장하는 사관학교, 프로젝트 데모데이 현장 | null | GENERAL / [] / [] 유지 | 제목만 현장 기록 형태이며 본문은 예시. 기수·후기 추정 금지 |
| 5 | AI 개발자를 위한 커리어 특강 안내 | null | GENERAL / [] / [] 유지 | 예시 본문. 특강 topic 생성/연결하지 않음 |
| 6 | 새로운 도전의 시작, 교육과정 안내 | null | GENERAL / [] / [] 유지 | 예시 본문. FAQ_CLASS나 REVIEW_CLASS로 분류하지 않음 |
| 7 | 인공지능 사관학교 홈페이지 이용 안내 | null | GENERAL / [] / [] 유지 | 예시 본문. 실제 운영 안내 사용 여부 확인 필요 |
| 8 | 인공지능 사관학교의 새로운 소식을 전합니다 | null | GENERAL / [] / [] 유지 | 예시 본문. 실제 공지로 쓸지는 운영자 결정 |
| 33 | 아아 | 2 (교육 소식) | GENERAL / [] / [] 유지 | 제목/본문만으로 의미 없음. category 이름으로 수업 주제나 기수 추정 금지 |

실제 원문을 확인한 뒤에만 운영자가 공통 ContentEditor에서 분류한다. 이번 초안은 기존 글을 삭제·비공개 전환·발행하거나 내용을 교체하는 승인도 아니다. 분류 변경은 기존 초안/revision/version 규칙을 따르며 공개 분류는 재발행 전 유지된다.

## 10. 실제 DB에 적용될 변경 목록

**이번 단계: 모든 테이블 0건 변경.** 이후 이 사전 초안만 별도 승인해 적용할 경우 현재 빈 사전을 기준으로 다음이 예상된다.

| 테이블/영역 | 최초 적용 예상 | 동일 입력 재실행 |
|---|---:|---:|
| cohorts | INSERT 2 | 0 |
| topics | INSERT 10 | 0 |
| content_type_topics | INSERT 10 | 0 |
| content_types | 검사만, 변경 0 | 0 |
| 기존 categories/category_id | 0 | 0 |
| posts 및 기수/주제 연결·맛집 주소 | 0 | 0 |
| post_publications 및 발행 분류/주소 | 0 | 0 |
| site_pages/page_publications/블록 ID registry | 0 | 0 |
| site_menus/site_links/site_settings | 0 | 0 |
| versions/미디어/템플릿/계정·역할 | 0 | 0 |
| schema/Flyway history/V1~V10/dependency | 0 | 0 |

등록만 해서는 실제 콘텐츠가 생기지 않으며 기존 GENERAL 글이 후기/FAQ/맛집 목록으로 옮겨지지 않는다. 홈페이지 page/POSTS query/menu 구성은 콘텐츠와 IA가 확정된 후 별도 승인 대상으로 남긴다.

검증은 JSON 형식·코드 고유성·기수/주제/허용 링크 수·IA의 코드 참조·숫자 사전 ID 미포함을 정적으로 확인했다. **DML 실행이나 사본 등록 리허설도 이번에는 하지 않았다.** 실제 등록의 원자성·idempotence는 후속 승인 단계의 사본 검증 항목이다.

최종 재조회 시각은 **2026-09-28T00:03:26.820602+09:00**이다. 최초 조회와 13개 응답 묶음(9개 콘텐츠와 2개 페이지 상세, 공개 페이지·메뉴, 분류, 활동 이력 등을 포함)을 비교해 차이 0건을 확인했다. 콘텐츠/페이지 revision·초안·발행값도 유지됐다. 서버는 기존 PID 9788로 계속 실행 중이며 이번 작업에서 재시작하지 않았다.

전환 기준점의 기존 소스 368개 및 승인 release 묶음 15개 파일을 SHA-256으로 비교해 모두 동일했다. 새로 만든 것은 이 보고서와 운영 후보 JSON 2개, 전체 IA 매핑표뿐이다. 실행 중인 DB 파일을 직접 열거나 일관되지 않은 hot-file 해시로 무변경을 주장하지 않았다. [조회 전후 및 파일 검증 기록](../../../.cache/phase5d1/20260927-234946/no-change-verification.json), [후보 파일 정적 검사 기록](../../../.cache/phase5d1/20260927-234946/artifact-static-check.json)을 남겼다.
