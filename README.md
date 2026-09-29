# AICA CMS (운영 V11)

콘텐츠·페이지·블록을 관리하는 Spring/React 백오피스와 발행본 전용 공개 API입니다. 2026-09-29 실제 8095의 V11 승격을 완료했습니다. 실제 공개 홈페이지는 아직 없습니다. [V11 운영 기준점](docs/V11_OPERATING_BASELINE.md)을 이후 UX 개선의 비교 기준으로 사용합니다.

2026-09-29 개발본에는 사용자 요청에 따라 [React 게시·재게시 연결](docs/POST_PUBLICATION.md)과 [게시물 휴지통·복원·영구삭제](docs/POST_TRASH.md)를 추가했습니다. 휴지통은 V11 추가 테이블을 사용합니다. 기존 8095 운영본과 분리해 검증했으며, 새 UI·서버·검증된 V11 DB를 함께 적용해야 합니다. UI 파일만 교체해서 적용할 수 없습니다.

V11 RC1의 [승격 절차](docs/V11_PROMOTION.md)와 [사본 리허설 결과](docs/V11_REHEARSAL_RESULTS.md)에 따라 최신 전체 백업·새 승인 계획·MIGRATED_V11 receipt를 발급하고 실제 승격했습니다. 기존 데이터 보존과 실제 정상 종료·cold 검사·재시작은 PASS했으며 V10 전체 rollback 묶음을 보존했습니다.

이후 디자인·동선 변경은 [UI·UX 기능 동결 기준](docs/UI_UX_FREEZE.md)을 따릅니다. 업무 기능 범위를 넘어야 하면 진행 전에 사용자에게 알립니다.

## 문서 시작점

**[5D 운영·사용·인수인계 문서 모음](docs/5D/README.md)**에서 담당 역할에 맞는 가이드를 선택하세요. 5D-1 운영 데이터/IA 후보와 5D-2의 11개 가이드를 한 폴더로 모았습니다.

- 처음 사용하는 운영자: [사용자 가이드](docs/5D/5D-2/01_USER_GUIDE.md), [역할·권한](docs/5D/5D-2/02_ROLES_AND_PERMISSIONS.md)
- 운영 담당자: [CMS 운영](docs/5D/5D-2/03_CMS_OPERATIONS.md), [운영 데이터·IA](docs/5D/5D-2/10_OPERATING_DATA_AND_IA.md)
- 인수 개발자: [개발자 인수인계](docs/5D/5D-2/04_DEVELOPER_HANDOVER.md), [배포·실행](docs/5D/5D-2/07_DEPLOYMENT_AND_RUNTIME.md), [백업·복구](docs/5D/5D-2/08_BACKUP_AND_RECOVERY.md), [migration](docs/5D/5D-2/09_DATABASE_MIGRATIONS.md)
- 납품 범위/최종 검수: [제한사항과 A/B 범위 결정](docs/5D/5D-2/11_LIMITATIONS_AND_ACCEPTANCE.md)

현재 이 PC 접속: [콘텐츠 작업](http://127.0.0.1:8095/admin-next/posts?view=structure) · [사이트 관리](http://127.0.0.1:8095/admin-next/dashboard?view=manage). 로그인 자격증명은 별도로 인계합니다. 집 PC의 V11 이관본에는 기능 동결 상태의 [콘텐츠 작업 홈 UI](docs/CONTENT_WORK_HOME.md)를 적용했습니다. 이 PC의 현재 실행·종료 경로는 해당 문서를 따릅니다.

## 실행 전에

현재 원본 V11은 `.cache/company-v11/start-ui.ps1`에서 승인된 `serve`와 validate-only로 실행합니다. 파일 DB writer의 **AUTO_COMPACT_FILL_RATE=0은 필수**입니다. 과거 V3/V10 시작 경로를 현재 V11 DB에 사용하지 마세요. 정확한 실행·종료·복구 경로는 [V11 운영 기준점](docs/V11_OPERATING_BASELINE.md)을 따릅니다.

## 이전 단계 기록

[5C-2 최종 전환 결과](docs/PHASE5C2_RETRY_RESULTS.md), [공개 API 상세 계약](docs/PUBLIC_API_V1.md), [Stage 0 검증](docs/STAGE0_VERIFICATION.md), [초기 설계 자료](docs/reference/README.md)는 보존합니다. 이전 단계 문서의 V3/사본 포트·옛 권한·초기 실행 절차는 당시 기록이며 현재 운영 기준이 아닙니다.
