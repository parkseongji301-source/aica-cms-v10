# 3C-3 맛집 주소 설계와 영향 범위

2026-09-27. DB 변경 전에 사용자에게 설명한 설계. 원본 V3는 유지하고 현재 FAQ V6 사본을 다시 복제해 검증한다.

## 저장과 키

기존 `posts.id` / `post_publications.post_id`를 사용한다. 발행본은 여러 이력이 아니라 현재 발행 snapshot 1개이며, 기존 PostService가 삭제/재삽입한다.

- `post_restaurant_details(post_id BIGINT PRIMARY KEY REFERENCES posts(id) ON DELETE CASCADE, address VARCHAR(500) NOT NULL DEFAULT '')`
- `post_publication_restaurant_details(post_id BIGINT PRIMARY KEY REFERENCES post_publications(post_id) ON DELETE CASCADE, address VARCHAR(500) NOT NULL)`

V7에는 위 빈 테이블 두 개만 생성한다. 기존 행/ID/category/분류/메뉴/페이지/발행본을 변경하거나 임의 맛집 데이터를 등록하지 않는다. 유형은 기존 RESTAURANT을 사용하고 전용 주제는 만들지 않는다. 테이블에 별도 독립 ID/타입별 주소 복사본을 만들지 않는다.

## 주소 정책 및 기존 저장과의 연결

- 주소는 선택 입력, 최대 500자, 앞뒤 공백 제거. 별도의 주소 검색·좌표/지도 검증·우편번호 정책은 도입하지 않는다.
- 제목/소개/미디어/분류/주소를 하나의 PostService 트랜잭션에서 저장한다. 주소만 바뀌어도 기존 posts revision과 충돌 검사를 사용한다.
- 발행 시 현재 초안 주소를 별도 발행 테이블에 복사한다. 초안 수정이나 유형 변경만으로 이전 공개 snapshot은 바뀌지 않는다.
- 기존 API에 `restaurant: {address: "..."}`를 추가한다. 필드를 생략하면 보존, 빈 문자열은 명시적 비우기다. 명시적 null/형식 오류는 거절한다. 주소가 없는 기존 RESTAURANT은 조회에서 빈 주소로 취급하며 자동 일괄 초기화하지 않는다.
- RESTAURANT이 아닌 유형에 비어 있지 않은 주소를 저장할 수 없다. 다른 유형으로 전환하면서 기존 주소가 남았으면 조용히 지우지 않고 명시적 비우기를 요청한다. 비운 뒤 저장하면 초안 확장 행을 제거하며 발행본은 재발행까지 보존한다.
- 일반 콘텐츠의 응답은 restaurant=null이며 입력 UI/미리보기에는 주소를 표시하지 않는다. 타입 조건은 Spring 서비스와 조회 조건으로 강제하고 PK/FK는 DB에서 보장한다.
- 기존 Thymeleaf 요청의 주소 누락은 보존으로 처리한다. 주소 요약과 React 편집 링크를 표시하고 저장/발행은 같은 서비스를 호출한다. 발행본 미리보기는 발행 주소 snapshot을 표시한다.

## UI와 탐색

`인사교 Real Life → 인사교 꿀팁 → 근처 맛집`은 기존 목록에 RESTAURANT 필터를 적용한다. 방문자 메뉴·페이지·주제를 생성하지 않는다. 개별 식당은 오른쪽 목록에만 표시한다.

같은 ContentEditor에서 식당명(title), 주소(전용), 소개(content/richContent), 기존 이미지/첨부를 편집한다. 기존 생성 API와 초안 생성 대화상자를 재사용하며 별도 맛집 편집기를 만들지 않는다. 주소를 dirty fingerprint 및 preview payload에도 포함한다.

## 예상 변경 파일

1. 신규 V7 SQL 및 migration 검증 테스트. V4~V6 불변. 기존 V3→V6 검사는 V6 target을 명시하고 별도 V6→V7 검사와 전체 앱 회귀를 추가한다.
2. 주소용 Mapper/XML와 작은 서비스: 조회, 검증, 초안 저장, 발행 snapshot. PostService 트랜잭션 내부에서만 저장한다.
3. PostService, NextPostApi: 선택적 주소 요청·조회·미리보기·발행본 응답. 기존 URL/분류/권한/CSRF 유지.
4. PostController 및 기존 폼/미리보기: 주소 읽기 전용 표시와 기존 저장 호환.
5. React types/api/contentDocument/ContentEditor/contentPresentation 및 공통 탐색 설정. 코드·미디어·분류를 재사용한다.
6. 새 migration/통합/프런트 테스트, 복사 DB 실행 스크립트, 결과·복구 기록.

## 적용 순서

소스/DB/JAR 기준점 → 별도 사본에서 V6 이력 확인 → V7 단독 적용/재실행/재접속/기존 모든 컬럼 비교 → 앱 코드 구현 → 실제 앱 classpath migration 검사 및 전체 회귀 → 별도 맛집 사본 8085 브라우저 검증 → DB/JAR 함께 백업.

원본 적용·지도·좌표·영업시간·전화·가격/메뉴·별점·맛집 주제·승인 정책·블록/템플릿은 이번 범위에 없다. H2/Flyway 위험은 DB_MIGRATION_RISKS.md에서 별도로 유지한다.
