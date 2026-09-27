# AICA CMS V10 · 운영·사용·인수인계 문서

기준: 2026-09-28, 원본 V10 전환 완료 후 기능/schema 동결 상태. 이 문서 세트는 실행이나 데이터 등록 승인이 아니다. 현재 공개 홈페이지는 없으며 CMS와 공개 API까지 준비됐다.

## 읽는 순서

- 콘텐츠 작성자: 사용자 가이드 → 역할·권한 → CMS 운영 가이드.
- 운영 관리자: 위 3개 → 운영 데이터/IA → 제한사항.
- 인수 개발자: 개발자 인수인계 → 실행 → 백업·복구 → migration → 확장 가이드.
- 납품 범위 결정자: 운영 데이터/IA → 제한사항의 **A/B 범위 결정**.

## 5D-2 문서 목록

| 문서 | 목적 | 대상 독자 |
|---|---|---|
| [01 CMS 사용자 가이드](5D-2/01_USER_GUIDE.md) | 실제 화면에서 작성·편집·발행·복구하는 방법 | 세 역할 모두 |
| [02 역할·권한 운영 가이드](5D-2/02_ROLES_AND_PERMISSIONS.md) | 역할별 허용 범위와 서버/UI의 제한 | 운영 책임자, 계정 관리자, 개발자 |
| [03 CMS 운영 가이드](5D-2/03_CMS_OPERATIONS.md) | 반복 작업의 순서·완료 기준·공개 반영 시점 | ADMIN, SUPER_ADMIN |
| [04 개발자 인수인계 가이드](5D-2/04_DEVELOPER_HANDOVER.md) | 소스·서비스·테이블·API와 인계할 실행물 | 인수 개발자, 운영 업체 |
| [05 컴포넌트 확장 가이드](5D-2/05_COMPONENT_EXTENSION.md) | 등록형 블록·Variation을 안전하게 확장하는 절차 | 프런트/백엔드 개발자 |
| [06 콘텐츠 유형 확장 가이드](5D-2/06_CONTENT_TYPE_EXTENSION.md) | 유형별 입력·검증·snapshot 확장 기준 | 프런트/백엔드 개발자 |
| [07 배포·실행 가이드](5D-2/07_DEPLOYMENT_AND_RUNTIME.md) | 승인 V10 실행·종료·로그 확인과 H2 필수 설정 | 실행 환경 담당자 |
| [08 백업·복구 가이드](5D-2/08_BACKUP_AND_RECOVERY.md) | DB/runtime 동시 백업과 장애 복구 | 운영 책임자, DB/서버 담당자 |
| [09 DB migration 가이드](5D-2/09_DATABASE_MIGRATIONS.md) | V1~V10 설명과 향후 변경 절차 | 백엔드/DB 담당자 |
| [10 운영 데이터·IA 가이드](5D-2/10_OPERATING_DATA_AND_IA.md) | 현재 원본·미적용 후보·미확정 IA 구분 | 고객, 운영자, 개발자 |
| [11 제한사항·후속 검증](5D-2/11_LIMITATIONS_AND_ACCEPTANCE.md) | 미완료 범위와 인수 전 결정 사항 | 납품/인수 책임자 |

## 5D-1 자료

- [운영 데이터 적용안과 기존 9개 콘텐츠 검토](5D-1/OPERATING_DATA_PLAN.md)
- [전체 IA 매핑표](5D-1/operating-data/IA_MAPPING.md)
- [운영 사전 후보 JSON](5D-1/operating-data/operating-dictionary.proposed.json)
- [IA 매핑 후보 JSON](5D-1/operating-data/ia-mapping.proposed.json)

후보 JSON은 검토 자료이며 V10이 읽는 설정이나 실행 가능한 importer가 아니다. 기수·주제는 아직 원본에 등록되지 않았다.

## 현재 접속과 문서 적용 범위

현재 이 PC의 관리자 주소는 [사이트 관리](http://127.0.0.1:8095/admin-next?view=manage) / [사이트 구조](http://127.0.0.1:8095/admin-next?view=structure)다. `127.0.0.1`은 이 서버 PC를 뜻하며 다른 운영자 PC의 접속 주소가 아니다. 원격 운영 주소·TLS·배포 호스트는 별도 확정 대상이다. 계정 정보는 문서에 포함하지 않는다.

현재 문서는 V10을 기준으로 한다. `docs`의 이전 단계 결과에는 당시 사본 포트·V3 기준·과거 권한이 남아 있으므로 운영 명령은 07~09를 우선한다. 과거 증거는 삭제하지 않았다. 5D-3 최종 검수는 아직 진행하지 않았다.

문서 작성 검증 범위와 동결 확인은 [작성 검증 기록](DOCUMENTATION_CHECK.md)을 참고한다.
