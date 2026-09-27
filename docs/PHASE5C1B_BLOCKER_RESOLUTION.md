# 5C-1B 연결 수명 blocker 조사 결과

2026-09-27. **원인은 H2 2.3.232 MVStore의 종료 시 파일 압축과 마지막 commit 순서 문제로 특정했다. 다른 V3 파일을 연 문제가 아니다.** 같은 파일의 최근 변경이 닫기 후 유지되지 않아 V3 상태가 다시 읽혔다. SQL 역 migration이나 백업 자동 복원은 없었다.

사본에서는 `AUTO_COMPACT_FILL_RATE=0` 회피책으로 재현을 제거하고 반복 재시작·외부 조회·V3 복구를 검증했다. 이 설정을 유지하는 검증 실행에 한해 연결 수명 blocker를 해소한 것으로 판정한다. **5C-2 원본 전환은 계속 보류한다.** 원본 guard·애플리케이션·UI·의존성·migration·기존 실행 스크립트는 변경하지 않았다.

## 1. 증거 위치와 실행 기준

- 프로젝트: `C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main`
- 이번 증거 B: 프로젝트의 `.cache/phase5c1b/20260927-214648` (`.cache/phase5c1b-current.txt`에 절대 경로 기록)
- OneDrive 외부 E: `C:/Users/sfsf1/AppData/Local/Temp/Aica5c1b-20260927-214648`
- 이전 동결 배포물 R: `.cache/phase5c1/20260927-205122/release`
- JDK 17.0.20.1+1, H2 2.3.232, Flyway 10.20.1, HikariCP 5.1.0, Spring Boot 3.4.5 유지.
- V10 JAR: `R/v10-rc.jar`, SHA-256 `240f377fb0e86665d8de4668352e2de7cf9d83e3df267ab5d8648cd376294306`.
- V3 JAR: `R/v3-runtime.jar`, SHA-256 `c20f0f4d92e1867fbdf326c30b67949ceedf71aa3c0a2d597dc4432e1201cf6d`.

새 원본 콜드 백업 `B/v3-backup.mv.db`는 원본과 바이트 해시가 같다. 실패 재현에는 이전 실패 당시와 같은 `pre-cutover-v3.mv.db`도 별도로 사용했다. 두 백업의 업무 데이터는 같지만 파일 압축 상태·크기는 다르므로 구분했다. 실제 서버 반복 시험은 **새 원본 백업에서 출발**했다.

## 2. 실제 원인과 통제 실험

H2 2.3.232 공식 소스에서 `MVStore.closeStore()`는 마지막 `commit()` 다음에 `FileStore.stop()`을 호출한다. `FileStore.stop()`은 압축 후 clean-shutdown 기록을 쓰지만, 압축 뒤 변경된 메타데이터를 확정하는 마지막 commit이 없다. 이 경로에서 재접속 시 최신 논리 상태가 유지되지 않는 문제가 발생했다.

같은 현상에 대한 H2 공식 [issue #4247](https://github.com/h2database/h2database/issues/4247)과 [수정 PR #4249](https://github.com/h2database/h2database/pull/4249/files)를 대조했다. 외부 보고만으로 원인을 확정하지 않고, 실제 사용 중인 2.3.232 소스의 `FileStore.stop()`에 **압축 뒤 `mvStore.commit()` 한 줄만 추가한 비교용 클래스**를 별도 디렉터리에 컴파일했다. 이 비교용 클래스는 서버 JAR에 넣지 않았다. `B/evidence/upstream-one-line.patch`로 변경량을 확인할 수 있다.

동일한 빠른 연결 절차, 같은 V3 백업, 같은 Flyway·migration·기본 JVM 조건에서 비교했다.

| 조건 | OneDrive 내부 | OneDrive 외부 | 합계 |
|---|---:|---:|---:|
| 기본 압축 설정 | V3 재관찰 1/3 | V3 재관찰 3/3 | 4/6 실패 |
| `AUTO_COMPACT_FILL_RATE=0`만 추가 | V10 3/3 | V10 3/3 | 6/6 통과 |
| 공식 수정의 핵심 commit을 비교용 H2에 추가 | V10 3/3 | V10 3/3 | 6/6 통과 |
| 기본 H2·기본 설정으로 다시 복귀 | V3 재관찰 3/3 | V3 재관찰 3/3 | 6/6 실패 |

총 24개 새 사본의 결과는 `comparison-matrix.json`에 있다. 기본 설정은 총 12회 중 10회 실패했으므로 항상 실패하는 단순 경로 오류가 아니다. 추가 관찰 SQL·시간이 들어간 시험과 UTF-8 JVM 인수를 사용한 일부 시험에서는 재현되지 않았다. **UTF-8 지정이나 관찰 지연을 해결책으로 채택하지 않았다.** 기록 부하를 줄인 원래 절차의 실패→압축 회피/commit 수정 성공→기본값 재실패를 판단 근거로 삼았다.

추가 PID 포함 증거:

| 실행 | PID / Spring profile | 연결·적용 중 | 종료 후 독립 JVM |
|---|---|---|---|
| `proof-default` | 26200 / Spring 없음 | V4~V10 각각 1건 적용, validate 성공, 재실행 0 | V3, 준비 revision도 4로 복귀 |
| `proof-no-compact` | 1592 / Spring 없음 | 위와 동일 | V10, 준비 revision 5 유지 |

순수 JDBC만으로 revision을 증가시키는 단순 시험은 기본값에서도 6회 유지됐다. 따라서 모든 H2 저장이 실패한다고 주장하지 않는다. Flyway를 포함한 기존 재현 절차가 압축 문제를 유발하는 I/O 패턴이었으며, 아래 호환 경고 문구가 실패 원인은 아니다.

## 3. 같은 물리 파일이라는 근거

최종 실패 증거 파일은 정확히 `E/proof-default.mv.db`다.

```text
jdbc:h2:file:C:/Users/sfsf1/AppData/Local/Temp/Aica5c1b-20260927-214648/proof-default;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE
```

| 항목 | 실행 전 | 종료 후 |
|---|---|---|
| Windows File ID | `0x0000000000000000001800000009d10c` | 동일 |
| 크기 | 98,304 bytes | 98,304 bytes |
| SHA-256 | `e6b79dff9d1d16a984708ba162efe8846f4d8e35ed5f134ce73b2615ff74945d` | `455a856aabff1f270da42890cfa5c4d98543a7a7d49f332c08a17566e959e2ad` |
| 수정 시각 UTC | `2026-09-27T11:59:13.6083861Z` | `2026-09-27T13:16:32.8853189Z` |

해시는 달라졌으므로 단순히 백업 바이트가 다시 복사된 결과도 아니다. 프로그램 내 migration 로그의 DB URL, `proof-default.json`의 V10 이력, `proof-default.physical.json`의 전후 식별자, `proof-default.external.json`의 `DATABASE_PATH()`와 V3 이력을 함께 확인했다. 별도 JVM은 `ACCESS_MODE_DATA=r`로 열었고 읽기 전후 파일 해시가 같다.

두 위치의 기본값 실패 사본에는 DB 파일 외에 초기화·복원 프로세스가 없었다. SQL 이력이 V3로 내려간 역 migration을 실행한 것이 아니라 **같은 파일에서 최신 상태가 영속화되지 않은 것**이다. 실패 사본과 MVStore dump도 보존했다.

## 4. datasource·프로파일·경로 추적

| 실행 경로 | 실제 설정 | 판단 |
|---|---|---|
| `run-local.ps1` | 프로젝트 루트로 cwd 고정, 보존된 V3 JAR, `local`, `.local-data/aica-local` | 시작 중 DB 복사 없음. 원본 실행은 이번에 하지 않음 |
| `run-dev.ps1` | `dev`, 기본 `.local-data/backoffice`, `BACKOFFICE_DEV_DB_URL` 대체 가능 | 현재 최신 JAR는 원본 경로 guard에 차단됨 |
| 기존 `run-*-copy.ps1` | `dev`, 사본 경로 절대화, `.mv.db` 제거 후 JDBC base로 사용, copy-validation=true | 원본 파일 복사/초기화 없이 기존 사본을 열음 |
| `test` | `jdbc:h2:mem:mvp-integration;DB_CLOSE_DELAY=-1` 및 테스트별 메모리 URL | 이번 파일 재현과 다른 대상 |
| `design-preview` | 전용 메모리 DB | 이번 파일 재현과 무관 |
| 이번 서버 시험 | `dev`, 명시적 사본 절대 URL, bootstrap=false, copy-validation=true | 실제 Hikari `jdbcUrl` 로그와 SQL `DATABASE_PATH()` 일치 |

통제 실험 `precedence.*`에서는 환경변수와 JVM property에 서로 다른 **존재하지 않는 시험 DB** 및 다른 profile을 지정했다. CLI의 `dev`와 외부 `server-outside` 절대 URL이 실제 Hikari 설정으로 선택됐다(PID 22488). 다른 DB 파일은 생성되지 않았다. 기존 셸에서는 관련 datasource/profile/JVM override 환경변수 이름이 발견되지 않았다. 비밀번호 값은 출력하지 않았다.

`path-v3.json` / `path-v10.json`:

- 같은 파일의 상대 URL·절대 URL은 같은 `DATABASE_PATH()`와 이력으로 연결됐다.
- 폴더가 다른 `same.mv.db` 두 개는 각각 V3/V10이었다. 따라서 이름만으로는 같은 DB라고 판단하지 않는다.
- JDBC base에 `.mv.db`를 잘못 포함한 경우, H2가 추가 확장자를 찾게 되어 `IFEXISTS=TRUE`에서 오류 90146으로 차단됐다. 새 DB는 생성되지 않았다.

## 5. 시작·종료·복원 스크립트 확인

`B/evidence/config-script-search.txt`와 소스/스크립트 hash manifest에 검사 범위를 기록했다. `scripts`, `workbench`, `src`, `.cache`의 시작·seed·checkpoint·restore·fixture·Flyway 호출을 검색했다.

- 기존 시작 스크립트에 V3 백업을 매번 덮어쓰는 동작 없음.
- `LocalDevelopmentInitializer`는 local profile의 지정 URL만 허용하고, 계정이 있으면 즉시 종료한다. 최초 계정/선택적 콘텐츠 import이지 DB 파일 복원이 아니다. 이번 dev 서버에서는 실행되지 않는다.
- bootstrap은 비활성화했고 기존 계정은 보존했다. design preview/test fixture도 해당 전용 실행에서만 사용한다.
- 기존 checkpoint·검증 준비 도구에는 의도적인 파일 복사가 있으나, 서버 시작 hook에서 자동 호출되지 않는다.
- 이번 비교 스크립트는 **각 사본 최초 생성 때만** 백업을 복사했다. 4회 반복 서버 시작 사이에는 파일 복사가 없다.
- 이번 rollback 시험에서만 V10 시험 파일을 별도 보존한 후 같은 시험 경로에 V3 백업을 명시적으로 복원했다.
- `Flyway.clean/repair` 또는 `INIT`로 V3를 재생성하는 경로는 사용하지 않았다. migration 위치는 동결 JAR의 `classpath:db/migration/h2` 그대로다.

## 6. H2 옵션·호환 경고·OneDrive

| 옵션 | 기존 값/기본값 | 이번 판단 |
|---|---|---|
| `DB_CLOSE_ON_EXIT` | FALSE | H2의 JVM 종료 hook 대신 명시적 pool 종료가 필요. Spring 정상 종료 확인. 이것만 변경하는 해결책은 채택하지 않음 |
| `DB_CLOSE_DELAY` | 파일 DB 기본 0 | 마지막 연결 종료 시 닫힘. -1로 연결을 영구 유지해 실패를 감추지 않음 |
| `AUTO_SERVER` | 미지정/false | 파일 DB를 다중 프로세스 공유 서버로 열지 않음 |
| `AUTO_RECONNECT` | 미지정/false | 다른 파일로 자동 재연결하는 설정 없음 |
| `IFEXISTS` | 검증 도구 TRUE | 경로 오류에서 빈 DB 자동 생성 방지 |
| `INIT` | 없음 | 접속할 때 seed/restore SQL 실행 없음 |
| `MODE` | REGULAR | 호환 모드에 의한 경로/초기화 변화 없음 |
| `AUTO_COMPACT_FILL_RATE` | 기본 90 → 진단 실행에만 0 | 유일하게 채택한 운영상 회피 옵션. 종료/자동 압축 경로를 피함 |
| `ACCESS_MODE_DATA` | 외부 검사에서만 r | 읽기 전용 검사 후 hash 불변 확인 |

H2 2.3.232 공식 `DbSettings` 및 `FileStore` 소스로 기본값과 0의 의미를 확인했다. 파일 압축 회피 시 저장 공간을 더 사용할 수 있다. `SHUTDOWN COMPACT`, 수동 compact, 검증되지 않은 다른 도구의 쓰기 연결은 회피 조건 밖이다. 이 옵션은 새로 연결할 때도 적용해야 하며, DB 파일 자체가 영구적으로 고쳐졌다는 뜻이 아니다.

Flyway 10.20.1은 H2 2.2.224까지 테스트했다는 기존 경고를 계속 출력한다. **같은 Flyway와 같은 경고를 유지한 채** H2 압축 설정/commit 순서만 바꿔 결과가 바뀌었다. 경고는 별도의 지원 조합 위험으로 남기며, 경고 문구를 원인으로 연결하지 않는다. H2/Flyway 의존성 교체는 이번에 하지 않았다.

OneDrive.exe PID 9344는 실행 중이었다. 외부 Temp 파일에서도 기본값 실패와 회피 성공을 모두 재현했으므로 OneDrive는 이 현상의 필수 원인이 아니다. 동기화 저장소를 장기 운영 DB 위치로 승인한 것도 아니다.

## 7. 실제 서버 A~K 절차와 반복 결과

새 V3 콜드 백업 → 파일 hash → 기존 V4~V10 적용 → V10 확인 → 동결 V10 서버 시작 → page 65 제목 한 건 저장 → 정상 종료 → cold hash → 외부 read-only 이력/변경값 조회 → **같은 절대 경로** 재시작을 수행했다.

실제 URL은 다음 패턴이다.

```text
jdbc:h2:file:<B/data/server-inside 또는 E/server-outside>;IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0
```

| 위치 | 순차 실행 PID | page 65 초안 revision | 결과 |
|---|---|---|---|
| OneDrive 내부 | 21344 → 30120 → 21576 → 3156 | 4 → 5 → 6 → 7 → 8 | 4/4 통과 |
| OneDrive 외부 | 31728 → 11924 → 19888 → 34736 | 4 → 5 → 6 → 7 → 8 | 4/4 통과 |

각 시작/종료에 profile, effective JDBC URL, normalized physical path, File ID, 파일 크기/mtime/SHA-256, 전체 Flyway 이력, page 1·65/post 33 대표 내용 및 fingerprint, PID, 실행 JAR checksum을 저장했다. `B/evidence/runtime-index.csv`에서 한 번에 비교할 수 있다. 원본 내용 대신 대표 fingerprint를 보고할 때에도 원시 조회 결과를 증거에 남겼다.

실행 중 H2의 Windows byte-range lock 때문에 별도 파일 핸들로 전체 SHA를 읽을 수 없었다. 따라서 서버에 **읽기 전용 관찰 agent**를 붙여 이미 잠긴 H2 FileChannel을 통해 live hash를 수집했다. 업무 SQL은 SELECT뿐이며 파일 위치/내용을 변경하지 않는다. 시작 직후 외부 2회차 한 건은 백그라운드 쓰기 때문에 연속 두 hash가 달랐고 `twoReadHashesEqual=false`로 기록했다. 실행 중 hash를 콜드 백업 hash처럼 해석하지 않았다. **8회 모두 정상 종료 후 cold hash와 외부 read-only 검사 전후 hash는 일치**했다.

- 저장된 초안은 다음 시작에서 API 응답 전체가 이전 저장 응답과 같았다.
- page 1, post 33, page 65 공개본은 반복 중 변하지 않았다.
- 모든 시작에서 Flyway V10, 신규 migration 0. 기존 block ID 유지.
- 종료 agent는 본 시험 PID에 `System.exit(0)`을 요청해 Spring shutdown hook을 실행했다. `Stop-Process` 강제 종료를 사용하지 않았다.
- 매번 `HikariPool-1 - Shutdown completed`, PID 종료, OS 독점 읽기 성공을 확인했다.
- V10 PID 22488 / V3 PID 4088 실행 중 별도 JVM 파일 접속은 각각 H2 90020으로 거절됐다.
- 종료 후 Java·테스트·Flyway 프로세스가 남아 있지 않았다. 원본 파일도 독점 읽기 가능 상태다.

## 8. V3 복구와 원본 보존

내부 시험 V10 파일은 `B/data/server-inside-v10-before-rollback.mv.db`에 보존했다. 같은 시험 경로에 이번 V3 백업을 복원하고 V3 JAR로 PID 4088 / 8097을 실행했다. V10 DB에 V3 JAR를 연결하지 않았다.

로그인, 콘텐츠/페이지/메뉴, React 진입, page 1·65 조회 통과. 종료 후 **16개 업무 테이블의 전체 snapshot과 Flyway 이력이 복구 전 V3 기준과 동일**했다. `rollback-before.json == rollback-after.json` 자동 검증 통과. 복원 파일은 V3로 남겨 증거를 보존했다.

원본 파일은 JDBC로 열지 않았다. 작업 전후 SHA-256:

- `.local-data/aica-local.mv.db`: `55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe`
- `.local-data/backoffice.mv.db`: `2a9ef304a1407a663f903e8924b1e7326c241033bd9e4c002a30b3f95c9b8a90`

원본과 바이트 동일한 사본에서 V3를 확인했다. `src/`, `frontend/`, `scripts/`, `pom.xml`은 이전 동결 manifest와 동일하다. 신규 schema migration, CMS 기능, UI, 권한 정책, guard 변경은 없다.

## 9. 적용한 회피 방식과 남은 전환 조건

이번에 추가한 것은 `workbench/blocker-resolution`의 사본 전용 진단 도구, 증거, 문서다. 기존 실행 스크립트를 일괄 변경하지 않았다.

**검증된 실행 조건:** 정확한 사본 절대 경로 + IFEXISTS + `AUTO_COMPACT_FILL_RATE=0` + 기존 동결 JAR + 단일 writer + 정상 종료 + 독립 read-only 확인. 서버뿐 아니라 migration/seed/검증 등 모든 쓰기 연결에 같은 회피 옵션이 필요하다. 기존 기본 설정으로 다시 연결하면 위험이 재발할 수 있다.

비교용 H2 패치 클래스는 원인 확인용이며 배포물이 아니다. 앞으로 정식 H2 수정 버전 도입을 검토하더라도 별도 의존성 검증과 전체 사본 리허설이 필요하다. 이번 결과로 임의 업그레이드를 승인하지 않았다.

연결 수명에 대한 9개 완료 기준은 재현·원인 대조·회피 재시험·내외부 8회 서버 수명·같은 File ID·외부 조회·잠금 해제·V3 rollback·원본 hash로 충족했다. **회피 조건을 포함한 5C-1B blocker는 해소했지만 원본 전환 승인/완료와는 다르다.**

5C-2 전에 남은 사항:

1. 사용자의 실제 전환 재개 승인, 적용 대상·시간·책임자 확정.
2. 원본 guard는 그대로 유지. 향후 별도 승인 전환 모드는 명시 flag, 대상 절대 경로, V3 이력, V1~V3 checksum, 직전 DB hash/fingerprint, 허용 runtime checksum 중 하나라도 다르면 거부해야 한다. 이번에 활성화하지 않았다.
3. 이 회피 설정을 승인된 실행 구성에 고정할지, 정식 H2 수정 버전을 별도 검증할지 결정. 현재 run-local/과거 도구를 그대로 재사용하면 회피 설정이 보장되지 않는다.
4. 운영 사전·baseline·배포/백업 위치 및 개발용 계정/preview 설정 검토는 기존 5C-2 조건 유지.
5. 실제 공개 홈페이지는 여전히 없으므로 렌더링 E2E는 후속 검수에 남는다.

전체 자동 증거 검증: `workbench/blocker-resolution/verify_results.py`. 결과 `B/evidence/verified-results.json`. 초기 진단에서의 컴파일/권한/기록 실패는 최종 통과 횟수에 포함하지 않았다. 이번 목적과 무관한 CMS 전체 기능 테스트를 다시 실행했다고 주장하지 않는다.
