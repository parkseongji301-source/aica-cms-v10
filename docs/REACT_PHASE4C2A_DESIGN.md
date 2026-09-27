# 4C-2A 직접 선택 설계

2026-09-27. 최신 4C-1 V8 검증 DB를 새 사본으로 복제한다. 원본 V3 및 기존 V1~V8 migration은 변경하지 않는다.

## 저장 계약

기존 schemaVersion 2 평면 블록에 optional `manual` 객체만 추가한다.

```json
{
  "id": "block_UUID",
  "type": "POSTS",
  "schemaVersion": 2,
  "variation": "default",
  "sourceMode": "manual",
  "categoryId": null,
  "query": {"typeCode":"REVIEW","cohortIds":[],"topicIds":[],"sort":"LATEST","limit":6},
  "manual": {"postIds":[97,105,103]}
}
```

- manual에는 원본 ID만 저장하며 제목·본문·발행 상태를 복사하지 않는다.
- category/query/manual 전환 시 나머지 설정은 보관한다. 선택한 모드만 결과에 영향을 준다.
- 배열 순서가 노출 순서다. 0~20개, 양수 ID, 중복 없음으로 검증한다. query의 개수 제한과 별도로 manual은 선택한 항목을 최대 20개까지 표시한다.
- 삭제되거나 찾을 수 없는 ID도 정수 형식이 유효하면 보존한다. 원본의 존재/발행 상태 때문에 저장된 목록을 자동 정리하지 않는다.
- 기존에 manual이 저장된 블록에서 필드가 누락되면 저장을 거절한다. 비우기는 명시적인 `{postIds:[]}`로 처리한다.
- DB 변경·migration·분류 사전/전체 IA 등록은 없다. 구버전 실행 파일은 새로운 필드를 모르므로 DB/JAR를 함께 복구한다.

## 편집과 상태

기존 ContentEditor와 PostService를 그대로 사용한다. 선택 창은 기존 `/api/admin/next/posts`의 q/typeCodes/cohortIds/topicIds/page를 호출한다. 검색은 기존과 같이 초안 제목·본문·분류 기준이다. 새 검색 시스템이나 콘텐츠 생성기를 만들지 않는다.

미발행 글도 선택 가능하게 한다. 선택한 목록에 발행/미발행/비공개/삭제됨/사용 불가를 구분하며, 발행 제목과 초안 제목이 다르면 발행 제목도 표시한다. 상태 조회 실패는 사용 불가로 오인하지 않도록 별도 안내한다. 상태 새로고침과 창 포커스 복귀 시 재조회를 지원한다. 공개 결과는 아래 발행 기준으로만 구성한다.

위/아래 버튼과 선택 해제를 지원한다. 새 선택은 배열 마지막에 추가되고 중복 선택 버튼은 비활성화한다. 블록 복제는 기존 structuredClone + 새 UUID를 재사용하므로 postIds 배열도 독립적이다.

## 조회와 snapshot

`PublishedPostQueryService`에서 기존 publicPosts/publicPostCount의 발행본 조회에 선택 ID 조건을 추가한다. Mapper의 SQL IN은 순서를 보장하지 않으므로 서비스가 postIds 순서로 다시 배열한다. 미발행/비공개/삭제/발행본 없음은 결과에서만 제외한다. count는 실제 공개 가능한 항목 수다. 빈 배열은 전체 콘텐츠로 해석하지 않고 0건을 반환한다.

콘텐츠 제목/본문은 post_publications에서 읽으며 posts는 공개 상태/삭제 여부 확인에만 사용한다. 콘텐츠 초안 수정은 공개 결과와 분리하고 재발행하면 최신 발행본이 반영된다. 페이지 목록·순서는 page_publications의 sections_json에 별도 snapshot으로 남는다. 페이지 초안 저장만으로 공개 선택 순서가 바뀌지 않는다.

## API/호환

- 기존 페이지 GET/PUT/preview/publication API가 manual 필드를 함께 처리한다. 별도 블록 저장 API는 없다.
- 신규 `GET /api/admin/next/pages/selected-posts?ids=...`는 선택된 ID의 관리자 표시용 상태를 배치 조회한다. 페이지 관리 권한을 적용하고 요청 순서를 유지한다. 콘텐츠를 찾는 검색 API를 대체하지 않는다.
- POSTS 등록 정의에 manual 입력/source mode/maxManualItems를 추가한다. Variation은 default 그대로다.
- Thymeleaf는 manual을 카드에 보관하고 저장·발행 요청에 그대로 포함한다. query/manual 모드에서 기존 category 입력은 비활성화하고 React 편집을 안내한다.

사이트 구조의 block ID 직접 이동, 드래그 정렬, 템플릿, 블록 테이블화는 4C-2A 범위에서 제외한다.
