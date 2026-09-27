# 10. 운영 데이터·IA 가이드

대상: 고객·운영 책임자·인수 개발자. 5D-1에서 작성한 적용안은 마감됐지만 **운영 사전 등록과 전체 IA 생성은 승인·실행되지 않았다.** 이 문서는 실제 원본과 후보를 분리한다.

## 현재 원본 V10

2026-09-28 문서 작성 시작 시 기존 조회 API로 재확인했다.

| 대상 | 실제 상태 |
|---|---|
| page 1 | 홈 / slug home / PUBLISHED / revision 1 |
| page 65 | 인사교 소개 / slug about / PUBLISHED / revision 5 |
| 페이지 블록 | 총 3개, 아래 ID 참조 |
| 방문자 메뉴 | menu 1→PAGE 1, menu 2→PAGE 65; 현재 평면 목록 |
| 콘텐츠 | ID 1~8 및 33, 총 9개, 모두 DRAFT/GENERAL/revision 0/기수·주제 없음 |
| 기존 categories | 1 공지사항, 2 교육 소식, 33 카테고리1. post 33만 category_id=2 |
| 신규 사전 | 유형 5종 활성; cohorts/topics/content_type_topics 모두 0건 |
| 미디어 / 공용 템플릿 | 0건 / 0건 |
| version 기준점 | 콘텐츠 초안 9, 페이지 초안 2·발행본 2; 이후 page 65 저장/발행 이력 추가 |

| page | 실제 block ID | 내용 |
|---|---|---|
| 1 | block_113f930f-0a78-41db-8f74-3b5fc5a5d484 | HERO, 기존 대표 문구 |
| 1 | block_e96cd216-6d06-46d6-805f-97d9cc927832 | POSTS, 사관학교 소식, category 미지정 |
| 65 | block_44c17a08-32c1-4bdd-a42c-a4266a9ad033 | HERO, 테스트 문구 |

실제 딥링크 예시: [페이지 65의 현재 HERO](http://127.0.0.1:8095/admin-next/pages/65/edit?view=structure&block=block_44c17a08-32c1-4bdd-a42c-a4266a9ad033). 이 링크는 관리자용이며 방문자 홈페이지 주소가 아니다. 테스트 블록을 ‘무엇을 배우는지’에 추측 연결하지 않는다.

## 콘텐츠 유형과 후보 IA

| 이미 등록된 유형 | IA 후보 | 관리 원칙 |
|---|---|---|
| GENERAL | 전용 유형에 해당하지 않는 일반 게시물 | 고정 페이지의 모든 문구를 글로 만들 필요 없음 |
| REVIEW | 선배들의 SSUL → 후기 | 기존 posts, 공통 편집기, 선택적 기수/주제 |
| FAQ | 지원 전 Check!! → FAQ | 질문 1개=post 1개, 질문별 topic/page 생성 없음 |
| RESTAURANT | 인사교 Real Life → 인사교 꿀팁 → 근처 식당/맛집 | 주소 확장만 사용; 전용 topic 없음 |
| INTERVIEW | 선배들의 SSUL → 인터뷰 | 공통 필드. 글/영상 구분 필드·고정 트리 진입은 미구현 |

## 미적용 사전 후보

기수는 COHORT_06/6기, COHORT_07/7기 두 개이며 sort_order=6/7, active=true 후보다. 모든 현재 유형에서 선택 없음/복수 선택 가능하다. 유형별 기수 허용 관계 테이블은 없으며 FAQ/맛집에 기수를 강제하지 않는다.

| 허용 유형 | code | 표시 이름 | sort_order 후보 |
|---|---|---|---:|
| REVIEW | REVIEW_LIFE | 생활 | 0 |
| REVIEW | REVIEW_CLASS | 수업 | 1 |
| REVIEW | REVIEW_PROJECT | 프로젝트 | 2 |
| FAQ | FAQ_PREPARATION | 준비사항 | 10 |
| FAQ | FAQ_APPLICATION | 지원·선발 | 11 |
| FAQ | FAQ_CLASS | 수업 | 12 |
| FAQ | FAQ_LIFE | 생활 | 13 |
| FAQ | FAQ_EMPLOYMENT | 취업 | 14 |
| FAQ | FAQ_ALLOWANCE | 지원금 | 15 |
| FAQ | FAQ_PROJECT | 프로젝트 | 16 |

모든 주제 active=true 후보이며 해당 유형 하나에만 허용한다. 생활·수업·프로젝트의 REVIEW/FAQ code는 각각 별도 topic이다. 표시 이름으로 병합하지 않는다. 지원금·특강·멘토링·기업탐방 등 후기 세부 내용은 별도 필터 요구 전까지 새 topic으로 만들지 않는다.

향후 확정된 파일 적용은 현재 빈 사전 기준 cohorts 2 + topics 10 + content_type_topics 10, 총 22행이다. 실제 숫자 ID는 대상 환경이 발급하고 code로 다시 조회한다. 같은 승인 입력은 no-op, 기존 code의 이름/정렬/활성/허용 관계가 다르면 자동 덮어쓰기 없이 STOP하는 절차를 제안했다. 아직 실행 가능한 importer를 구현하거나 사본에 등록 테스트한 것은 아니다.

## 전체 IA 연결은 어디까지 가능한가

전체 55행의 [매핑표](../5D-1/operating-data/IA_MAPPING.md)와 [판단 근거](../5D-1/OPERATING_DATA_PLAN.md)를 기준으로 한다.

- 소개 영역: page 65 재사용 후보. 설명은 TEXT/IMAGE 등 블록과 그 안의 소제목으로 구성하는 최소안이다. 실제 하위 블록은 아직 없다.
- 후기/FAQ: 현재 코드에 전체 목록과 주제 경로가 있다. 사전/허용 연결 등록 후 실제 topic ID로 필터링할 수 있다. 새 저장소·질문별 페이지가 필요하지 않다.
- 맛집: 기존 RESTAURANT 목록 경로를 재사용한다. ‘근처 식당/근처 맛집’ 최종 명칭은 결정 대상이다.
- 기수별 스토리: 관리자 필터는 가능하나 포함할 유형 범위와 공개 목록 구성은 미정이다. POSTS query는 유형 1개만 지원한다.
- 인터뷰: 기존 INTERVIEW 유형 목록은 가능하나 트리 연결과 글/동영상 분리 조건은 없다.
- 생활/지원 안내: 실제 page/slug가 아직 없고 생성 여부도 확정하지 않았다. 모든 IA 항목을 개별 page 또는 topic으로 만들지 않는다.
- 상위 4개 메뉴: 현재 site_menus에 parent_id/block_id/신규 분류 필터 필드가 없다. 별도 IA 데이터만 입력하면 목표 전체 계층이 완성되는 구조가 아니다.

현재 관리자 구조 트리는 REVIEW/FAQ/RESTAURANT 코드 경로와 실제 메뉴/페이지/블록을 조합한다. `ia-mapping.proposed.json`을 읽는 기능은 없다. TYPE/CATEGORY/PAGE를 혼동해 새 topic으로 홈페이지 위치를 대신하지 않는다.

## 고객·운영자 결정 목록

| 항목 | 결정 전 처리 |
|---|---|
| ‘인사교 알아보기’ 최종 이름 | 기존 page 65/메뉴 이름 유지 |
| 초기 간단 소개/인프라/후기/기수별 이미지 묶음 위치 | 자동 흡수·복사·페이지 생성 안 함 |
| 3~5기 | 단일 집계/실제 3·4·5기 별도 여부 미정, 등록 안 함 |
| 2027 인사교와 기수 관계 | 연도 이름을 cohort로 변환 안 함 |
| 주거/문화·여가 | 설명 block으로 충분한지, 개별 콘텐츠가 필요한지 확인 |
| 관련 기업 | 실제 필드/로고/관계 정보 시안 확인 전 새 모델 없음 |
| 인터뷰 상세 구조 | 영상 URL/썸네일/인터뷰이 요구 확인 전 전용 필드 없음 |
| 실제 홈페이지 라우트·상위 메뉴·계층 | 현재 데이터 추가와 별도 기능 범위를 구분하여 승인 |

## 기존 콘텐츠·페이지의 운영 준비

9개 글을 제목이나 category로 자동 분류하지 않는다. ID 1~8은 본문에 디자인 검토용 예시라는 안내가 있고 ID 33은 제목/본문만으로 의미를 확인할 수 없다. 모두 GENERAL/빈 분류를 유지하고 실제 원문을 운영자가 확정한 뒤 같은 ContentEditor에서 변경한다. [9건별 표](../5D-1/OPERATING_DATA_PLAN.md)에 근거가 있다.

page 65는 아직 테스트 문구와 관리자용 버튼 링크를 포함한다. 홈의 category 미지정 POSTS는 모든 발행 글을 가져오므로 FAQ 등을 발행하기 전에 홈의 콘텐츠 범위를 정한다. 이번 문서 작성에서 내용을 수정하지 않았다.

## 적용 승인 후에도 분리할 작업

사전 등록 승인 → 최신 V10 백업/사본 검증 → code 기준 중복·충돌 검사 → 별도 운영 DML → cold/재시작 검증 순서를 따른다. V1~V10은 수정하지 않는다. 사전 등록은 기존 글 분류·페이지/블록 생성·메뉴 연결·실제 홈페이지 구축 승인을 포함하지 않는다.

5D-1 JSON은 운영 후보이고 workbench/seed/smoke 자료는 검증 fixture다. 검증 DB의 숫자 ID나 샘플 글을 운영 원본으로 복사하지 않는다. [사전 파일](../5D-1/operating-data/operating-dictionary.proposed.json)과 [IA 매핑 파일](../5D-1/operating-data/ia-mapping.proposed.json)은 검토용으로 유지한다.
