# 8095 V11 운영 기준점

> 2026-09-30 이후 현재 기준은 [V12 안정 기준점](V12_STABLE_BASELINE.md)이다. 이 문서는 V11 기준점과 rollback 자료의 원래 기록으로 보존한다. 아래 `.cache/company-v11/` 경로와 PID는 V11을 승격한 이전 PC 기준이다.

2026-09-29 실제 승격 **PASS**. 14:47:53 KST 최종 확인 시 8095는 V11 RC1으로 실행 중이다. 사용자 승인에 따라 이 상태를 이후 UX 개선의 기준점으로 고정한다.

## Git 기준점

기준 태그는 `v11-operating-baseline-20260929`이며, V11 기능 코드·migration·테스트·승격 도구·운영 문서를 포함한 커밋을 가리키는 annotated tag로 남긴다. 태그 설명에는 운영 JAR SHA-256과 적용 범위·검증 결과를 기록한다.

기준점 생성 전 운영 RC의 소스 스냅샷과 현재 서버/프런트엔드·빌드 설정 221개 파일의 바이트 일치를 확인했다. 운영 적용 후 갱신한 문서와 운영 orchestration은 현재 상태로 함께 보존한다. 운영 DB·백업·JAR·계정 자료는 Git에 포함하지 않고 기존 Git 제외 경로에 보관한다. 로컬 커밋/태그이며 원격 전송은 별도 작업이다.

## 승인 범위

React 게시물 작성·수정·게시·재게시, 휴지통 이동·복원·영구삭제, V11 `post_trash` migration만 적용했다. UX/UI 개편, Thymeleaf 제거, `/admin-next` 경로 정리, 동적 IA, 대시보드, 방문자 통계, 기타 신규 기능은 추가하지 않았다. 기존 업무 규칙·권한·데이터를 이후 UX 변경에서도 보존한다.

## 실제 적용 결과

| 항목 | 결과 |
| --- | --- |
| 실제 8095 승격 | PASS, V11 정상 실행 |
| Migration | V11 한 건만 적용. V1~V10 checksum 동일 |
| JAR SHA-256 | `5be79fd5ce32e348e6ddf0f3b5acd5f32bebd0441802ac3356d64deca800906e` |
| 새 receipt | MIGRATED_V11 / V10_TO_V11 발급 |
| 기존 데이터 | 35개 기존 테이블 전체 컬럼·행·BLOB fingerprint 보존 |
| React 작성·수정·게시·재게시 | 동일 JAR + 승격 직전 최신 백업 사본에서 PASS |
| 휴지통·복원·재게시·영구삭제 | 동일 JAR + 최신 백업 사본에서 실제 HTTP 작업 PASS |
| 정상 종료·cold inspect·재시작 | 실제 8095에서 2회 PASS, 이후 최종 V11 기동 PASS |
| V10 rollback 묶음 | 직전 정상 종료 DB·JAR·UI·설정·receipt·도구 및 파일별 hash 보존 |

운영 원본에는 시험 계정·게시물을 만들지 않았다. 기능 쓰기 검증은 사본의 8098에서 수행했고, 실제 8095에서는 DB migration과 로그인 화면/공개 API 조회, 정상 종료·cold 보존·재시작을 검사했다. 사본 서버는 종료했다. 비정상 데이터 변화·checksum 차이·기동 실패가 없어 실제 rollback은 실행하지 않았다.

## 운영 실행과 증거

- 시작 파일: `.cache/company-v11/start-ui.ps1` (PowerShell 7).
- 실행 설정: `.cache/company-v11/ui-launch.json`.
- 고정 실행물: `.cache/v11-release/V11-RC1-20260929/runtime/v11-rc.jar`.
- 운영 DB: `.local-data/aica-local.mv.db`.
- 운영 승인/receipt 폴더: `.cache/company-v11/promotion-20260929-1/run/`.
- 종합 결과: [acceptance.json](../.cache/company-v11/promotion-20260929-1/acceptance.json).
- 최종 실행 대조: [final-health.json](../.cache/company-v11/promotion-20260929-1/final-health.json).
- 운영 승인 기록: [approval.json](../.cache/company-v11/promotion-20260929-1/approval.json).
- 실제 적용 로그: `.cache/company-v11/promotion-20260929-1/operating-promotion.log`.
- 실행 당시 orchestration 소스: 같은 폴더 `executed-promote-operating.py`, `executed-start-hidden.ps1`.

정상 실행은 파일 DB schema V11과 receipt를 검증만 하며 migration/seed/baseline을 자동 실행하지 않는다. `AUTO_COMPACT_FILL_RATE=0`을 유지한다. React 정적 파일은 승인 JAR 내부를 사용하며 V10 외부 `ui-assets` 설정을 상속하지 않는다. 운영 JAR/receipt를 수정하거나 현재 V11 DB를 과거 V10 시작 파일로 실행하지 않는다.

최종 확인 당시 PID는 33136이었다. PID는 재시작하면 바뀌므로 다음 작업에서는 최신 `run/serve-*/normal-runtime-process.json`과 실제 8095 listener를 다시 대조한다. 종료에는 해당 실행의 `shutdown-tool/graceful-stop.jar`를 사용한다. 강제 종료나 열린 DB의 파일 복사를 사용하지 않는다.

## 고정 해시와 rollback 기준

| 항목 | SHA-256 |
| --- | --- |
| 운영 승인 계획 | `365bdb61846fb453fad741ea4a186514d7f5d0eb91949b7f92b997776e6198c2` |
| 운영 receipt | `04cf0f68bc08ab9f795d5c147999cee9409890378b738b390859f62fbb54c85b` |
| 직전 전체 백업 manifest | `529c21bea79f7e4b9b2447719e0cb617f91f120a6a87ba93c0295d0633af82ac` |
| 직전 V10 DB | `a4a9295efc5c211f77973a3da4983d0b74311c5296025b21a507a2fdcd317d98` |
| 승인 V10 JAR | `606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e` |

직전 rollback 묶음은 `.cache/company-v11/promotion-20260929-1/capture/backup/`, 해시 목록은 같은 capture 폴더의 `backup-manifest.json`이다. 이전 준비 리허설 백업이 아니라 실제 승격 직전 새로 확보한 묶음이다. 정상 종료에 따른 H2 내부 파일 hash 변화 외에 이전 리허설 이후 업무 데이터 변화는 없었다.

복구는 [V11 승격/rollback 절차](V11_PROMOTION.md)를 따른다. 승인 V10 원본 JAR·UI·설정·receipt/run은 별도로 그대로 보존했다. V11 운영에서 새로 작성한 내용은 V10 백업에 없으므로, 향후 rollback 시에는 V11 DB를 보존하고 데이터 차이 처리 방침을 먼저 결정한다. 승격 orchestration은 일회용이며 완료된 폴더로 재실행하지 않는다.

## UX 개선 기준

기능·schema·권한 기준은 이 V11 운영 버전이다. 이후 UX 변경은 이 기준과 비교하고 새로 빌드한 산출물을 별도로 검증한다. 현재 승인 RC/JAR·receipt·이전 migration을 덮어쓰지 않는다. 과거 V10의 UI 단독 배치 기록을 현재 운영 절차로 사용하지 않는다.
