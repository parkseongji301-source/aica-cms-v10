# 5C-2 재시도 결과 — 원본 V10 전환 성공

2026-09-27 23:42:44 +09:00 최종 확인. **원본 V10 전환과 미완료 보충 검사를 모두 통과했다. rollback하지 않았으며 V10 서버를 정상 기동 상태로 유지했다.** 실제 홈페이지 렌더링 E2E는 미완료다.

관리자: [사이트 관리](http://127.0.0.1:8095/admin-next?view=manage) · [사이트 구조](http://127.0.0.1:8095/admin-next?view=structure)

## 1. 새 V3 기준점

원본에 접근하기 전에 실패했던 SQL 명령부터 보존된 V10의 새 사본에서 검증했다. 이후 Java/DB 접근 프로세스와 포트 점유가 없고 원본 파일 독점 접근이 가능한 것을 확인했다.

- 새 기준점 시각: **2026-09-27T23:33:14.546570+09:00**
- 원본 절대 경로: `C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.local-data/aica-local.mv.db`
- 최신 V3 DB SHA-256: `3d8340a64a368a2ab2d7617518b8750c68917d94daa24f1007ad46e1bad99118`
- V3 runtime SHA-256: `c20f0f4d92e1867fbdf326c30b67949ceedf71aa3c0a2d597dc4432e1201cf6d`
- 승인 V10 RC SHA-256: `606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e`

새 V3 DB, 대응 V3 runtime, 승인 release 전체, 현재 소스·문서 등 기준 파일 368개, Git HEAD/status/working-tree patch, 실행 설정, V1~V10, checksum, fingerprint와 rollback 자료를 새로 보관했다. V3 history와 기존 16개 테이블 fingerprint가 직전 rollback 기준과 일치했다.

이번 증거 루트는 `C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c2-retry/20260927-233113/`다. 이 아래 `pre-cutover`가 이번 전환의 최신 기준점이다. 이전 실패 시도의 백업이나 소모 plan을 전환에 재사용하지 않았다.

## 2. 보충 검사 명령 사전 검증

수정한 범위는 보충 SQL 명령의 인수 전달 방식뿐이다. 애플리케이션·UI·dependency·migration·승인 cutover 스크립트는 그대로다.

- Python `subprocess.run`에 네이티브 인수 배열을 전달하고 `shell=False`로 실행했다. PowerShell이 `-Dfile.encoding=UTF-8`을 다시 해석하지 않는다.
- 인수 순서는 Java → JVM option → `-cp` 및 승인 RC 내부의 동일 H2 2.3.232 JAR → `org.h2.tools.Shell` → JDBC/SQL 옵션이다.
- DB 경로를 정규화한 절대 경로로 전달했다. `.mv.db`를 제외한 JDBC base path를 사용했다.
- `ACCESS_MODE_DATA=r;IFEXISTS=TRUE`로 읽기 전용 검사했다. `AUTO_COMPACT_FILL_RATE=0`은 유지하지만 읽기 권한을 쓰기 권한으로 바꾸는 옵션이 아니다. V10 쓰기 runtime의 강제 검사와 구분했다.
- 원본 접근 전 V10 사본, 최신 V3 acceptance 사본, 원본 V10, 원본 재발행 후 cold 상태에서 같은 명령을 실행했다. 모두 V10 history·baseline ID·개수를 정상 출력하고 검사 전후 DB SHA-256이 같았다.

명령과 검증 증거: [검사 명령](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c2-retry/20260927-233113/readonly-baseline-check.py), [원본 최종 SQL 출력](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c2-retry/20260927-233113/original-final-sql/sql-output.txt).

## 3. 새 plan과 guard

새 원본 plan SHA-256은 `e815fdcb45ca6b152a3936f81e8f24e52bd442b704584ab076904137b53a0841`이다.

정확한 절대 경로, 최신 V3 SHA/fingerprint, V3 history와 V1~V3 checksum, 동일 RC checksum, 승인 migration manifest, `AUTO_COMPACT_FILL_RATE=0`, 파일 독점 접근, 사용자 재시도 승인 및 `--authorize-original-cutover`를 확인했다. guard를 완화하거나 이전 `.spent` 파일을 지우지 않았다.

이 새 plan도 성공한 일회성 migration에서 소모되어 `.spent`가 생겼다. 재사용할 수 없다.

## 4. V4→V10 결과

최신 V3 사본에서 고정 `plan/run/serve` 경로, 5회 반복 기동, 보충 SQL, 동일 구성 재발행을 먼저 통과했다. 원본의 hash가 새 plan 이후 변하지 않았음을 다시 확인한 뒤 동일 경로로 원본을 전환했다.

| Version | 승인 Flyway checksum | 결과 |
|---|---:|---|
| V4 | 1414704998 | 1건 적용·validate 성공 |
| V5 | 1744004732 | 1건 적용·validate 성공 |
| V6 | -285908275 | 1건 적용·validate 성공 |
| V7 | 908107551 | 1건 적용·validate 성공 |
| V8 | 804202609 | 1건 적용·validate 성공 |
| V9 | -148566281 | 1건 적용·validate 성공 |
| V10 | -1567156257 | 1건 적용·validate 성공 |

독립 SQL 검사에서 V1~V10 성공 10건, 실패 0건을 확인했다. 정상 runtime의 V10 검사에서 validate와 pending migration 0개 조건을 통과했다. 운영 사전은 DEFER이며 **기수 0건·주제 0건**이다. 승인 migration 자체의 등록형 콘텐츠 유형 baseline 외에 임의 운영 데이터를 넣지 않았다.

## 5. 데이터 무결성

각 migration 단계마다 기존 16개 테이블의 기존 컬럼 fingerprint를 비교했다. V8은 sections_json의 ID/schemaVersion/variation 메타데이터를 제외한 원래 블록 내용·순서를 별도로 비교했다. 원본 receipt의 7개 단계 모두 기존 데이터 보존 검사를 통과했다.

콘텐츠 9건, 기존 category 3건, 페이지 2건, 페이지 발행본 2건, 메뉴 2건, 계정 2건, 사이트 설정 12건을 유지했다. 미디어·콘텐츠 발행본은 원래 0건이며 그대로다. 기존 콘텐츠 ID, category_id, 계정/역할 및 메뉴 연결을 바꾸지 않았다.

이후 승인된 baseline·명시 저장·동일 구성 재발행 때문에 생긴 변경은 별도로 구분했다.

- post_versions 9건, page_versions 10건, template version 0건.
- page 65 명시 저장 version 5건: 반복 지속성 시험 4건 + 재발행 직전 1건.
- page 65 발행 version 1건, revision 4→5, 발행 시각 갱신.
- 관련 활동 이력 증가: 38→58건. 기존 이력을 삭제하지 않았다.
- 이 외 migration 직후와 최종 cold 비교에서 변경된 테이블은 없음.

원본에 시험 글·계정·미디어·템플릿을 만들지 않았다. 별도 `.local-data/backoffice.mv.db`도 작업 전후 hash가 동일하다.

## 6. baseline 대상과 개수

| 대상 | 실제 원본 ID | baseline 개수 |
|---|---|---:|
| 콘텐츠 초안 | 1, 2, 3, 4, 5, 6, 7, 8, 33 | 9 |
| 콘텐츠 발행본 | 없음 | 0 |
| 페이지 초안 | 1, 65 | 2 |
| 페이지 발행본 | 1, 65 | 2 |
| 공용 템플릿 | 없음 | 0 |

콘텐츠 baseline version ID는 1~9다. 페이지 baseline version ID는 1(page 1 초안), 2(page 1 발행본), 3(page 65 초안), 4(page 65 발행본)다. baseline 재실행은 모두 0건이었고, 독립 SQL에서 대상/이유별 중복도 0건이었다. 재발행 이후에도 baseline ID·개수는 그대로다.

## 7. persistence 반복 검사

고정 원본 run에서 5회 정상 기동·정상 종료·파일 잠금 해제·별도 JVM readonly 검사를 수행했다. 앞선 4회는 page 65의 같은 내용을 명시 저장하여 새 version이 재시작 후 유지되는지 검증했다. 공개 snapshot은 그대로였다.

보충 serve에서 재발행한 뒤 다시 정상 종료하고, Java PID 종료·Spring/Hikari 종료·독점 파일 접근·V10 history·baseline·DB hash를 확인했다. 최종 serve 재시작 후 페이지, publication, version 목록, 공개 API, 콘텐츠 목록, 메뉴 및 분류 사전이 종료 전과 정확히 일치했다.

합계는 **원본 정상 기동 7회, 완료한 정상 종료 6회, 최종 1개 서버 실행 중**이다. 재발행 후 마지막 cold SHA-256은 `9e2c28cc63297b06ad475e1054afd40e92dad76b3459d7dd0686107ba23c2406`이다. 이는 실행 중인 파일의 현재 해시를 주장하는 값이 아니라, 최종 재기동 직전에 확인한 값이다. 같은 cold 상태의 별도 V10 백업도 증거 루트에 보관했다.

## 8. 원본 재발행과 공개 API

대상은 기존 page 65 `인사교 소개`다. 초안과 기존 발행본의 제목·slug·블록 구성이 정확히 같고 pending이 없음을 먼저 확인했다.

1. 동일 내용 명시 저장 → MANUAL_DRAFT version ID 9 생성, revision 4 유지, 공개 API 전체 응답 불변.
2. 기존 발행 경로로 재발행 → PUBLISH version ID 10 생성, page/publication revision 5.
3. 공개 발행 시각: `2026-09-25T16:07:11.648753+09:00` → **`2026-09-27T23:40:11.242678+09:00`**.
4. 공개 API의 publishedAt을 제외한 나머지 응답은 동일. 제목·본문·slug·블록 내용·순서·ID·variation·visible을 바꾸지 않음.
5. 정상 종료·재기동 후에도 새 publication, version 및 공개 응답 유지.

page 65 block ID는 `block_44c17a08-32c1-4bdd-a42c-a4266a9ad033`이며 저장·재발행·재시작에서 유지됐다. [원본 재발행 결과](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c2-retry/20260927-233113/original-republication/result.json)에 전후 상태를 기록했다.

실제 본문·분류·맛집 주소 변경, POSTS category/query/manual, 역할별 쓰기·차단, 템플릿·복구·미디어 보호의 전체 동작은 고정 runbook대로 별도 smoke 사본에서 확인했다. 8개 CMS 검증 묶음과 세 역할 요청 검증이 통과했다. 원본에서는 기존 데이터 조회·편집 진입·두 탐색 경로·block URL·미리보기·이력·메뉴·미디어/템플릿/컴포넌트 목록·역할 정책·공개 API를 확인했다. 원본에 시험용 사전을 넣지 않았으므로 이 차이를 구분한다.

## 9. 최종 V10 서버

- PID: **9788** (최종 확인 시점)
- 바인딩: **127.0.0.1:8095**, profile `dev`
- 실행물: 승인된 동일 V10 RC JAR
- DB: 원본 `.local-data/aica-local.mv.db`
- 연결 옵션: `IFEXISTS=TRUE;DB_CLOSE_ON_EXIT=FALSE;AUTO_COMPACT_FILL_RATE=0`
- 상태: 로그인 및 API 정상, `migration=validate-only`

현재 서버가 실행 중이므로 같은 포트/DB로 두 번째 서버를 시작하지 않는다. **기존 `scripts/run-local.ps1`은 V3 runtime용이므로 원본 V10에 사용하지 않는다.** 이번 동결 범위에서는 해당 스크립트를 수정하지 않았다.

정상 종료 후 재실행할 때에는 승인된 다음 경로를 사용한다. 이 명령은 migration 승인 모드가 아니다.

```powershell
& 'C:/Users/sfsf1/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -X utf8 `
  'C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c1c/20260927-223140/release-final/cutover/cutover.py' serve `
  --java 'C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.tools/jdk/jdk-17.0.20.1+1/bin/java.exe' `
  --run-dir 'C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c2-retry/20260927-233113/original-run' `
  --port 8095
```

## 10. cutover mode 종료

일회성 migration 자식 프로세스는 종료됐고 새 plan은 소모됐다. 정상 서버는 `cutoverFlag=false`이며 receipt의 DB 절대 경로/RC hash와 기존 V10 schema를 validate한다. 매 기동 시 migration을 재승인하지 않는다. Java 프로세스도 최종 V10 서버 하나만 유지한다.

## 11. rollback 필요 여부

**이번 재시도에는 STOP 조건이나 rollback이 발생하지 않았다.** 새 V3 백업·대응 runtime·복구 자료를 계속 보관한다. 이번 성공한 V10 DB에 V3 JAR만 실행하는 것은 복구가 아니다. 쓰기 재개 뒤 새 작업은 전환 직전 V3에 포함되지 않으므로 향후 rollback 시 별도 보존·검토가 필요하다.

## 12. 5D 전에 남은 항목

- 실제 공개 홈페이지 확보 및 렌더링 E2E. 이번 결과는 CMS/공개 API 계약과 발행 경계까지다.
- 운영용 기수·주제 사전 확정 및 별도 등록 승인. 현재 0건 유지.
- 운영 인수인계에서 V10 전용 기동/종료 절차, 오래된 V3 실행 경로 사용 방지, 백업 보관 위치·주기·담당자와 복구 책임 정리.
- H2 2.3.232의 workaround 유지. H2 업그레이드 및 workaround 제거 검증은 별도 작업.
- Flyway의 H2 지원 경고는 남아 있다. 반복 migrate/validate 통과와 공급자 지원 범위는 구분한다.
- OneDrive 아래 파일 DB 장기 운영 배치, 운영 계정/접속 범위 등 배포 환경 최종 결정.

새 기능·UI·migration·dependency·분류 데이터 변경 없이 기존 기준 파일 368개와 승인 release manifest 15개 항목의 해시가 그대로임을 확인했다. 이번에 추가한 것은 실행 증거, 보충 검사 명령 및 결과 문서다.

최종 증거: [전체 판정](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c2-retry/20260927-233113/final-status.json), [원본 migration receipt](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c2-retry/20260927-233113/original-run/runtime-receipt.json), [종료 후 독립 검사](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c2-retry/20260927-233113/original-final-cold.json), [최종 리스너](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/.cache/phase5c2-retry/20260927-233113/final-listener.json).
