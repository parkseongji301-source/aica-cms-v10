# AICA CMS V10

콘텐츠·페이지·블록을 관리하는 Spring/React 백오피스와 발행본 전용 공개 API입니다. 원본 V10 전환은 완료됐으며 실제 공개 홈페이지는 아직 없습니다. 현재 기능/schema는 동결 상태입니다.

이후 디자인·동선 변경은 [UI·UX 기능 동결 기준](docs/UI_UX_FREEZE.md)을 따릅니다. 업무 기능 범위를 넘어야 하면 진행 전에 사용자에게 알립니다.

## 문서 시작점

**[5D 운영·사용·인수인계 문서 모음](docs/5D/README.md)**에서 담당 역할에 맞는 가이드를 선택하세요. 5D-1 운영 데이터/IA 후보와 5D-2의 11개 가이드를 한 폴더로 모았습니다.

- 처음 사용하는 운영자: [사용자 가이드](docs/5D/5D-2/01_USER_GUIDE.md), [역할·권한](docs/5D/5D-2/02_ROLES_AND_PERMISSIONS.md)
- 운영 담당자: [CMS 운영](docs/5D/5D-2/03_CMS_OPERATIONS.md), [운영 데이터·IA](docs/5D/5D-2/10_OPERATING_DATA_AND_IA.md)
- 인수 개발자: [개발자 인수인계](docs/5D/5D-2/04_DEVELOPER_HANDOVER.md), [배포·실행](docs/5D/5D-2/07_DEPLOYMENT_AND_RUNTIME.md), [백업·복구](docs/5D/5D-2/08_BACKUP_AND_RECOVERY.md), [migration](docs/5D/5D-2/09_DATABASE_MIGRATIONS.md)
- 납품 범위/최종 검수: [제한사항과 A/B 범위 결정](docs/5D/5D-2/11_LIMITATIONS_AND_ACCEPTANCE.md)

현재 이 PC 접속: [React 관리자](http://127.0.0.1:8095/admin-next?view=manage). 로그인 자격증명은 별도로 인계합니다.

## 실행 전에

현재 원본 V10에는 승인된 `serve` 경로와 validate-only를 사용합니다. 파일 DB writer의 **AUTO_COMPACT_FILL_RATE=0은 필수**입니다. 과거 `run-local.ps1`은 V3 실행 경로이므로 원본 V10에 사용하지 마세요. 정확한 명령·경로·종료 절차는 배포·실행 가이드에 있습니다.

## 이전 단계 기록

[5C-2 최종 전환 결과](docs/PHASE5C2_RETRY_RESULTS.md), [공개 API 상세 계약](docs/PUBLIC_API_V1.md), [Stage 0 검증](docs/STAGE0_VERIFICATION.md), [초기 설계 자료](docs/reference/README.md)는 보존합니다. 이전 단계 문서의 V3/사본 포트·옛 권한·초기 실행 절차는 당시 기록이며 현재 운영 기준이 아닙니다.
