# 4A 블록 저장 기반 설계 (DB 변경 전)

2026-09-27. 3C는 완료, 인터뷰 전용 필드는 실제 시안 요구가 생길 때 검토한다. 원본 V3에는 적용하지 않는다.

## 현재 코드와 실제 데이터

- site_pages.sections_json과 page_publications.sections_json은 CLOB 안의 JSON 배열이다. CmsModels.Section은 type/heading/body/bodyDoc/imageId/categoryId/link/label/visible를 읽는다.
- 종류는 HERO/TEXT/IMAGE/POSTS/CTA. visible boolean으로 표시를 제어하며 배열 순서가 화면 순서다. 별도 블록 ID/정렬 값/버전은 없다.
- PageService.save가 전체 배열을 검증·직렬화한다. publishPage는 site_pages의 JSON과 revision을 발행 테이블에 복사한다. 초안/발행 snapshot은 독립이다.
- NextPageApi는 sections 배열로 조회/저장/미리보기를 제공한다. React PageEditor는 현재 메모리 전용 randomUUID 키 배열을 쓰고 재조회 때 다시 만든다. Thymeleaf cms.js도 DOM 전용 key를 만들며 제출할 때 type과 입력 필드만 보낸다.
- 최신 V7 사본 실제 페이지 1(home): revision=1, HERO→POSTS, bodyDoc 필드 없음. 발행 revision=1과 JSON이 일치한다.
- 실제 페이지 65(about): revision=4, HERO 1개, heading/body/label=테스트, bodyDoc은 Quill Delta 문자열, link는 기존 8081 편집 주소. 발행 revision=4와 JSON이 일치한다. 문구·링크를 임의 수정하지 않는다.
- 사본의 모든 행/원문은 .cache/react-phase4a-baseline.json에 보존했다.

## 최소 확장

기존 배열과 필드를 유지하고 각 블록에 3개 필드만 추가한다.

```json
[{"id":"block_<UUID v4>","schemaVersion":2,"type":"TEXT","variation":"default","heading":"...","body":"...","visible":true}]
```

기존 형식은 암묵적 v1, 새 블록은 v2다. 빈 배열에는 식별할 블록이 없으므로 별도 envelope를 만들지 않는다. type을 등록 컴포넌트 종류로 사용하고 variation은 현재 default만 허용한다. 지원하지 않는 version/variation/필드는 저장 시 거절해 조용히 손실시키지 않는다. 향후 정식 등록형 variation·타입별 데이터 변환을 버전에 맞춰 확장하며 자유 코드 입력은 제공하지 않는다.

UUID는 Java UUID.randomUUID / 브라우저 crypto.randomUUID로 생성한다. 내용·배열 위치·표시 순서를 ID에 사용하지 않는다. 동일 ID의 내용/순서/숨김 변경은 유지, 신규/복제는 새 UUID, 중복 ID 제출은 거절한다.

## ID 재사용 방지

page_block_identities(block_id VARCHAR(42) PK, page_id BIGINT nullable FK site_pages(id) ON DELETE SET NULL, retired BOOLEAN NOT NULL)를 추가한다. 데이터 본문은 JSON 한 곳에만 있고 이 표는 ID 소유/폐기 기록만 갖는다. 삭제된 블록은 retired=true로 남기며, 페이지 삭제 후에도 ID 기록은 유지해 다른 블록/페이지에서 재사용하지 못한다. 현재 초안에서 없어진 발행본 전용 ID도 폐기 기록으로 남기지만 발행 snapshot 자체는 변경하지 않는다.

기존 PageService의 CMS lock·revision·권한·트랜잭션 안에서 JSON과 ID 등록/폐기를 함께 저장한다. UI의 새 UUID는 저장 시 등록한다. 다른 페이지 ID, 폐기된 ID, 현재 초안에 없는 과거 ID의 재사용을 거절한다. 복제 버튼/API는 추가하지 않고 서버 수준 복제 함수는 데이터만 복사하고 새 ID를 발급한다.

## 기존 데이터 변환

새 Java Flyway V8은 모든 JSON을 먼저 검증한 후 등록 표와 메타데이터를 추가한다. 기존 V1~V7은 수정하지 않는다. 내용 필드는 JSON 노드 수준에서 그대로 보존하며 title/slug/ID/revision/날짜/미디어 연결도 바꾸지 않는다.

같은 페이지에서 **초안 revision=발행 revision이고 JSON도 완전히 같은 경우**는 기존 publishPage가 복사한 동일 snapshot이므로 UUID 집합을 한 번 생성해 양쪽에 적용한다. 이는 내용에서 ID를 생성하거나 다른 revision의 블록을 유사도로 추측하는 작업이 아니다. 현재 페이지 1·65가 여기에 해당한다.

revision이 다르거나 문서가 다르면 과거 블록 대응은 증명할 수 없다. 각 snapshot에 별도 UUID를 부여하고 불확실한 연결을 만들지 않는다. 이후 재발행부터는 초안 ID가 그대로 발행 snapshot에 복사된다. 같은 revision인데 문서가 다른 비정상 경우도 추측하지 않는다.

이미 v2인 블록의 ID는 그대로 사용한다. 재실행해도 ID/본문/등록 기록을 바꾸지 않는다. 잘못된 JSON·중복 ID·미지원 형식은 사전 검사에서 중단한다. 실패한 Flyway 이력은 자동 repair하지 않고 DB/JAR 기준점으로 복구한다.

## 양쪽 관리자와 API

React의 별도 임시 키 배열을 없애고 저장된 block ID로 렌더링/비동기 업로드 대상을 찾는다. 기존 추가·삭제·위아래 버튼만 유지한다. Thymeleaf cms.js도 ID/version/variation을 제출 때 보존하고 새 블록에 UUID를 부여한다. 이전 앱/오래 열린 폼이 ID 없이 기존 페이지를 덮어쓰려 하면 재조회 오류로 막는다. 배열 위치로 ID를 복구하지 않는다.

신규 페이지의 서버 생성 흐름은 ID 없는 새 블록에 UUID를 발급할 수 있다. 기존 페이지의 새 블록은 클라이언트가 새 UUID를 보낸다. 기존 API URL과 sections 배열을 유지하고 미리보기에도 ID를 포함한다. 페이지 발행본 읽기 전용 API를 제공해 두 snapshot의 ID/순서를 검증할 수 있게 한다. 저장/발행 권한과 UI 정책은 바꾸지 않는다.

## 변경 및 검증 범위

- 신규 V8 Java migration, 블록 ID Mapper/서비스 및 테스트.
- CmsModels.Section, PageService, NextPageApi, PageController의 기존 저장 흐름.
- React types/PageEditor/블록 유틸, Thymeleaf cms.js.
- 기존 migration 테스트를 각 단계 target에 고정하고 V7→V8 및 기존 회귀를 함께 실행.
- 새 4A 검증 사본/포트8086, 작업 전·후 DB/JAR/source 복구 기록.
- 내용/ID/revision 보존, 재실행·재시작, 순서·내용·신규·복제·삭제/재사용 거절, 초안/발행 분리, legacy/React, 권한/CSRF/충돌 및 콘텐츠 회귀를 검증한다.

4B의 편집 UI, 드래그/복제 버튼, 블록 직접 이동, 새 컴포넌트/콘텐츠 조건/템플릿은 추가하지 않는다.
