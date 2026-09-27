# 09. DB migration 가이드

현재 원본은 **V10**이다. 일반 운영은 migrate가 아니라 **validate-only**로 시작한다. `V3 → V10` 전환 plan은 이미 소모됐으며 다시 쓰지 않는다. 이번 문서 작성에서는 migration/repair/clean/baseline/DML을 실행하지 않았다.

## 실제 migration 목록

SQL 경로는 `src/main/resources/db/migration/h2`, Flyway 검색 경로는 `classpath:db/migration/h2`다. V8은 같은 package의 **Java migration**이며 SQL 파일만 백업하면 빠진다.

| 버전 | 파일 | 목적 |
|---|---|---|
| V1 | V1__backoffice.sql | users/역할·account_guard, posts 기본 원본 |
| V2 | V2__cms.sql | category·상태/revision·미디어·콘텐츠/페이지 publication·페이지·메뉴·설정·활동 이력 |
| V3 | V3__rich_editor.sql | posts/발행본 rich_content, 미디어 MIME 확장 |
| V4 | V4__content_classification_schema.sql | content_types/cohorts/topics/허용 주제 및 초안·발행 분류 연결 |
| V5 | V5__content_classification_baseline.sql | 개발자 정의 유형 5종·기존 GENERAL baseline; 실제 운영 기수/주제는 넣지 않음 |
| V6 | V6__content_classification_constraints.sql | 유형 필수·초안 GENERAL 기본값·발행 snapshot 제약 |
| V7 | V7__restaurant_details.sql | 맛집 초안/발행 주소 1:1 |
| V8 | src/main/java/db/migration/h2/V8__page_block_identity.java | 안정 ID registry, 기존 블록 id/schemaVersion/variation 확장·발행본 보존 |
| V9 | V9__page_templates.sql | 공용 템플릿 blocks_json·revision·메타데이터 |
| V10 | V10__document_versions.sql | 대상별 version snapshot/media 관계와 baseline 완료 마커 |

미완성 과거 `V4__navigation`은 `workbench/navigation-draft/.../V4__navigation.java.txt`에 격리돼 있다. 현재 V4와 다른 파일이다. `.txt`를 Java로 복원하거나 Flyway 검색 경로에 넣지 않는다. 전체 IA seed를 migration에 끼워 넣지 않는다.

V10 schema 생성과 version baseline 데이터 캡처는 별도 작업이었다. 원본 baseline은 이미 완료됐다. 콘텐츠 초안 9개, 페이지 초안 2개·발행본 2개가 기준점이며 템플릿/콘텐츠 발행본은 당시 0개였다. 반복 baseline은 추가 0개이고 도입 이전의 실제 이력을 만들어내지 않는다.

## 변경 금지와 증거

V1~V10의 파일·checksum·이미 적용된 history를 수정하지 않는다. SQL whitespace 변경도 checksum에 영향을 줄 수 있다. V8의 Java checksum과 실제 실행 bytecode를 모두 보존한다. 숫자 checksum을 그대로 두고 Java 코드를 바꾸는 것도 승인되지 않은 migration 변경이다.

원본은 [5C-2 최종 기록](../../PHASE5C2_RETRY_RESULTS.md)의 checksum과 승인 RC manifest로 대조한다. 경고가 나온다는 이유로 Flyway repair/clean, history 행 수정, 이미 성공한 migration 강제 재실행을 하지 않는다. 지원 경고는 현재 남아 있고 migrate/validate 검증 성공과 별개다.

파일 runtime은 FileRuntimeConfiguration과 ClassificationMigrationConfiguration이 V10/receipt/옵션을 검사하고 `flyway.validate()`만 실행한다. 테스트용 메모리 DB의 migrate 분기는 운영 파일 경로와 다르다.

## 향후 V11 이상이 필요할 때

현재 동결을 해제하고 변경 범위를 승인받은 뒤 다음 순서로 진행한다.

1. 데이터 구조 변경이 정말 필요한지 판단한다. 기수·주제 사전 등록 같은 운영 DML은 schema migration과 분리한다.
2. V1~V10을 그대로 둔 채 새 V11 이상 migration 파일을 추가한다. 기존 ID/category/블록/publication/version·미디어 관계에 대한 보존 조건을 정의한다.
3. **새 schema를 수용하는 실행 guard·migration tool·receipt 승인 설계도 함께 준비한다.** 현재 V10 runtime은 목표 버전 10과 정확한 10개 migration을 검사한다. V11 파일만 넣거나 기존 V3→V10 도구의 숫자를 현장에서 바꾸어 실행할 수 없다.
4. 최신 정상 종료 V10 백업의 새 사본으로 migration을 적용한다. 적용 전후 전체 기존 데이터 fingerprint, ID/JSON 의미 보존, 새/기존 version 복구, 공개 경계, 미디어 보호를 확인한다.
5. 반복 validate/pending 0, 서버 쓰기/정상 종료/cold 독립 검사/재시작을 검증한다. H2 2.3.232 유지 시 writer workaround도 유지한다.
6. 적용 직전 최신 V10 DB·대응 runtime·source/config/checksum을 다시 백업하고 동일 사본 리허설을 통과한다.
7. 새 release·정확한 경로/hash·허용 migration manifest를 승인한 뒤 원본 적용한다. 실패 시 DB와 runtime을 직전 V10 묶음으로 함께 복구한다.

파일 schema를 직접 되돌리는 임의 역 SQL을 기본 rollback으로 삼지 않는다. 보관 중인 과거 version JSON과 publication을 최신 형식 편의로 덮어쓰지 않는다. 이미 등록된 운영 사전/활동 이력/미디어/계정까지 포함해 사본에서 검증한다.

## 운영 데이터와 혼동하지 않을 것

`COHORT_06`, REVIEW/FAQ topics와 허용 링크는 [운영 데이터 후보](10_OPERATING_DATA_AND_IA.md)이며 아직 미적용이다. V5 기술 baseline에 이 후보를 추가하거나 기존 category 명칭을 새 topic으로 자동 변환하지 않는다. 데이터 적용은 별도 승인된 diff·단일 트랜잭션·code 기준 idempotence·실제 ID 조회를 따른다.

근거: [현재 SQL migration 폴더](../../../src/main/resources/db/migration/h2), [V8 Java](../../../src/main/java/db/migration/h2/V8__page_block_identity.java), [정상 실행 전략](../../../src/main/java/egovframework/backoffice/mvp/config/ClassificationMigrationConfiguration.java), [V10 고정 검사](../../../src/main/java/egovframework/backoffice/mvp/operations/FileDatabaseSafety.java).
