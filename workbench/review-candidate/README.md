# 3C-1 후기 운영 후보 사전

`vocabulary.sql`은 사용자 지정 code를 사용하는 운영 후보 데이터이며, 현재 적용 허용 범위는 **3C-1 복사 DB뿐**이다. 원본 등록은 승인되지 않았다.

- 기수: COHORT_06 / 6기, COHORT_07 / 7기
- REVIEW 전용 주제: REVIEW_LIFE / 생활, REVIEW_CLASS / 수업, REVIEW_PROJECT / 프로젝트
- 저장 ID는 사본에서 발급받는다. UI는 code와 content_type_topics 허용 관계로 실제 ID를 조회한다.
- 홈페이지 위치인 선배들의 SSUL, 후기는 사전 항목이 아니다.
- 3B-2B의 B2B_* fixture 및 그 DB와 분리한다. 기존 데이터의 code나 category 이름을 추정 변환하지 않는다.
- schema migration, 앱 부팅, 전체 IA 등록에 연결하지 않는다. 기존 사전의 이름·활성 상태·허용 관계를 덮어쓰거나 삭제하지 않는다.
- SQL에는 후기 샘플 콘텐츠가 없다. `[검증]` 제목의 동선 검사용 글은 별도로 API/공통 편집기를 통해 생성한다.

등록 절차: 복구 가능한 기준점을 보존하고, 기수·주제가 비어 있는 검증된 V6 DB에서 새 `aica-phase3c1*.mv.db` 사본을 만든다. 그 서버를 종료한 상태에서 `scripts/seed-review-copy.ps1 -Database <사본경로>`를 실행한다. 사전 2개/3개, REVIEW 허용 관계 3개, 기존 콘텐츠·메뉴·페이지 불변을 확인한 뒤 시작한다.

이미 다른 분류가 있는 DB에 이 파일을 일괄 적용해 교정하지 않는다. 기본 실행 경로는 `.cache/react-phase3b2a-data/aica-phase3c1.mv.db`이며, 스크립트는 원본과 3B fixture 파일을 허용하지 않는다. 같은 사본에서 재실행해도 기존 code를 중복 생성하지 않는다. 운영 사전 변경 시에도 V4~V6 파일을 수정하지 않는다.
