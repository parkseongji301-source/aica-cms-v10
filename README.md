# AICA CMS (V12 안정 기준점 · V11 보존)

콘텐츠·페이지·블록을 관리하는 Spring/React 백오피스와 발행본 전용 공개 API입니다. 실제 공개 홈페이지는 아직 없습니다.

- **현재 기준: V12** — 2026-09-30 [V12 안정 기준점](docs/V12_STABLE_BASELINE.md). V11 기능에 [글쓰기 템플릿](docs/WRITING_TEMPLATES.md)(schema V12)과 2026-09-30 UI 개선을 더했습니다. 이후 Thymeleaf → React 전환 작업은 이 기준점과 비교합니다.
- **보존 기준: V11** — 2026-09-29 [V11 운영 기준점](docs/V11_OPERATING_BASELINE.md)(태그 `v11-operating-baseline-20260929`). [React 게시·재게시](docs/POST_PUBLICATION.md)와 [휴지통](docs/POST_TRASH.md)을 포함합니다. V11 DB·실행본·rollback 자료는 수정하지 않고 보관합니다.

V12 서버는 schema V12 DB와 MIGRATED_V12 receipt만 받습니다. V11 DB를 V12 JAR로 열거나, V12 DB를 V11 JAR로 열 수 없습니다. 둘 다 정상 종료된 DB의 별도 사본에서만 전환합니다. V12에 새로 작성한 내용이 생긴 뒤 V11로 되돌리면 그 내용은 보이지 않습니다.

업무 기능을 바꾸는 작업은 [UI·UX 변경 범위](docs/UI_UX_FREEZE.md)에 범위를 먼저 기록하고 사용자 승인을 받습니다.

## 문서 시작점

**[5D 운영·사용·인수인계 문서 모음](docs/5D/README.md)**에서 담당 역할에 맞는 가이드를 선택하세요. 각 가이드 첫 부분에 V11·V12 변경 요약이 있습니다.

- 처음 사용하는 운영자: [사용자 가이드](docs/5D/5D-2/01_USER_GUIDE.md), [역할·권한](docs/5D/5D-2/02_ROLES_AND_PERMISSIONS.md)
- 운영 담당자: [CMS 운영](docs/5D/5D-2/03_CMS_OPERATIONS.md), [운영 데이터·IA](docs/5D/5D-2/10_OPERATING_DATA_AND_IA.md)
- 인수 개발자: [개발자 인수인계](docs/5D/5D-2/04_DEVELOPER_HANDOVER.md), [배포·실행](docs/5D/5D-2/07_DEPLOYMENT_AND_RUNTIME.md), [백업·복구](docs/5D/5D-2/08_BACKUP_AND_RECOVERY.md), [migration](docs/5D/5D-2/09_DATABASE_MIGRATIONS.md)
- 납품 범위/최종 검수: [제한사항과 A/B 범위 결정](docs/5D/5D-2/11_LIMITATIONS_AND_ACCEPTANCE.md)

## 이 PC의 실행

이 PC는 2026-09-29 V11 이관 묶음(`트랜스퍼 0930/`, Git 제외)을 풀어 사용합니다. 루트의 `START.cmd`/`STOP.cmd`는 Git 제외 파일 `.cache/current-ui.json`이 가리키는 실행본을 시작·정상 종료합니다. 현재 실행본과 전환·되돌리기 절차는 [V12 안정 기준점](docs/V12_STABLE_BASELINE.md)의 "이 PC의 실행 상태"를 따릅니다. 다른 PC·경로로 옮길 때는 [V12 실행본 옮기기](docs/V12_RELOCATION.md)를, 스키마 변경 없이 JAR만 바꿀 때(RC1 → RC2)는 [V12 JAR 교체](docs/V12_JAR_SWAP.md)를 따릅니다.

8095는 2026-09-30부터 **V12 RC2**(React 관리자 `/admin`)로 실행합니다. RC1과 같은 V12 DB를 쓰며 RC1로 되돌릴 수 있습니다([V12 JAR 교체](docs/V12_JAR_SWAP.md)).

접속: [관리자](http://127.0.0.1:8095/admin) · [콘텐츠 작업](http://127.0.0.1:8095/admin/posts?view=structure) · [사이트 관리](http://127.0.0.1:8095/admin/dashboard?view=manage). 로그인 자격증명은 별도로 인계합니다.

- `/admin-next/...`: 과도기 호환 redirect입니다. 같은 화면의 `/admin/...`으로 302 이동합니다.
- [`/admin/legacy`](http://127.0.0.1:8095/admin/legacy): 기존 Thymeleaf 화면으로, **비교·복구용**입니다. 일반 업무는 `/admin`에서 합니다. 제거 조건은 [5단계 결과](docs/REACT_ADMIN_STEP5.md)에 있습니다.

파일 DB writer의 **AUTO_COMPACT_FILL_RATE=0은 필수**입니다. 과거 V3/V10/V11 시작 경로를 V12 DB에 사용하지 마세요. 강제 종료나 열린 DB의 파일 복사를 하지 않습니다.

`.cache/company-v11/`로 시작하는 경로는 V11을 처음 승격한 이전 PC의 경로입니다. 이 PC에는 없습니다.

## 이전 단계 기록

[V11 승격 절차](docs/V11_PROMOTION.md), [V11 사본 리허설](docs/V11_REHEARSAL_RESULTS.md), [5C-2 최종 전환 결과](docs/PHASE5C2_RETRY_RESULTS.md), [공개 API 상세 계약](docs/PUBLIC_API_V1.md), [Stage 0 검증](docs/STAGE0_VERIFICATION.md), [초기 설계 자료](docs/reference/README.md)는 보존합니다. 이전 단계 문서의 V3/V10 경로·사본 포트·옛 권한·초기 실행 절차는 당시 기록이며 현재 운영 기준이 아닙니다.
