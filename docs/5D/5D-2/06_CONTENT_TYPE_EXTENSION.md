# 06. 콘텐츠 유형 확장 가이드

대상: 개발자. 현재 GENERAL/REVIEW/RESTAURANT/INTERVIEW/FAQ 5종을 유지한다. 이 문서는 향후 승인된 확장 방법이며 새 유형이나 운영 사전을 등록하는 작업이 아니다.

## 이미 구현된 세 사례

| 유형 | 공통 필드 재사용 | 구조화 저장 | UI·검증 |
|---|---|---|---|
| REVIEW | title/content/richContent/미디어 | 별도 후기 필드 없음 | 기수·후기 주제를 선택적으로 복수 지정 |
| FAQ | title=질문, content/richContent=답변 | 질문/답변 전용 테이블 없음 | 공통 편집기의 명칭 변경, FAQ 허용 주제 검증, 기수 강제 없음 |
| RESTAURANT | title=식당명, content/richContent=소개, 기존 미디어 | posts 1:1 주소 확장과 publication 1:1 주소 snapshot | 해당 유형일 때만 주소 UI, 주소만 수정해도 변경/revision 감지 |

INTERVIEW는 현재 공통 필드를 사용한다. 영상 URL/썸네일/인터뷰이 필드는 실제 요구가 없으므로 미구현이다. 새 유형마다 별도 저장소를 만드는 것이 기본이 아니다.

## 설계 기준

유형은 글당 1개이며 향후 입력폼과 검증의 기준이다. 기수/주제는 선택적 복수 분류다. 홈페이지의 노출 위치는 페이지/POSTS 참조다. 분류 이름이나 제목을 보고 기존 글을 자동 변환하지 않는다. 기존 posts ID와 category_id는 보존한다.

공통 제목·rich text·첨부로 충분하면 REVIEW/FAQ 방식으로 시작한다. 주소처럼 독립 조회/검증이 필요한 값이 확인됐을 때만 최소 1:1 확장 구조를 설계한다. 공통 posts에 유형별 선택 컬럼을 계속 늘리거나 필요 없는 필드를 미리 추가하지 않는다.

## 승인 후 구현 순서

1. **등록형 타입 코드/명칭을 정의한다.** `content_types`의 기존 값과 `ClassificationService.REGISTERED_TYPES`, 프런트 타입·표현, 공개 계약을 함께 확인한다. DB에 code 한 행을 넣는 것만으로 구현 완료가 아니다. 임의 타입 생성 운영 UI는 없다.
2. **공통 필드·상세 필드를 구분한다.** 상세 테이블이 필요하면 post ID 기준 1:1, 제약·삭제·빈 값 의미·다른 유형으로 전환할 때의 처리를 설계한다.
3. **PostService 흐름에 연결한다.** 현재 actor/소유권·분류·미디어·revision·transaction 안에서 저장한다. 별도 컨트롤러가 독립적으로 원문을 저장하지 않는다.
4. **발행 snapshot을 추가한다.** 새 상세 데이터가 초안에서 바뀌어도 기존 공개본에 반영되지 않도록 publication용 복사본을 발행 트랜잭션에 포함한다. 공개 서비스가 초안 상세를 조인하면 안 된다.
5. **version snapshot·복구를 추가한다.** `VersionSnapshots`, `VersionRestoreService`와 비교 UI에 상세를 포함한다. 과거 snapshot schema와 누락 필드 호환을 먼저 정한다. 작성자/ID/공개 상태는 복구로 바꾸지 않는다.
6. **미디어 참조를 연결한다.** 상세에 미디어 ID가 들어가면 초안·publication·version 참조 추출, 사용처, 삭제 차단, 공개 허용 규칙을 함께 구현한다. 외부 URL을 로컬 미디어로 간주하지 않는다.
7. **허용 주제 관계를 정한다.** content_type_topics의 code→실제 topic ID 관계를 사용한다. 같은 이름을 이름으로 합치지 않는다. 기수는 현재 유형별 제한 테이블 없이 선택 가능하다.
8. **React UI·요청을 연결한다.** `contentPresentation.ts`, `ContentEditor`, `contentDocument.ts`, `ClassificationFields`, `api.ts`, `types.ts`와 해당 상세 필드 helper를 점검한다. 미저장 지문·자동저장·미리보기·발행본 비교에 상세값이 포함돼야 한다.
9. **기존 Thymeleaf 호환을 정한다.** 신규 필드가 없는 요청은 이미 저장된 상세/분류를 보존한다. 누락과 명시적으로 비우는 요청을 구분하며 고급 편집은 React로 유도한다.
10. **공개 DTO와 조건 조회를 확인한다.** 상세값은 해당 유형의 발행본에만 노출한다. 새로운 콘텐츠 유형도 POSTS의 category/query/manual과 공개 메뉴 상태 검사에서 같은 원본을 사용한다.
11. **필요한 migration·운영 데이터 적용을 분리한다.** 유형 등록형 기술 baseline과 고객이 정하는 주제/기수 사전은 같은 작업이 아니다. 새 schema는 V11 이상, 기존 V1~V10은 동결한다.

현재 RESTAURANT 주소는 draft `post_restaurant_details`, 공개 `post_publication_restaurant_details`, history JSON의 `restaurant`에 각각 보관된다. 타입 변경 시 현재 상세 정리/보존 규칙과 발행본 독립성을 재사용한다. 주소를 모든 콘텐츠 응답의 공통 필수값으로 바꾸지 않는다.

## 회귀 검증 기준

- 생성·초안 저장·재조회·상세값 단독 변경·자동저장 지문·같은 화면 보기 전환.
- 잘못된 유형/허용되지 않은 주제 차단, 같은 표시명의 서로 다른 topic 격리.
- 초안 상세/분류 변경 → 공개 API 불변 → 발행 → 새 snapshot 반영.
- manual에 연결된 원본의 재발행 반영, query는 발행 당시 분류 기준.
- AUTOSAVE 이력 없음, MANUAL_DRAFT/PUBLISH 이력 있음, 전체 상세 복구 후 공개본 유지.
- Thymeleaf 저장/발행에 신규 필드가 없어도 값 보존.
- 대상 삭제/공개 중단과 미디어 보호, ADMIN/SUPPORTER 권한, revision 충돌 rollback.
- 기존 GENERAL/REVIEW/FAQ/RESTAURANT/INTERVIEW와 페이지·템플릿·공개 API 회귀.
- 새 migration 사본 전후 원본 ID/category 연결 보존, 정상 종료·재시작 지속성.

근거: [ClassificationService](../../../src/main/java/egovframework/backoffice/mvp/classification/ClassificationService.java), [PostService](../../../src/main/java/egovframework/backoffice/mvp/post/PostService.java), [RestaurantDetailsService](../../../src/main/java/egovframework/backoffice/mvp/restaurant/RestaurantDetailsService.java), [snapshot](../../../src/main/java/egovframework/backoffice/mvp/version/VersionSnapshots.java), [유형별 표시](../../../frontend/src/contentPresentation.ts), [맛집 migration](../../../src/main/resources/db/migration/h2/V7__restaurant_details.sql).
