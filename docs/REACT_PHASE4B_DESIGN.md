# 4B 블록 편집 UI 설계

2026-09-27. 기존 4A 데이터 기반 위에서 UI만 확장한다. 원본 V3는 유지하며, 4A 최신 V8 검증 DB를 정상 종료 후 새 4B 계열로 복제한다. V1~V8 migration 파일과 DB 스키마는 변경하지 않는다.

## 화면과 작업 단위

- 페이지 제목과 고정 저장 영역 아래에 블록 목록, 선택한 블록의 속성 편집, 실시간 미리보기를 배치한다.
- 넓은 화면은 3열, 중간 폭에서는 왼쪽에 목록/편집·오른쪽에 미리보기, 작은 화면은 한 열로 배치한다.
- 목록은 현재 배열 순서, type, 제목, Variation, 표시/숨김을 보여준다. 선택 상태는 배열 위치가 아닌 ID로 유지한다.
- 추가는 등록 컴포넌트를 고르는 대화상자, 삭제는 확인 대화상자를 사용한다. 복제본은 원본 바로 다음에 삽입하고 선택한다. 삭제 후에는 인접 블록을 선택한다.
- 기존 위/아래 이동 방식을 유지한다. 드래그는 추가하지 않는다.
- 페이지 전체 초안 저장/기존 자동저장/미리보기 API를 유지한다. 발행은 저장 완료 후 기존 Thymeleaf 발행 화면으로 연결한다. 정책 변경이나 블록별 저장 API는 없다.

## 등록 컴포넌트 정의

`PageComponentRegistry`가 개발자 소유의 정의 목록을 제공한다. 정의에는 type, 표시명, 입력 필드, 기본값, schemaVersion, 기본 Variation, 허용 Variation이 포함된다.

| 타입 | 입력 필드 | Variation |
|---|---|---|
| HERO | 제목, 본문, 버튼 이름/주소 | default, centered |
| TEXT | 제목, 본문 | default |
| IMAGE | 제목, 본문, 기존 이미지 선택/업로드 | default |
| POSTS | 제목, 기존 category 선택 | default |
| CTA | 제목, 본문, 버튼 이름/주소 | default |

공통 컴포넌트 화면과 블록 추가/속성 편집은 같은 읽기 전용 `GET /api/admin/next/page-components`를 사용한다. 서버 저장·미리보기는 같은 정의로 type/version/Variation을 검증한다. 기존 로고/상단/하단 문구 설정은 보존한다. 활성/비활성 정책이나 임의 코드 등록 기능은 추가하지 않는다.

HERO `centered`는 기존 입력값만 사용한다. 제목·본문·버튼을 가운데 배치하고 본문 폭과 패딩, 상단 강조선을 바꾼다. React 미리보기와 기존 관리자 미리보기는 동일한 `static/css/page-blocks.css` 규칙을 사용한다. 별도 이미지/지도/새 데이터 필드 없이 저장→재조회→미리보기의 실체를 검증한다. 공개 홈페이지 전체 구현은 제외한다.

## 식별자와 snapshot

- 내용·Variation·표시·순서 변경은 ID를 유지한다. 신규/복제는 UUID v4를 발급한다.
- 복제는 모든 입력, schemaVersion, Variation, visible을 복사한다. bodyDoc은 JSON 문자열이므로 공유되는 가변 객체가 없다.
- 삭제는 초안에서 제거하고 기존 PageBlockService의 폐기 기록을 재사용한다. 기존 발행본 JSON은 재발행 전까지 변경하지 않는다.
- React 선택과 비동기 업로드는 ID로 대상을 찾는다. 보기 전환은 같은 PageEditor 인스턴스를 유지한다.
- 기존 Thymeleaf는 ID/version/Variation을 보존하며 새 Variation의 편집은 React 진입 링크로 안내한다.

4C용 콘텐츠 조건·직접 선택 블록, 블록 직접 이동, 공용 템플릿, 새 타입의 대량 추가는 범위에 포함하지 않는다. POSTS의 기존 category 연결만 유지한다. 인터뷰 구조화 요구는 실제 시안에서 확인할 때 검토한다.

## 복구와 검증

작업 전 `.cache/checkpoints/20260927-145301-react-phase4b/`에 최신 V8 DB와 4A JAR, 수정/미추적 소스를 보존했다. 검증 DB는 `.cache/react-phase4b-data/aica-phase4b.mv.db`, 포트8087이다. 같은 V8이라도 이전 4A 앱은 centered Variation을 지원하지 않으므로 복구는 해당 시점의 DB/JAR 쌍으로 한다.

기존 페이지 1·65, 후기/FAQ/맛집을 보존하고 전체 회귀와 사본 migration 검사를 실행한다. UI 검증 후 기존 페이지의 시험 문구·순서·Variation은 복원하며, 시험으로 삭제된 신규 블록 ID는 폐기 기록으로 남긴다. 결과는 `REACT_PHASE4B_BLOCK_EDITOR.md`에 기록한다.
