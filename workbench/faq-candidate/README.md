# FAQ 운영 후보 사전 — 복사 DB 검증 전용

`vocabulary.sql`은 승인된 FAQ 주제 7개와 허용 관계만 등록한다. 기존 후기 사전·기수·category·메뉴·페이지를 수정하지 않으며 FAQ의 기본 기수는 선택 없음이다. FAQ_LIFE와 REVIEW_LIFE는 서로 다른 code/ID다.

스키마 migration V4~V6 및 앱 부팅과 분리된 수동 등록이다. `scripts/seed-faq-copy.ps1`은 정지된 `.cache/react-phase3b2a-data/aica-phase3c2*.mv.db`만 허용한다. 기존 사전을 이름으로 변환하거나 지우지 않는다. 다른 FAQ 주제/허용 관계가 이미 있는 DB는 사용하지 말고 먼저 검토한다.

질문 4건의 검증 데이터는 이 SQL에 포함하지 않는다. 공통 API/편집기로 생성하고 답변에 검증용임을 표시한다. 아직 확정되지 않은 실제 지원 안내를 임의로 작성하지 않는다. 원본 V3에는 사전과 콘텐츠 모두 등록하지 않는다.
