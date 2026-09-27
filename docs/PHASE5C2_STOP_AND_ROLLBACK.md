# 5C-2 실행 결과: STOP 후 V3 복구

2026-09-27. **5C-2 전체 성공으로 판정하지 않는다. 현재 원본은 V3이며 서버는 정지 상태다.**

승인된 고정 `cutover.py run` 자체는 원본에서 성공했다. 이후 내가 덧붙인 baseline 대상 ID 확인용 외부 SQL 검사 명령에서 PowerShell에 Java 옵션을 따옴표 없이 전달했다. Java가 `.encoding=UTF-8`을 main class로 해석하여 `ClassNotFoundException`으로 종료했다. DB에 연결하기 전 실행 인수 오류다. 사용자의 “예상 밖 오류 발생 시 STOP, 현장 수정 없이 rollback” 지시에 따라 해당 명령을 수정·재실행하거나 전환을 계속하지 않았다.

이 오류를 H2 지속성 문제나 migration 실패로 분류하지 않는다. 마지막 V10 cold 검사 해시와 rollback 때 보존한 V10 해시가 동일하므로 실패한 검사 명령이 DB를 변경하지 않았음도 확인했다.

## 요청 항목별 결과

| 항목 | 실행 결과 |
|---|---|
| 1. 전환 직전 V3 기준점 | 23:17:58 +09:00에 원본 잠금 해제 후 새 백업. V3 DB, 대응 V3 JAR, 승인 V10 release 전체, 현재 소스 367개, Git 상태·HEAD·working-tree patch, 실행 설정·migration·복구 문서·체크섬 보관. 이전 백업을 전환 기준으로 재사용하지 않음. |
| 2. guard | 정확한 원본 절대 경로, V3 및 V1~V3 checksum, 새 백업과 원본 SHA/16개 테이블 fingerprint, 승인 RC/전체 migration manifest, 명시적 원본 옵션, 일회성 cutover flag, `AUTO_COMPACT_FILL_RATE=0`, 독점 접근 확인. 조건 완화 없음. |
| 3. V4→V10 | 최신 백업의 새 사본에서 전체 run을 먼저 통과한 뒤 원본 적용. 7개 migration 각각 1건 실행 및 validate 성공. 원본 receipt의 7개 단계 모두 `legacyDataPreserved=true`. V10 history가 승인 V1~V10 목록과 일치함. |
| 4. 데이터 비교 | migration 단계마다 기존 16개 테이블의 기존 컬럼을 비교. V8의 sections_json은 block ID/schemaVersion/variation 메타데이터를 제외한 내용·순서를 별도 비교하여 보존 확인. 콘텐츠 ID/category_id, 메뉴, 계정, 활동 이력, 미디어 등 기존 값 보존. |
| 5. baseline | 원본에서 콘텐츠 초안 9건, 콘텐츠 발행본 0건, 페이지 초안 2건 + 발행본 2건, 템플릿 0건. 두 번째 실행은 전부 0건. 페이지 대상은 1·65. 콘텐츠 baseline 대상 ID를 별도 SQL로 나열하는 보충 검사는 실행 인수 오류로 미완료. baseline 데이터는 보존한 V10 사본에 남아 있으며 현재 복구된 V3에는 없음. |
| 6. smoke | 원본: SUPER_ADMIN 로그인, 콘텐츠/페이지 관리자 진입, 사이트 관리/구조 경로, 페이지 1·65 조회, 페이지 65 동일 내용 명시 저장, version 증가 및 공개 API 불변. 원본의 별도 smoke 사본: 8개 CMS 검증 묶음과 SUPER_ADMIN/ADMIN/SUPPORTER 실제 요청 권한표 통과. |
| 7. 종료 후 검사 | 원본 V10 5회 모두 정상 종료·PID 종료·Hikari 종료·파일 독점 접근·별도 JVM readonly V10 검사 통과. 이후 보충 SQL 검사만 DB 연결 전에 실패함. |
| 8. 재시작 | 원본에서 4회 동일 내용 저장 후 총 5회 기동하여 페이지·최신 version·공개 snapshot 일치 확인. 본문 시험 문자열이나 fixture를 원본에 넣지 않음. |
| 9. cutover mode | 일회성 migration 자식에서만 사용했고 정상 기동 5회는 `cutoverFlag=false`, `migration=validate-only`, 옵션 0. plan은 `.spent`로 소모되어 재사용 불가. 현재 V10/V3 서버 모두 정지. |
| 10. rollback | 보충 검사 STOP 후 검증된 `rollback --authorize-original-rollback` 실행. V10 DB/RC 보존 → 백업 V3 검증 → 원본 V3 DB 및 대응 V3 JAR 복구 → V3 격리 기동·로그인·조회 → 정상 종료 → readonly fingerprint 비교 모두 통과. |
| 11. 원본 최종 상태 | **V3. 기존 16개 테이블 전체 fingerprint 및 V1~V3 history가 최신 전환 직전 백업과 동일.** 파일 잠금 해제 및 Java 프로세스/8095·8096 listener 없음 확인. 쓰기 재개하지 않음. |
| 12. 5D 전 남은 사항 | 5C-2 재개 여부 판단, 보충 검사 실행 방식 재검토, 새 기준점/새 plan으로 재전환 필요. 원본에서 동일 구성 재발행 후 API 발행 시각 변경 확인과 최종 `serve`는 STOP 이후 실행하지 않음. 실제 홈페이지 렌더링 E2E도 미완료. |

## 검증 범위의 구분

원본 run의 저장 지속성 검사는 page 65의 동일 내용 명시 저장 4회다. 내용·공개본을 바꾸지 않고 version 행 4개를 생성했다. 원본에 기수/주제를 등록하거나 시험 계정·콘텐츠·미디어·템플릿을 만들지 않았다.

후기/FAQ/맛집, query/manual/category, 발행 후 공개 변화, 역할별 실제 쓰기·차단, 템플릿, 버전 복구와 미디어 참조 보호의 전체 기능 시험은 고정 runbook대로 `original-run/smoke/fixture.mv.db`에서 수행했다. 이 사본을 원본으로 승격하지 않았다. 이 결과를 원본에서 해당 시험 데이터를 생성·발행했다고 표현하지 않는다.

원본 공개 API에서는 동일 내용 초안 저장 전후 불변을 확인했다. 이후 계획한 원본 동일 구성 재발행 검사는 STOP으로 실행하지 않았다. 브라우저 시각 검증 및 실제 홈페이지 E2E를 수행했다고 기록하지 않는다.

## 기준 경로와 체크섬

- 원본: `C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.local-data/aica-local.mv.db`
- 이번 증거 루트: `.cache/phase5c2/20260927-231757/`
- 최신 전환 직전 기준점: `pre-cutover/`
- 고정 RC: `.cache/phase5c1c/20260927-223140/release-final/`
- 최신 V3 백업 SHA-256: `55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`
- V10 RC SHA-256: `606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e`
- V3 runtime SHA-256: `c20f0f4d92e1867fbdf326c30b67949ceedf71aa3c0a2d597dc4432e1201cf6d`
- 소모된 원본 plan SHA-256: `e8678b214cd67ec8664362fab4a2657e608731a9f6190a4bb9cee21593d41ecc`
- 보존 V10 DB SHA-256: `0d3e595b9e6ff562c745c5c5f5cf8c86af0d5123604ec7cc1d757cfdfb24eee8`
- V3 복구·격리 기동·종료 후 최종 SHA-256: `3d8340a64a368a2ab2d7617518b8750c68917d94daa24f1007ad46e1bad99118`

V3 파일을 복원한 직후에는 백업과 바이트 해시가 같았다. V3 확인 기동·정상 종료 후에는 파일 바이트 해시가 달라졌지만 V1~V3 history와 모든 테이블 fingerprint는 동일하다. 이 차이를 숨기거나 최종 파일이 백업과 바이트 단위로 같다고 표현하지 않는다.

V10 보존물은 `original-run/v10-preserved/`, 대응 V3 복구 runtime은 `original-run/active-rollback/v3-runtime.jar`에 있다. V10 파일에 V3 JAR만 연결하는 방식은 사용하지 않았다. 별도 `.local-data/backoffice.mv.db` 해시는 작업 전후 `2a9ef304a1407a663f903e8924b1e7326c241033bd9e4c002a30b3f95c9b8a90`으로 동일하다.

## 확인할 증거

증거 루트 기준:

- `pre-cutover/checkpoint.json`, `backup-checksums.json`, `v3-inspection.json`
- `original-plan.json`, `original-plan-review.json`, `original-plan.json.spent`
- `last-copy-acceptance.json`, `copy-run/result.json`
- `original-run/runtime-receipt.json`, `original-run/evidence/target-migrated-cold.json`
- `original-run/evidence/baseline.json`, `cycle-1..5-api.json`, `cycle-1..5-process.json`, `cycle-1..5-closed.json`, `cycle-1..5-cold.json`
- `original-run/evidence/cms-smoke.json`, `roles-smoke.json`
- `stop-incident.json`, `stop-processes.json`
- `original-run/rollback-1790519060121752600/rollback-result.json`, `rollback-before.json`, `rollback-after.json`, `rollback-v3-closed.json`
- **`final-status.json`: 이번 작업 전체의 최종 판정**

`original-run/result.json`의 success는 STOP 전 고정 run 경로의 성공 기록이다. 보충 검사 실패 및 rollback으로 최종 상태가 바뀌었으므로 해당 파일만 보고 V10 운영 전환이 완료됐다고 판단하면 안 된다. 원본 기록을 덮어쓰지 않고 `final-status.json`과 이 보고서로 최종 상태를 구분했다.

애플리케이션·UI·dependency·migration·고정 cutover release는 변경하지 않았다. 기준점 소스 367개와 release manifest 15개 항목의 해시가 그대로임을 확인했다. H2/Flyway 지원 경고와 실제 홈페이지 미연결은 기존 잔여 위험으로 유지한다.
