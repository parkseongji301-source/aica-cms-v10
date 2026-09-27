# 5B-2A 운영 권한·저장 보호 결과

2026-09-27. 사용자 확정 정책을 적용했다. 승인/반려, 휴지통, 버전 보관·복구는 추가하지 않았다. 원본 V3에 migration하지 않았다.

확인 URL: http://127.0.0.1:8093/admin-next?view=manage

완료 실행 파일: `.cache/phase5b2a-test.jar` / DB: `.cache/phase5b2a-data/aica-phase5b2a-completed.mv.db` (V9).
기존 개발 로그인 1234 / 1234를 유지한다. 이 계정은 운영용 자격 증명이 아니다. 기존 5A 서버 8092는 정상 종료했고 이번 결과는 8093에서 확인한다.

## 1. 최종 권한

| 작업 | SUPPORTER | ADMIN | SUPER_ADMIN |
|---|---|---|---|
| 콘텐츠 조회 | 본인 | 전체 | 전체 |
| 콘텐츠 생성 | 본인 작성 | 가능 | 가능 |
| 제목·본문·분류·주소·첨부 수정 / 초안 저장 | 본인 | 전체 | 전체 |
| 콘텐츠 발행·재발행 | 차단 | 가능 | 가능 |
| 콘텐츠 공개 중단 | 차단 | 가능 | 가능 |
| 콘텐츠 영구 삭제 | 차단 | 차단 | 가능 |
| 기존 페이지 내용·블록 편집 / 초안 저장·발행 | 차단 | 가능 | 가능 |
| 기존 페이지 공개 중단 | 차단 | 기존 권한 유지 | 가능 |
| 새 페이지 생성 / slug 변경 | 차단 | 차단 | 가능 |
| 페이지 영구 삭제 | 차단 | 차단 | 가능, 참조 보호 적용 |
| 페이지·블록 사이트 구조 탐색 | 차단 | 가능 | 가능 |
| 메뉴·구조·기존 카테고리 관리 | 차단 | 차단 | 가능 |
| 공통 스타일·컴포넌트 설정 / 기본 정보·SNS·시스템 설정 | 차단 | 차단 | 가능 |
| 공용 템플릿 사용·저장·수정·비활성화 | 차단 | 차단 | 가능 |
| 계정 발급·역할 변경 / 활동 이력 조회 | 차단 | 차단 | 가능 |
| 미디어 업로드·조회·정보 편집 | 기존 본인 범위 | 기존 전체 범위 | 전체 |
| 미디어 영구 삭제 | 차단 | 차단 | 참조 없는 파일만 가능 |
| 승인·반려 / 휴지통 / 이전 버전 복구 | 미구현 | 미구현 | 미구현 |

SUPPORTER가 작성한 글은 ADMIN/SUPER_ADMIN이 기존 전체 콘텐츠 목록에서 확인한 뒤 발행한다. 검토 요청 상태나 새로운 역할을 추가하지 않았다.

## 2. 서버 권한과 화면 반영

- `AccessPolicy`에 발행, 구조 관리, 영구 삭제 책임을 분리했다. 기존 ALL_POSTS 권한으로 ADMIN이 기존 문서를 편집하는 범위는 유지한다.
- `PostService`에서 발행·공개 중단과 삭제를 각각 검사한다. `PageService`는 새 페이지와 slug 변경을 SUPER_ADMIN으로 제한하고 기존 페이지 편집·발행은 ADMIN도 가능하다.
- `SiteService`의 메뉴·카테고리·순서·공통 설정 변경을 SUPER_ADMIN으로 제한했다. 템플릿과 계정의 기존 SUPER_ADMIN 제한을 유지한다.
- Spring Security의 URL 검사와 서비스 검사를 함께 적용했다. React 권한 정보를 바꾸거나 직접 HTTP 요청을 보내도 서버에서 차단한다. 기존 세션·CSRF·계정 활성 검사를 유지한다.
- React 메뉴는 권한 없는 작업을 비활성화하고, 발행 진입·템플릿·미디어 삭제 버튼을 권한에 맞춘다. Thymeleaf도 발행/공개 중단/삭제/새 페이지/slug/전역 메뉴를 같은 기준으로 제한한다.
- 발행·공개 중단은 기존 Thymeleaf 업무 흐름을 재사용한다. 이번 단계에 별도 React 발행 API를 만들지 않았다.

근거: `security/AccessPolicy.java`, `SecurityConfiguration.java`, `cms/CmsAccess.java`, `PostService.java`, `PageService.java`, `SiteService.java`, `next/NextWorkspaceApi.java`, `frontend/src/navigation.ts`, `ContentEditor.tsx`, `ReadPanels.tsx`, `templates/fragments.html`, `editor-fragments.html`.

## 3. 공개 중단과 영구 삭제

공개 중단은 PRIVATE로 전환한다. 발행 snapshot은 남지만 공개 API에서 제외하며 다시 발행할 수 있다. 자동저장은 공개 상태를 바꾸지 않는다.

영구 삭제는 SUPER_ADMIN만 사용한다. 새 확인 화면 `/admin/{posts|pages|media}/{id}/delete-confirm`에서 제목, 현재 참조 위치, 삭제 영향을 표시하고 확인란을 요구한다. 콘텐츠·페이지 삭제 요청에는 해당 revision과 `confirmed=true`가 필요하다. React 미디어는 최신 사용처를 조회한 후 참조가 있으면 차단하고, 참조가 없으면 복구 불가 확인 후 삭제한다. 서비스가 삭제 시점에 참조를 다시 검사한다.

- 콘텐츠: 페이지 초안·발행본 및 활성/비활성 템플릿의 manual ID 참조와 현재 발행 조건 일치를 표시한다. query 일치는 실제 표시 개수 바깥의 잠재 영향도 포함한다. 삭제 후 manual ID 설정은 그대로 남으며 공개 결과에서 제외된다.
- 페이지: 메뉴 및 첫 화면 연결이 있으면 삭제를 차단한다.
- 미디어: 기존 참조 및 템플릿 참조가 있으면 삭제를 차단한다.
- 본문/CTA에 사람이 직접 입력한 URL, 외부 시스템 링크는 자동 추적 대상이 아니며 확인 화면에 한계를 표시한다.

콘텐츠의 기존 저장 방식인 `deleted_at` 표시와 발행본·분류·미디어 연결·맛집 상세 정리를 유지한다. **DB의 posts 행 자체와 제목/본문을 물리적으로 지우는 방식으로 바꾸지는 않았다.** UI 복구 수단이 없는 삭제이고 휴지통이 아니다. 법적 보존/완전 폐기 요구가 생기면 별도 정책이 필요하다. 원본 미디어 파일도 콘텐츠 삭제와 함께 자동 삭제하지 않는다.

추가: `DeletionController`, `DeletionImpactService`, `cms/delete-confirm.html`. 조건 영향 조회는 `PublishedPostQueryService`와 기존 publicPostCount를 재사용한다.

## 4. revision 및 충돌

기존 문서 수정·저장·발행·공개 중단·삭제에 expected revision이 필요하다. 누락은 거절하며 서버의 최신 revision으로 자동 대체하지 않는다. 오래된 값도 거절한다. 생성은 기존 revision이 없는 흐름을 유지한다.

기존 서비스 트랜잭션과 CMS 잠금 안에서 권한/현재 버전 검사 및 쓰기를 수행한다. 콘텐츠/페이지 공개 중단도 revision을 1 증가시키도록 보완했다. 중단 전 열린 편집창이 저장·재발행·삭제를 시도하면 충돌한다. 삭제와 일반 수정은 기존 revision 증가 규칙을 유지한다.

React 저장 충돌은 409, 누락/입력 오류는 400으로 처리하고 자동저장을 중지한다. Thymeleaf는 기존 400 폼 오류 또는 redirect+오류 메시지 응답 방식을 유지하지만 데이터 덮어쓰기는 허용하지 않는다. 오류 코드를 맞추기 위해 기존 폼 계약을 임의 교체하지 않았다.

미디어·메뉴·계정에는 기존 문서 revision 컬럼이 없다. 이번에 임의 DB 컬럼을 추가하지 않았으며 미디어는 잠금 안에서 현재 참조를 검사한다.

## 5. 자동저장·이동·닫기

- 콘텐츠와 페이지의 1.8초 초안 자동저장 및 명시적 초안 저장을 유지한다.
- React의 현재 활성 화면이면서 브라우저 문서가 보일 때만 자동저장한다. 숨겨진 방문 화면의 타이머를 취소한다. Thymeleaf도 브라우저가 숨겨지면 자동저장을 중지한다.
- 다른 문서/화면 이동과 뒤로가기·앞으로가기를 공통 guard로 검사한다. 같은 문서에서 보기만 전환하면 입력을 유지한다.
- 저장·업로드 중에는 이동을 승인해도 진행하지 않는다. 완료 후 이동을 다시 선택한다. 이미 시작한 정상 저장을 중간 취소해서 응답을 잃는 방식은 사용하지 않는다.
- 미저장 상태에서 이동을 선택하면 해당 브라우저 내 편집 인스턴스에 입력을 보관한다. 숨겨진 편집기는 저장하지 않고 다시 활성화하면 자동저장이 재개된다. 새로고침/브라우저 종료 후 미저장 입력까지 영구 복구하는 기능은 없다.
- TemplateEditor(관리 및 페이지 내 템플릿 저장 대화상자)에 이동·beforeunload·닫기 보호를 추가했다. 닫기에서는 ‘계속 편집 / 변경 버리고 닫기’ 확인을 표시한다. 템플릿은 자동저장하지 않는다.
- 브라우저 종료 경고는 beforeunload에 의존하므로 강제 종료·전원 차단까지 보장하지 않는다.

구현: `editorGuard.ts`, `useWorkspaceRoutes.ts`, `ContentEditor.tsx`, `PageEditor.tsx`, `PageTemplates.tsx`, `BlockDialog.tsx`, `writing.js`.

## 6. 템플릿 미디어 보호

`TemplateReferences`가 page_templates 원문을 검사한다. 활성 여부와 블록 visible 여부에 관계없이 imageId 및 rich bodyDoc의 이미지/파일 ID를 확인한다. 별도 참조 DB나 migration을 만들지 않았다.

미디어 삭제 서비스와 기존 `/api/admin/next/media/{id}/usage`에 이 결과를 연결했다. 템플릿 이름과 활성/비활성 상태가 표시된다. SUPER_ADMIN에게는 템플릿 관리 링크를 제공하며 다른 역할에 템플릿 관리 접근 권한을 주지는 않는다. 읽을 수 없는 템플릿 JSON은 삭제를 실패 처리한다.

새 API를 중복 생성하지 않고 사용처 API를 확장했다. 이전 버전의 미디어 보호는 버전 저장소가 생기는 5B-2B 범위다.

## 7. 공개 설정과 시간

메뉴·공통 스타일·사이트 정보·SNS는 기존 명시적 저장 요청을 유지하며 자동저장/새 발행 workflow를 추가하지 않았다. 관리 화면에서 편집을 입력만 해서는 저장하지 않는다. 공개 메뉴 API가 읽는 메뉴 등은 저장 후 반영된다. 공통 스타일·SNS·로고/사이트 정보의 실제 홈페이지 소비 계약은 아직 없으므로 홈페이지 반영 완료라고 간주하지 않는다.

시간 기준은 중앙 `backoffice.time-zone`/`BACKOFFICE_TIME_ZONE` (기본 Asia/Seoul)이다. Clock을 대시보드 날짜 계산에 사용하며 H2 dev 연결 세션 시간대를 맞춘다. LocalDateTime API 직렬화는 offset 포함 ISO 형식이다. Bootstrap의 timeZone과 서버 렌더링 메타 값을 사용해 React/Thymeleaf 저장 시각을 표시한다.

기존 timestamp는 UPDATE하지 않았다. 예를 들어 DB 2026-09-27 17:46:00은 API에서 2026-09-27T17:46:00+09:00으로 반환하며 원래 DB 값은 그대로다. PUBLIC_API_V1 계약 문서를 갱신했다. 시간대 설정을 바꾸면 기존 로컬 시각의 해석도 바뀌므로 원본 전환 시 실제 저장 환경 확인이 필요하다.

## 8. 검증 결과

| 범위 | 결과 |
|---|---|
| Java/Spring/실제 HTTP/DB 회귀 | 최종 보고서 합계 137 tests, 0 failures, 0 errors, 0 skipped |
| 신규 운영 정책 테스트 | 6개 통과: 3역할 권한, revision, 삭제 확인/참조, 템플릿 미디어, offset/원본 시간 보존 |
| V3/V6/V7/V8 실제 백업 사본 migration 회귀 | 전부 실행·통과. 원본에 적용하지 않음 |
| eGov 별도 호환 프로필 | 3개 통과, 위 137개에 포함 |
| 프런트엔드 | 39/39 통과, TypeScript 및 Vite build 성공 |
| 재시작 | 콘텐츠 18건, 페이지 1·65, 발행본, 메뉴·분류·권한, 템플릿 응답이 재시작 전후 같음 |
| 완료 사본 | 작업 전 모든 테이블 fingerprint, 페이지 초안/발행 JSON, Flyway history 동일 |
| 원본 V3 | SHA-256 동일, migration/데이터 변경 없음 |

첫 전체 회귀에서 과거 정책(ADMIN 페이지 생성/삭제, SUPPORTER 발행)을 사용하던 테스트 준비와 확인 없는 삭제 요청이 실패했다. 준비 작업을 SUPER_ADMIN으로 분리하고 실제 기존 페이지 편집은 ADMIN으로 유지했다. 정책 제한 assertion을 추가하고 해당 67개를 재실행해 통과했다. 실제 migration 사본 테스트는 최초 실행에서 모두 통과했으며 이미 migration한 파일을 반복 사용하지 않았다.

브라우저 검증:

1. 콘텐츠 97 수정 → 다른 화면 이동 확인 → 1.8초 이상 서버 revision/제목 불변 → 뒤로가기 입력 복원 → 활성 자동저장. 발행본 불변.
2. page 65 블록 수정 → page 1 이동 확인 → 숨김 자동저장 중지 → 뒤로가기 동일 block ID/입력 → 명시적 저장. 발행본 불변.
3. 템플릿 관리 입력 → 뒤로가기 확인/취소/이동 → 앞으로가기 입력 복원. 닫기 확인 및 버리기 후 서버 템플릿 불변. 페이지 내 템플릿 저장 대화상자도 확인.
4. 검증 프록시에서 PUT를 5초 지연 → 저장 중 이동 버튼으로도 이동 안 됨 → 저장 완료 후 이동 성공. 프록시는 종료했다.
5. ADMIN React/Thymeleaf에서 기존 페이지 편집/발행 노출, 새 페이지/slug/삭제/전역 설정 제한. SUPPORTER 본인 생성·수정·초안 저장 및 발행/중단/삭제 UI 부재.
6. 비활성 템플릿만 참조하는 새 파일의 사용처 표시 및 삭제 차단. SUPER_ADMIN 콘텐츠 삭제 영향 화면과 확인란 후 검증 글 삭제.

브라우저 기본 confirm은 도구 응답이 멈추는 현상이 있어 템플릿 닫기 확인을 화면 내 UI로 통일하고 재검증했다. 브라우저 종료 자체의 네이티브 경고는 강제 종료로 시험하지 않았다.

증거: `.cache/phase5b2a-evidence/`, `phase5b2a-hidden-content.json`, `phase5b2a-hidden-page.json`, `phase5b2a-content-autosave.json`, `phase5b2a-template-guard.json`, `phase5b2a-template-media.json`, `phase5b2a-busy-guard.json`, `phase5b2a-restart-result.json`, `phase5b2a-preservation.json`.
테스트: `phase5b2a-full-tests.log`, `phase5b2a-regression-recheck.log`, `phase5b2a-compatibility.log`, `phase5b2a-frontend-tests.log`, `target/surefire-reports`.

## 9. 보존과 복구

작업 전 기준점: `.cache/checkpoints/20260927-184549-phase5b2a/`.

- 작업 전 source.zip / manifest / working.patch / git 상태 + baseline-v9.mv.db + baseline-5a.jar.
- 시험 데이터 verified-v9.mv.db와 완료 데이터 completed-v9.mv.db를 구분한다.
- 완료 실행 파일 completed-5b2a.jar 및 완료 소스/체크섬을 함께 보관한다.
- 검증 계정, 변경 문구, 템플릿 전용 파일은 시험 사본에만 남긴다. 완료 사본은 기존 18개 콘텐츠·2개 템플릿 등의 작업 전 데이터를 유지한다.
- 원본 `.local-data/aica-local.mv.db` SHA-256: 55aed6f98ff52a00347257b94d5f0fc4e63f0ded657f27b39a75babb42735cbe.

스키마와 V1~V9 migration 소스는 변경하지 않았다. DB/JAR/시간대 설정을 쌍으로 복구한다. 절차는 해당 기준점의 RESTORE.md를 따른다. 원본 V3는 구버전 전용 실행 환경을 유지하며 신규 JAR로 실행하지 않는다.

## 10. 5B-2B 및 5C 전에 남은 문제

- 콘텐츠/페이지/템플릿의 버전 보관, 비교, ‘새 초안으로 복구’, 보관 기간·권한·용량 설계는 미구현이다. 공개본 즉시 되돌리기를 추가하지 않는다.
- 버전이 참조하는 미디어의 삭제 보호를 버전 저장과 함께 설계해야 한다. 현재 보호는 현재 초안·발행본 참조와 현재 활성/비활성 템플릿 기준이다.
- 본문/CTA 직접 URL·외부 링크 참조는 완전 추적하지 못한다. 삭제 영향 안내에 포함하지만 자동 무결성 보장은 아니다.
- 메뉴·설정·미디어 메타데이터는 문서 revision과 같은 다중 편집 충돌 제어가 없다. 이번에 DB 구조를 확대하지 않았다.
- H2 2.3.232/Flyway 지원 범위 경고, 이전 파일 DB 연결 수명 문제를 해결됐다고 간주하지 않는다. 이번 정상 종료·재시작 통과는 위험 해소 선언이 아니다.
- 실제 홈페이지는 없다. 공개 API 계약만 완료했으며 실제 홈페이지 renderer/E2E·CORS·배포·공통 설정 연결은 후속이다.
- 원본 V3와 검증 V9 차이 및 JSON/API/권한 계약 변화가 존재한다. 같은 V9라도 구버전 JAR로 복구하면 이전 SUPPORTER 발행 권한까지 되돌아가므로 복구 후 권한 재확인이 필요하다.
- 기존 timestamp가 모두 한국 시간으로 저장됐는지 운영 전 확인이 필요하다. 이번에는 변환 migration을 하지 않았다.
- 빌드의 기존 500KB 초과 번들 경고는 남아 있다. 이번 기능과 별개로 성능 점검 시 다룬다.
