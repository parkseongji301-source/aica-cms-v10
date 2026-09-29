# V11 RC1 승격 준비 결과

2026-09-29 최종 판정: **요청한 준비·리허설 항목 모두 PASS. 실제 8095 V11 승격은 미실행.**

운영 서버는 승인 V10 JAR, 기존 외부 UI, 원래 DB와 receipt로 8095에서 실행 중이다. 사용자 추가 승인에 따라 V10 기동·정상 종료·백업·재기동만 수행했다. 정상 종료 과정의 H2 내부 저장 상태 갱신은 발생했으며, 기동 전후 업무 데이터·schema·승인 실행물은 동일했다. 운영 DB에 V11 migration, 테스트 계정/글, seed 또는 baseline을 적용하지 않았다.

## 산출물

| 산출물 | 위치 / 값 |
| --- | --- |
| 공식 절차 | [V11_PROMOTION.md](V11_PROMOTION.md) |
| 배포 후보 | [v11-rc.jar](../.cache/v11-release/V11-RC1-20260929/runtime/v11-rc.jar) |
| RC SHA-256 | `5be79fd5ce32e348e6ddf0f3b5acd5f32bebd0441802ac3356d64deca800906e` |
| 릴리스 manifest | [release-manifest.json](../.cache/v11-release/V11-RC1-20260929/release-manifest.json) |
| 종합 증거 | [result.json](../.cache/v11-preflight/rehearsal-rc1-accepted/result.json) |
| 사본에서 발급한 receipt | [runtime-receipt.json](../.cache/v11-preflight/rehearsal-rc1-accepted/promotion/runtime-receipt.json) |
| 최신 정상 종료 백업 | `.cache/v11-preflight/capture/backup/` |
| 백업 파일별 해시 | [backup-manifest.json](../.cache/v11-preflight/capture/backup-manifest.json) |
| V10 백업 DB SHA-256 | `5181646c8cd5772c5e8721b2592d09c81c45ebb793a9a014baaddb2377d24c2b` |
| 승인 V10 JAR SHA-256 | `606ad598cf965d3a065df1a0eeed13581dcfdd7aa9d8e2313dd772bd5b80822e` |

위 receipt는 **리허설 사본 경로와 RC hash에 묶인 증거**다. 운영에 복사하거나 경로를 수동 편집해 사용하지 않는다. 실제 승격 시 최신 백업과 새 계획으로 운영용 receipt를 발급해야 한다.

## 요청별 판정

| 검증 항목 | 결과 | 근거 |
| --- | --- | --- |
| RC 생성 전 V1~V10 내용/checksum | PASS | 승인 JAR에서 SQL 원본을 추출해 비교. 9개 SQL의 CRLF를 승인 LF 바이트로 복원. 실제 Flyway checksum은 복원 전후 동일 |
| V10→V11 공식 절차 | PASS | 계획·해시·전체 백업·일회성 승인·cold inspect·receipt·실행·rollback 절차 및 도구 준비 |
| 새 V11 RC 및 SHA-256 고정 | PASS | 전체 테스트 후 생성. 최종 JAR의 V1~V10 SQL 및 V8 Java class 바이트까지 승인 JAR과 동일 |
| MIGRATED_V11 receipt | PASS | 사본에 공식 도구 실행. migration 1건, 기존 데이터 보존, baseline/업무 쓰기 0 및 cold 증거 포함 |
| 최신 정상 종료 사본 | PASS | 실제 8095가 사용하는 V10을 정상 기동·종료. 원본과 백업 DB hash 동일, 전체 V10 묶음 백업 |
| V11만 적용 | PASS | V1~V10 이력 동일, V11 한 건 추가, POST_TRASH 초기 0행, 기존 컬럼 유지 |
| 기존 데이터/계정/분류/첨부/발행본/버전 | PASS | 기존 35개 테이블의 행 수·모든 컬럼 값·BLOB fingerprint 동일. 기능 시험 후에도 원래 모든 행의 해시가 존재 |
| React 작성·수정·게시·재게시 | PASS | RC JAR의 React를 실제 사본 서버 8098에서 조작. 초안 저장 시 공개본 유지, 재게시 후 공개 API 변경 확인 |
| 휴지통→복원→재게시→영구삭제 | PASS | 실제 HTTP로 전 과정 실행. 같은 ID/최신 초안/첨부/맛집 주소/이력 보존, 재게시 전 404, 실제 영구삭제 확인 |
| 정상 종료·cold inspect·재시작 | PASS | schema 전용 2회, 기능 시험/React 종료 및 이후 재시작. 발행본·버전·첨부·휴지통 상태와 전체 fingerprint 유지 |
| 적용 직전 V10 전체 묶음 rollback | PASS | 사본의 V11 DB/JAR/receipt 보존 후 V10 DB 바이트 복원. 승인 V10 JAR/설정/receipt 기동, 로그인 후 UI 20개 파일 바이트 일치, 임시 계정 제거 후 원래 DB 재복구·정상 재시작·cold 보존 확인 |

## Migration 고정값

| 버전 | Flyway checksum |
| --- | ---: |
| V1 | 379266581 |
| V2 | -925664058 |
| V3 | -1610808639 |
| V4 | 1414704998 |
| V5 | 1744004732 |
| V6 | -285908275 |
| V7 | 908107551 |
| V8 | 804202609 |
| V9 | -148566281 |
| V10 | -1567156257 |
| V11 | 502117418 |

V1~V10의 의미나 내용은 변경하지 않았다. Flyway가 줄 끝을 정규화하므로 이번 LF/CRLF 차이는 실제 checksum을 바꾸지 않았지만, 사용자 요구대로 SQL 바이트 자체도 승인 원본에 맞췄다. `.gitattributes`에 해당 migration SQL의 LF 고정을 추가했다. V8은 Java migration이므로 checksum뿐 아니라 JAR 내 관련 class 파일도 모두 비교했다.

증거: [RC migration 감사 결과](../.cache/v11-release/V11-RC1-20260929/migration-audit/result.json), [RC 생성 전 감사](../.cache/v11-preflight/migration-audit/result.json).

## 데이터 보존 범위

전체 35개 테이블을 검사했으며 대표 수치는 다음과 같다. 단순 건수 비교 외에 모든 셀을 반영한 fingerprint도 비교했다.

| 데이터 | 보존 수량 |
| --- | ---: |
| 계정 | 2 |
| 게시물(원본 테이블 전체) | 18 |
| 카테고리 / 유형 / 기수 / 주제 | 3 / 5 / 2 / 10 |
| 첨부 원본(BLOB 포함) | 1 |
| 페이지 / 페이지 발행본 | 2 / 2 |
| 게시물 / 페이지 / 템플릿 버전 | 11 / 10 / 2 |
| 템플릿 / 블록 식별자 | 2 / 9 |
| 활동 기록 | 88 |

이 V10 원본에는 게시물 발행본이 0건이었다. 기존 페이지 발행본 2건은 그대로 보존했고, 게시물 발행본의 저장·복원·재게시·재시작은 사본에 새로 만든 시험 글로 검증했다. 기존 계정의 비밀번호를 바꾸지 않았으며 사본 전용 계정으로 기능을 검사했다.

백업에는 운영 DB·JAR·UI·실행 설정·승인 receipt/run·이관/종료 도구를 포함했다. 작업 폴더의 소스 스냅샷은 참고 증거이며 승인 V10 소스로 간주하지 않는다.

## 시험 상세와 제한

- Java 전체: 174건 중 **168 PASS, 실패/오류 0, 6 SKIP**. 프런트엔드: **48 PASS**. 총 실행 통과 216건. 프런트엔드 타입 검사/빌드 및 Maven verify 성공.
- 제외된 6건은 별도 경로를 요구하는 과거 V3/분류/V7/V8/V9/V10 사본 검사다. 이번 요청의 최신 V10→V11 파일 DB 검증은 별도 공식 도구 리허설로 실제 수행했으며 생략하지 않았다.
- RC 리허설은 `dev` 파일 DB + 정상 `MIGRATED_V11` receipt로 실행했다. `design-preview`, 메모리 DB, `copy-validation` 우회를 사용하지 않았다.
- 게시 API는 첨부 파일·서식·카테고리·맛집 유형/주소를 포함한 게시물로 검사했다. 오래된 revision의 삭제 요청은 409로 거절됐고, 실제 purge 후 휴지통/본문/복원 API에서 사라진 것을 확인했다. 통합 테스트에서는 소유 관계/버전의 물리 삭제도 검사했다.
- React 시험 글은 ID 137, API 시험의 삭제 글은 ID 135, 재시작 보존 글은 ID 136이었다. 모두 사본 데이터이며 운영 원본에는 없다.
- 브라우저에서는 영구삭제 확인란 미선택 시 버튼 비활성, 선택 후 활성까지 검사하고 취소했다. 실제 최종 영구삭제는 별도 API 시험에서 실행했다. React 콘솔 오류 0건.
- 리허설 보조 도구의 날짜 해시 처리와 API fixture 형식 오류는 운영 변경 없이 중단된 뒤 수정해 새 사본에서 재실행했다. V10 UI 검사도 인증 필요 조건을 반영해 보완한 후 재검증했다. 제품 코드나 RC JAR은 이 과정에서 변경하지 않았다. 최초 실패 로그를 보존했다.
- V10 UI 인증 검증에 추가한 임시 계정은 검사 후 백업 DB 재복구로 제거했다. 최종 사본은 원본 V10의 35개 테이블과 모두 동일하다.

## 실제 승격 결정 시 확인할 사항

RC1과 리허설은 PASS지만 **실제 V11 승격 승인은 아직 없다.** 8095 운영 JAR·UI·receipt는 기존 해시를 유지하고 로그인 화면 HTTP 200을 확인했다. 리허설용 8098은 종료했다.

운영은 V10으로 재기동됐으므로 실제 승격 날짜에 다시 쓰기를 중지하고 최신 정상 종료 전체 백업과 새로운 24시간 계획을 발급해야 한다. V11 공개 후 새로 작성한 데이터를 V10 백업으로 자동 합치는 기능은 없다. rollback은 새 V11 DB를 별도 보존하고 적용 직전 V10 전체 묶음으로 복귀한다.

전체 UX/UI 개편·Thymeleaf 제거·경로 정리·동적 IA·대시보드·방문자 통계 및 기타 신규 기능은 이번에 추가하지 않았다.

검증 화면(8098 사본):

![복원 후 React 재게시](../.cache/v11-preflight/rehearsal-rc1-accepted/react-republished.png)
