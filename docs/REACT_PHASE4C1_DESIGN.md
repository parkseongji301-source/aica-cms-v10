# 4C-1 POSTS 조건 연결 설계

2026-09-27. 기존 4B V8 검증 DB를 새 사본으로 복제한다. 원본 V3, 기존 migration V1~V8 및 미완성 V4 격리는 변경하지 않는다.

## 현재 구조와 최소 확장

`sections_json`은 schemaVersion 2의 평면 블록 배열이다. 기존 POSTS는 `id`, `type`, `heading`, `categoryId`, `visible`, `variation` 등을 가진다. page_publications는 배열 전체를 snapshot으로 복사한다. 기존 공개 콘텐츠 Mapper에 발행본 typeCode/cohortIds/topicIds 조건과 EXISTS 기반 목록/count가 이미 있다. 4B의 페이지 미리보기는 POSTS에 빈 자리만 보여주고 있었다.

```json
{
  "id": "block_UUID",
  "schemaVersion": 2,
  "type": "POSTS",
  "heading": "생활 후기",
  "visible": true,
  "variation": "default",
  "categoryId": null,
  "sourceMode": "query",
  "query": {
    "typeCode": "REVIEW",
    "cohortIds": [102],
    "topicIds": [201],
    "sort": "LATEST",
    "limit": 6
  }
}
```

숫자는 설명용이며 실제 사전의 ID를 선택한다. sourceMode 누락/null/category는 기존 categoryId 모드다. query 모드는 categoryId를 삭제하거나 변환하지 않고 조회 시 무시한다. 모드 전환 후 기존 선택을 복원할 수 있도록 categoryId/query를 보존한다. 유형은 단일 선택, 기수·주제는 0개 이상 선택, 최신순만 제공하고 1~20개로 제한한다. 기존 category 모드는 최신순 6개다.

이번 변경은 optional 필드 추가이므로 schemaVersion 2와 flat array를 유지한다. DB migration과 과거 JSON 일괄 변환은 없다. 다만 예전 4B 실행 파일은 새 query 필드를 모른다. 동일 V8이어도 실행 파일과 DB를 함께 복구해야 한다.

## 조회와 발행 경계

- `ClassificationService.filter`로 기존 ID/등록 타입 검증을 공유하고 선택 유형의 허용 주제도 확인한다. 잘못된 조건은 저장/미리보기에서 거절한다. 주제는 이름이 아닌 ID로 구별한다.
- `PublishedPostQueryService`가 기존 CmsMapper의 publicPosts/publicPostCount 및 publishedClassificationFilter를 재사용한다. 조회/count SQL을 새로 복제하지 않는다.
- post_publications의 type/category, post_publication_cohorts/topics의 연결을 사용한다. posts에서는 공개 상태·삭제 여부·동일 ID만 확인한다. 분류/본문/제목은 발행본 기준이다.
- 동일 기준 IN/EXISTS=OR, 서로 다른 기준=AND. published_at DESC, post ID DESC로 정렬한다. count는 LIMIT 이전 전체 수, items는 제한 개수다.
- 페이지 초안 미리보기: 현재 초안 query × 발행 콘텐츠. 페이지 발행본 미리보기: 저장된 발행 query × 발행 콘텐츠. 콘텐츠를 재발행하면 최신 발행 콘텐츠가 조건에 따라 갱신된다. 페이지에 콘텐츠 자체를 복제해 고정하지 않는다.
- 페이지 저장/발행/권한/revision/CSRF와 block ID 관리 서비스는 그대로 사용한다. 블록별 저장 API를 만들지 않는다.

## 편집기와 호환

- 등록 POSTS 정의에 query 지원, source mode, 정렬, 개수 한계를 추가한다. Variation은 default 그대로다.
- 기존 PageEditor 선택 블록 아래 소스/유형/기수/주제/정렬/개수 및 결과 미리보기를 표시한다. 다른 유형의 주제는 자동 삭제하지 않고 명시적 해제를 요구한다.
- query 배열을 포함한 복제는 deep copy, 새 UUID를 사용한다. 이동·일반 수정은 기존 ID를 유지한다.
- Thymeleaf 카드에 sourceMode/query 원문을 보관해 read/save/publish payload에 포함한다. query 모드에서는 기존 category picker 편집을 막고 React 안내를 보여준다. 새로운 조건을 누락한 오래된 요청은 서버에서 저장을 거절한다.
- 기존 preview 응답에 posts/total을 추가하고 발행본 비교용 읽기 API `GET /api/admin/next/pages/{id}/publication/preview`를 추가한다. 기존 manager 권한을 적용한다. 공개 홈페이지 자체는 이번 범위에 없다.

## 제외 및 다음 단계

직접 콘텐츠 선택, 수동 순서, block ID 직접 이동, 신규 Variation, 템플릿, IA/사전 자동 등록, 원본 migration은 제외한다. 4C-2의 직접 선택은 sourceMode 확장과 발행본 기반 조회 재사용이 가능하다. block ID 선택 상태가 이미 독립적이므로 URL/트리에서 선택 ID를 전달할 수 있으나 해당 동작은 이번에 구현하지 않는다.
