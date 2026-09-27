# V10 새 Windows 경로 승인 검증 결과

검증일: 2026-09-28. 목적은 이미 V10인 DB의 relocation 승인이다. CMS 기능·React·DB schema·기존 migration·dependency는 변경하지 않았다.

## 판정

**새 절대 경로의 V10 사본을 공식 재승인하는 기술 blocker는 검증 범위에서 해제했다.** 회사 PC 자체의 설치·권한·파일 전달과 기존 SUPER_ADMIN 브라우저 로그인 문제까지 해결됐다는 뜻은 아니다. 실제 회사 PC에서의 최종 조회/종료/재시작은 [이관 절차](TRANSFER_TO_NEW_PC.md)대로 별도 수행한다.

## 구현 범위

| 파일 | 변경 |
|---|---|
| `scripts/cutover/cutover.py` | relocate-plan/relocate-approve 분기, relocation 자료의 serve·최초 activation 처리, relocation에서 V3 rollback 차단 |
| `scripts/cutover/relocation.py` | 전달 증거 hash, read-only 검사, 일회성 승인, 새 실행 자료 생성, 실행 자료 무결성 검사 |
| `scripts/cutover/RelocationInspect.java` | 기존 RC의 FileDatabaseSafety/Flyway 코드를 호출하는 외부 read-only 도구 |
| `scripts/cutover/relocation_probe.py` | 별도 사본 A/B 및 부정 사례 자동 검증 |
| `docs/5D/TRANSFER_TO_NEW_PC.md` | 공식 명령, 전달 도구, 승인/실행/재시작 절차 |

Java helper와 shutdown agent만 JDK로 컴파일한다. CMS JAR이나 React를 재빌드하지 않는다. helper JAR은 자기 검사 class만 포함하고 RC 클래스를 덮어쓰지 않는다. 원본 RC 안의 FileDatabaseSafety/FileRuntimeConfiguration/CutoverTool은 변경하지 않았다.

승인 RC SHA-256:
`606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e`

## 승인 자료와 수명

- `relocate-plan`: 절대 경로·DB bytes hash·전체 fingerprint·V1~V10 checksum·pending 0·validate·RC hash·전달 기준을 읽기 전용으로 검사한다. 24시간 유효한 plan을 생성한다.
- `relocate-approve`: plan hash 및 `--authorize-relocation`을 요구하고 실제 파일을 재검사한다. 계획은 `.spent`로 소모한다.
- `input.json`, `result.json`, `runtime-receipt.json`, `relocation-plan.json`, `approval-inspection.json` 및 종료 도구를 새로 생성한다. 기존 파일의 경로를 편집하지 않는다.
- `approvalKind=V10_RELOCATION`, `migrationPerformed=false`, `migrationsExecuted=0`, `baselineCreated=false`를 기록한다.
- receipt의 `status=MIGRATED_V10`은 기존 RC가 요구하는 V10 schema 상태 호환값이다. 이관 중 migration을 수행했다는 의미가 아니다.
- 최초 serve 직전에 DB bytes를 승인값과 비교한다. 정상 실행 후 activation을 기록하고 이후에는 정상 운영 변경을 허용한다. 매 실행의 경로·RC hash·receipt·V10 validate·workaround 검사는 유지한다.
- 파일에 모두 접근 가능한 머신 관리자를 막는 전자서명 시스템은 아니다. 신뢰 경로로 전달 hash를 대조하고 운영 승인 자료의 접근권한을 관리해야 한다.

## 테스트 결과

자동 검증 27개: 긍정 경로 7개, 차단 경로 20개 통과.

| 검증 | 결과 |
|---|---|
| 경로 A 공식 승인 → serve → 종료 → cold → 재시작 | 통과 |
| 다른 경로 B 별도 공식 승인 → serve → 종료 → cold → 재시작 | 통과 |
| A의 receipt를 B에 사용 | 기존 RC에서 차단 |
| A/B 승인 전후 bytes SHA-256 | 동일 |
| A/B 로그인·사이트 관리/구조 HTML·page 1/65·콘텐츠·이력·공개 GET | 세션 기반 HTTP 검사 통과 |
| A/B 총 4회 정상 실행·종료 | validate-only, Hikari shutdown, 파일 독점 접근, V10/history/전체 fingerprint 유지 |
| 잘못된 RC hash / 승인되지 않은 RC | 차단 |
| V9 / checksum 다른 V10 | 실제 RC/Flyway 검사에서 차단, 검사 전후 bytes 동일 |
| pending migration | 검사 도구가 차단. negative 전용 가짜 RC에만 미래 migration 리소스를 넣었으며 실행하지 않음 |
| workaround 누락 / web cutover flag | 변경하지 않은 runtime guard에서 차단 |
| 기준 fingerprint 불일치 / source receipt·manifest 오류 | 차단 |
| relocation 중 cutover flag | 차단 |
| 이미 사용한 승인 / 명시적 승인 없음 / 만료 | 차단 |
| V3 plan을 relocation 승인에 사용 | 차단 |
| 승인 후 최초 serve 전에 DB 파일 교체 | 차단 |
| relocation run-dir에 V3 rollback 요청 | 차단 |

V9/checksum 부정 사례는 **폐기 가능한 별도 부정 테스트 DB**의 Flyway 이력만 구성해서 확인했다. 정상 이관 A/B에는 업무 쓰기, baseline, migration, revision/activity 생성이 없다. 기존 원본이나 전달 기준본을 수정하지 않았다.

기존 V3 경로 회귀:

- 기존 Java `CutoverSafetyTest`: **5개 통과**.
- 보존된 V3 백업의 새 사본에서 현재 `plan/run`을 실행: V3→V10, baseline, 5회 종료/cold 검사, 기존 CMS/역할 smoke, 재시작 통과.
- 같은 사본에서 기존 `rollback`: V3 DB와 V3 runtime 복구, 기존 테이블 fingerprint 일치, version=3 확인.
- 위 V3 회귀의 baseline·업무 smoke는 원래 cutover 검증 절차에 따른 **회귀 전용 사본 작업**이다. V10 relocation 승인 경로의 작업이 아니다.

## 원본 보존

- 집 PC 원본 서버 PID 9788, 시작 시각 2026-09-27 23:41:42 유지. 종료/재시작하지 않음.
- 애플리케이션·frontend·pom 보호 파일 210개 hash 변경 없음.
- 승인 release bundle 15개 hash 변경 없음.
- 원본의 13개 API/데이터 조회 묶음이 기존 기준과 동일. 원본 DB 파일을 별도 H2 연결로 열거나 복사하지 않음.
- 원본 기능·사전·IA·계정·운영 데이터 변경 없음.

검증 자료는 Git 제외 디렉터리 `.cache/relocation-v10/20260928-012640/`에 있다.

| 증거 | 위치 |
|---|---|
| 이관 27개 결과 | `acceptance-final/results.json` |
| A/B 승인 자료 | `acceptance-final/path-A/run`, `acceptance-final/different-path-B/run` |
| A/B cold 검사 | `acceptance-final/A-cold-*.json`, `B-cold-*.json` |
| 기존 전환 회귀 | `regression-run/result.json` |
| rollback | `regression-run/evidence/rollback-result.json` |
| 원본 보호 기준 | `protected-files.json` |

## 남은 범위

- 실제 회사 PC의 실행·종료·재시작 확인은 아직 수행하지 않았다.
- SUPER_ADMIN 숫자형 계정과 이메일 입력 UI의 기존 불일치는 그대로다. 세션 HTTP 인증 성공을 일반 브라우저 로그인 결함 해결로 해석하지 않는다.
- H2 2.3.232 workaround와 Flyway H2 지원 경고는 유지한다.
- 실제 홈페이지는 없으며 렌더링 E2E는 미완료다.
- 운영 사전/IA 입력이나 DB migration을 이관 과정에 추가하지 않는다.
- 새 도구/문서는 비공개 저장소 `parkseongji301-source/aica-cms-v10`의 전달 대상이다. 게시 완료 보고의 commit SHA와 이관 문서 H4가 지정한 최신 도구 묶음 hash를 확인한다. DB·승인 RC·receipt는 Git으로 전달하지 않는다.
