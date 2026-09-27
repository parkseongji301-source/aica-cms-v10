# AICA 5B-1 운영 정책 검토안

작성일: 2026-09-27  
상태: **검토안 — 정책 확정·구현·원본 적용을 의미하지 않음**

## 검토 범위와 기준

현재 작업 소스와 5A까지의 결과·API 계약, 통합 지시문의 운영 방향을 대조했다. 이번 작업은 읽기 분석과 이 문서 작성뿐이다. 애플리케이션 코드, DB, migration, 권한, 서버 실행 상태를 변경하지 않았으며 테스트·migration을 새로 실행하지 않았다. 기존 수정·미추적 작업도 그대로 둔다.

5A는 **공개 API와 연결 계약 완료**로 기록한다. 공개 홈페이지가 없으므로 실제 홈페이지 렌더링 E2E는 미완료이며 홈페이지 확보 후 최종 검수 대상이다. 현재 확인하는 기능은 V9 검증 환경용 최신 코드다. V3 원본에서 신규 기능이 작동한다고 해석해서는 안 된다. [S27](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/docs/PHASE5A_RESULTS.md) [S28](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/docs/PUBLIC_API_V1.md)

문서의 표기:
- **현재 허용**: 현재 서버 권한 검사와 서비스가 허용한다. 최종 운영 정책 승인과는 다르다.
- **현재 차단**: 해당 기능은 있지만 해당 역할에 제한되어 있다.
- **미구현**: 기능 자체가 없으므로 역할을 부여할 대상도 없다.
- **정책 미확정**: 향후 운영 결정을 뜻한다. 현재 허용/차단 사실을 대신하는 표기가 아니다.
- **추천안**: 채택할 경우 후속 구현이 필요한 제안이다. 이번에 적용하지 않는다.

## 1. 역할별 현재 권한

아래의 콘텐츠 조회는 관리자 초안·관리 목록 접근이다. 익명 공개 API에서 발행 콘텐츠를 읽는 권한과 구분한다. 정상 로그인, 활성 계정, 필요한 비밀번호 변경, 세션 유효성, CSRF 및 대상별 검증을 통과해야 한다.

| 작업 | SUPER_ADMIN | ADMIN | SUPPORTER | 현재 범위 / 정책 상태 |
|---|---|---|---|---|
| 콘텐츠 조회 | 현재 허용: 전체 | 현재 허용: 전체 | 현재 허용: 본인 / 타인 현재 차단 | 목록과 단건 조회 모두 소유권 검사 |
| 본인 콘텐츠 수정 | 현재 허용 | 현재 허용 | 현재 허용 | 발행된 글도 초안 수정 가능 |
| 타인 콘텐츠 수정 | 현재 허용 | 현재 허용 | 현재 차단 | 작성자 ID 기준 |
| 콘텐츠 생성 | 현재 허용 | 현재 허용 | 현재 허용 | 공통 posts 및 생성 서비스 |
| 콘텐츠 초안 저장 | 현재 허용: 전체 | 현재 허용: 전체 | 현재 허용: 본인 | 수동·자동 저장 동일 서비스 |
| 콘텐츠 발행·재발행 | 현재 허용: 전체 | 현재 허용: 전체 | **현재 허용: 본인** | SUPPORTER 운영 발행 범위는 정책 미확정 |
| 콘텐츠 공개 중단 | 현재 허용: 전체 | 현재 허용: 전체 | **현재 허용: 본인** | PRIVATE 전환; 최종 역할 범위 미확정 |
| 콘텐츠 삭제 | 현재 허용: 전체 | 현재 허용: 전체 | **현재 허용: 본인** | 발행 여부별 별도 역할 제한 없음; 복구 가능한 휴지통 아님 |
| 기존 페이지 내용 편집 | 현재 허용 | 현재 허용 | 현재 차단 | 작성자와 무관한 사이트 관리 권한 |
| 페이지 발행·재발행 | 현재 허용 | 현재 허용 | 현재 차단 | 기존 Thymeleaf 발행 동작 사용 |
| 페이지 블록 구성 변경 | 현재 허용 | 현재 허용 | 현재 차단 | 추가·복제·삭제·순서·표시·Variation·POSTS 설정 |
| 새 페이지·slug 생성/수정 | 현재 허용 | 현재 허용 | 현재 차단 | 기존 화면에서 제공; 최종 생성 범위는 정책 미확정 |
| 페이지 공개 중단·삭제 | 현재 허용 | 현재 허용 | 현재 차단 | 삭제 참조 검사 있음; 정책 범위 별도 결정 |
| 활성 템플릿 불러오기 | 현재 허용 | 현재 차단 | 현재 차단 | 4D에서 “이번에는 SUPER_ADMIN만 사용”으로 결정한 범위 |
| 공용 템플릿 저장·수정·비활성화 | 현재 허용 | 현재 차단 | 현재 차단 | 실제 삭제 API 없음 |
| 메뉴 관리 | 현재 허용 | 현재 허용 | 현재 차단 | 연결·노출·정렬·추가·삭제 |
| 공통 스타일/공통 영역 설정 | 현재 허용 | 현재 허용 | 현재 차단 | 색상·모서리·로고·헤더/푸터 문구 |
| 등록 컴포넌트/Variation 목록 확인 | 현재 허용 | 현재 허용 | 현재 차단 | 등록 코드 조회; 임의 코드 제작/등록 및 활성 토글은 미구현 |
| 계정 발급 | 현재 허용 | 현재 차단 | 현재 차단 | 새 계정 대상 역할은 ADMIN/SUPPORTER |
| 계정의 역할 변경 | 현재 허용 | 현재 차단 | 현재 차단 | 타인 계정만; 기존 계정의 SUPER_ADMIN 승격은 가능 |
| 역할별 권한 규칙 편집 | 미구현 | 미구현 | 미구현 | 역할/권한 화면은 규칙 조회, 계정별 역할 배정과 다름 |
| 활동 이력 조회 | 현재 허용 | 현재 차단 | 현재 차단 | 임시저장 로그는 선택해서 포함 |
| 콘텐츠/페이지/템플릿 이전 버전 복구 | 미구현 | 미구현 | 미구현 | revision이나 최신 발행본이 있어도 이전 버전 복구 기능은 없음 |

근거: 역할 매핑·소유권 [S01](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/security/AccessPolicy.java:14), URL 보안 경계 [S02](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/security/SecurityConfiguration.java:24), 페이지/사이트 관리자 검사 [S03](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/CmsAccess.java:12), 콘텐츠 저장·발행·삭제·비공개 [S04](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/post/PostService.java:107), 페이지 저장 [S05](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/PageService.java:85), 템플릿의 사용/관리 검사 [S06](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/PageTemplateService.java:26), 계정 서비스 [S07](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/account/AccountService.java:44), 실제 React 메뉴 기능 [S23](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/ReadPanels.tsx:52) [S29](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/EditPanels.tsx:74).

추가로 계정 역할 변경·비활성화는 본인에게 실행할 수 없고, 마지막 활성 SUPER_ADMIN을 강등/비활성화하지 못하도록 보호한다. 역할·활성 상태·비밀번호 변경 시 인증 버전이 바뀌어 기존 세션의 권한을 다시 검사한다. 새 SUPER_ADMIN 계정 직접 발급과 기존 계정 승격은 현재 서로 다른 처리다. [S07](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/account/AccountService.java:44)

## 2. 직접 발행과 승인형 발행

현재 콘텐츠 공개 상태는 DRAFT / PUBLISHED / PRIVATE다. 이미 발행한 글의 초안만 수정하면 PUBLISHED를 유지하면서 revision과 publishedRevision의 차이로 미반영 수정을 구분한다. 검토 요청·승인·반려 상태나 검토자 권한은 없다. SUPPORTER도 본인 글에 action=publish를 실행할 수 있다. [S04](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/post/PostService.java:107)

| 비교 | A. 직접 발행 | B. 승인형 발행 |
|---|---|---|
| 현재 재사용 | 저장·발행·공개 중단과 기존 snapshot 흐름 그대로 | 초안 저장 및 최종 발행 처리만 재사용 |
| 추가 상태 | 현재 상태로 가능. 역할을 좁히는 경우에도 승인 상태는 불필요 | 검토 중/반려 등 별도 검토 상태, 요청자·검토자·대상 revision 필요 |
| API | 기존 발행 API 재사용. 역할을 변경하면 공통 서비스 검사 보강 | 검토 요청·취소·반려·승인/발행 명령 추가, 중복 처리·충돌 방지 |
| UI | 저장 상태, 미리보기, 발행 확인·결과를 명확히 표시 | 검토 대기 목록, 요청/취소, 의견, 승인/반려, 결과 안내 |
| 권한 | 편집과 발행 권한을 분리할지 결정 | 요청자/검토자/발행자 범위 및 자기 승인 가능 여부 결정 |
| 활동 기록 | 현재 발행/비공개 기록에 대상 revision·결과를 보강 | 요청·취소·반려 사유·승인·실제 발행 revision까지 기록 |
| 추가 주의 | 담당자가 직접 확인 후 발행할 책임 필요 | 검토 중 수정으로 승인 대상이 바뀌면 요청 무효화 또는 재검토 필요 |

**추천: A의 단순한 직접 발행 구조를 유지하되, 발행 책임은 SUPER_ADMIN·ADMIN에게 두고 SUPPORTER는 본인 콘텐츠 초안 작성·수정을 담당하는 안.**

ADMIN이 기존 전체 콘텐츠 목록에서 글을 확인하고 직접 발행하면 된다. 검토 요청 상태·승인 대기함·반려 기능은 이 기본안에 넣지 않는다. 다만 이 방식에는 “검토를 요청했다”는 시스템 기록이 없으므로 글이 많아지거나 심사 증빙이 필요해지면 B를 별도로 검토한다. 실제 운영 인원·물량 자료가 없으므로 작은 운영팀과 현재 세 역할을 전제로 한 추천이다.

이는 **현재 SUPPORTER 직접 발행 허용을 좁히는 신규 제안**이다. 채택 시 UI 숨김만으로 처리하지 않고 PostService의 발행·공개 중단 검사와 모든 기존/React 요청을 함께 바꿔야 한다. 현재 저장·발행이 같은 소유권 규칙을 사용하므로 권한 검사 지점을 분리해야 한다. 이번에는 변경하지 않는다.

## 3. 자동저장 현황

| 화면 | 현재 자동저장 | 수동 저장 / 발행 | 충돌·실패·이탈 |
|---|---|---|---|
| Thymeleaf 콘텐츠 | 입력이 멈춘 뒤 1.8초에 초안 저장 | 초안 저장·발행 버튼, Ctrl/Cmd+S | revision 전달, 충돌 시 중단, 오류 표시, 미저장/업로드/저장 중 beforeunload 경고 |
| Thymeleaf 페이지 | 같은 writing.js 사용, 1.8초 | 같은 저장/발행 방식 | 같은 보호. 새 페이지에서도 유효한 제목 등이 입력되면 자동저장이 최초 행을 만들 수 있음 |
| React ContentEditor | 입력이 멈춘 뒤 1.8초; 분류·주소도 대상 | 초안 저장·Ctrl/Cmd+S, 발행은 기존 화면 진입 | 409 및 인증·권한·대상 오류 시 차단, 실패 입력 반복 재시도 억제; beforeunload 있음 |
| React PageEditor | 입력이 멈춘 뒤 1.8초; 블록 구성 전체 | 초안 저장·Ctrl/Cmd+S, 발행은 기존 화면 진입 | revision 충돌/인증 오류 차단, beforeunload 및 SPA 다른 페이지 이동 확인 있음 |
| 공용 템플릿 자체 | **없음** | 저장·설정 저장·기존 구성 교체 저장을 명시적으로 실행 | revision 확인 및 오류 표시 있음; 템플릿 폼의 미저장 닫기/이동/종료 보호는 없음 |

근거: [S11](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/static/js/writing.js:16) [S12](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/ContentEditor.tsx:35) [S13](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/PageEditor.tsx:33) [S14](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/PageTemplates.tsx:17) [S15](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/useWorkspaceRoutes.ts:10).

현재 자동저장은 정해진 시간마다 무조건 쓰는 방식이 아니다. 유효한 변경이 있고 입력이 1.8초 멈췄을 때 실행한다. 업로드나 저장 중에는 기다리고, 분류/주소 오류·빈 필수값 등은 저장을 막는다. 공개본은 바꾸지 않는다. 저장 요청 중 새 입력이 생기면 성공 응답이 그 새 입력을 덮어쓰지 않도록 처리한다.

React 실패 입력은 같은 값으로 무한 재시도하지 않는다. 실패 표시를 확인하고 다시 저장하거나 오류 원인을 해결해야 한다. 충돌은 자동 병합하지 않고 현재 입력을 남긴 채 재조회가 필요하다고 안내한다. Thymeleaf의 충돌 인식은 오류 문구에 의존하는 부분이 있어 React의 HTTP 409 계약과 완전히 같지는 않다.

탐색과 종료의 차이:
- 관리/구조 보기 전환과 방문했던 React 화면 전환은 편집기 인스턴스를 유지한다. URL로 미저장 본문을 저장하지 않는다.
- PageEditor는 다른 페이지 이동 전 확인을 요청한다. 이동을 허용해도 입력을 버리지 않고 해당 편집기를 숨겨 유지한다.
- **ContentEditor에는 PageEditor와 같은 SPA guard 등록이 없다.** 화면은 유지되지만 확인 UX가 일치하지 않는다.
- 숨겨진 ContentEditor/PageEditor도 자동저장 효과에 active 제한이 없어 저장을 계속할 수 있다. 사용자가 다른 화면에 있다는 것이 저장 중단을 뜻하지 않는다.
- 콘텐츠의 “발행·상세 관리” 링크는 페이지 편집기의 발행 링크처럼 미저장 상태에서 비활성화되지 않는다. 다른 탭에서 마지막 저장본을 볼 수 있으므로 발행 진입 전 저장 완료 확인을 통일할 필요가 있다.
- beforeunload는 브라우저가 허용하는 범위의 경고다. 브라우저 강제 종료·충돌 시 저장 완료를 보장하지 않는다. 미저장 입력의 영구 로컬 복구나 종료 시 저장 완료 기능은 없다.
- 템플릿 적용은 페이지 편집 상태를 바꾸므로 이후 PageEditor 자동저장이 페이지 **초안**에 적용된다. 템플릿 자체 저장이나 페이지 발행과는 다르다.

**추천:** 글·페이지의 1.8초 자동저장과 명시적 초안 저장을 함께 유지한다. 자동 발행은 두지 않는다. 미저장/저장 중/저장 완료/실패/충돌 표시를 통일하고, 콘텐츠도 기존 페이지 이동 확인 규칙을 공유한다. 템플릿은 영향 범위가 넓으므로 자동저장을 넣기보다 명시적 저장과 닫기 전 미저장 확인을 제공한다. 브라우저 종료 직전 자동 저장을 보장한다고 안내하지 않는다.

현재 서버 충돌 보호의 빈틈도 있다. CmsRules.revision은 expected가 null이면 검사하지 않는다. React 저장 및 기존 폼의 JSON 저장에는 revision이 필요하지만, 일반 콘텐츠 폼 요청은 신규 분류/주소가 없으면 revision 누락을 서비스가 거절하지 않는다. 콘텐츠 삭제에는 revision 인자가 없고, 콘텐츠/페이지 공개 중단 및 페이지 삭제는 revision이 선택값이다. 공개 중단은 revision을 증가시키지 않는다. 정상 UI가 토큰을 보낸다는 사실과 모든 서버 명령에서 충돌을 보장한다는 것은 다르다. [S20](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/CmsRules.java:26) [S21](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/post/PostController.java:49) [S05](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/PageService.java:85)

5B-2에서는 기존 저장뿐 아니라 공개 중단·삭제 등 상태 변경도 예상 revision을 반드시 확인하고 필요한 버전 증가를 일관되게 처리하는 것을 후보로 둔다. 특히 오래 열린 발행 화면이 다른 운영자의 공개 중단을 모르고 다시 발행하지 못하게 해야 한다.

## 4. 페이지 생성·URL 관리

| 작업 | 현재 화면·처리 | 현재 역할 |
|---|---|---|
| 기존 내용/블록 수정 | React PageEditor와 Thymeleaf 편집기, 동일 PageService | SUPER_ADMIN / ADMIN |
| 새 페이지 생성 | /admin/pages → 새 페이지 → /admin/pages/new | SUPER_ADMIN / ADMIN |
| React에서 생성 진입 | 전체 페이지 현황의 “발행·페이지 추가 등 관리”가 기존 화면으로 연결 | SUPER_ADMIN / ADMIN |
| slug 생성·수정 | 기존 페이지 폼; 비우고 생성하면 page-UUID 자동 생성, 형식·중복 검사 | SUPER_ADMIN / ADMIN |
| 메뉴 연결 | 메뉴 관리에서 별도 PAGE 대상 지정. 새 페이지 생성만으로 자동 메뉴 생성하지 않음 | SUPER_ADMIN / ADMIN |
| 공개 중단 | 기존 관리 화면 → PRIVATE, 발행 snapshot 보존 | SUPER_ADMIN / ADMIN |
| 삭제 | 메뉴/homePageId 참조 검사 후 실제 페이지 삭제 | SUPER_ADMIN / ADMIN |

React 페이지 저장 API는 기존 페이지용이며 기존 slug를 사용한다. React 자유 페이지/URL 생성기를 새로 제공하는 구조는 아니다. [S05](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/PageService.java:85) [S23](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/ReadPanels.tsx:52)

**추천:** SUPER_ADMIN에게 새 페이지 생성·slug 변경·페이지 삭제 책임을 두고, ADMIN에게 기존 페이지의 내용·블록 편집·발행 및 기존 메뉴 관리를 유지한다. 템플릿 사용은 앞서 확정한 SUPER_ADMIN 전용을 유지한다. 블록 편집 능력을 새 URL 생성 권한으로 자동 확대하지 않는다.

대안은 현재처럼 ADMIN도 생성하게 두는 것으로, 최고관리자 의존을 줄이지만 URL 변경·중복 페이지·불필요한 초안 생성 책임까지 ADMIN에게 맡기게 된다. 어느 안을 택하든 페이지를 자동 생성하는 전체 IA 등록 기능은 추가하지 않는다. 기존 URL 변경 시 리다이렉트·유입 링크 영향은 현재 미구현이므로 별도 확인이 필요하다.

## 5. 공개 중단과 삭제

| 대상 | 현재 공개 중단/비활성 | 현재 삭제 | 참조 보호와 빈틈 |
|---|---|---|---|
| 콘텐츠 | PRIVATE 전환, 초안·최신 발행 snapshot 유지. 공개 API에서는 제외 | posts에 deleted_at 표시하지만 category_id를 비우고 초안/발행 미디어 연결·발행본·기수/주제·맛집 상세를 제거 | manual postIds는 그대로 남고 관리자에 삭제 상태, 공개 조회에서 제외. 삭제 전 페이지/템플릿 사용처 차단은 없음 |
| 페이지 | PRIVATE 전환, 최신 snapshot 유지 | site_pages 실제 삭제, 연결된 발행본/미디어 관계도 삭제 | 메뉴·첫 화면 참조 시 삭제 차단. 공개 중단은 이 보호와 별개. CTA/본문의 URL 문자열까지 추적하지 않음 |
| 미디어 | 독립 비활성 상태 없음. 현재 공개 문서 참조가 사라지면 공개 접근 차단 | DB 미디어 행과 파일 BLOB 실제 삭제 | 콘텐츠·페이지·로고 참조 시 차단. 템플릿 참조 누락 |
| 메뉴 | visible=false로 공개 메뉴에서 숨김 | 메뉴 행 실제 삭제 | 연결 콘텐츠/페이지는 삭제하지 않음. 메뉴를 숨겨도 대상의 공개 URL 자체를 비공개로 만들지 않음 |
| 공용 템플릿 | active=false, 이후 불러오기 제한 | 삭제 API 없음 | 기존에 복사한 페이지는 유지. 비활성 템플릿 내부 자원 보호도 필요 |
| 기존 category | 비활성 상태 없음 | 미사용 분류 실제 삭제 | 콘텐츠 초안/발행본·메뉴·페이지 초안/발행본 검사. 템플릿의 category 참조는 검사하지 않음 |
| 콘텐츠 유형·기수·주제 | active 컬럼/유형별 허용 관계가 있음 | 운영자용 삭제/비활성 관리 UI·명령 API는 **미구현** | 실제 할당에는 FK가 있지만 페이지/템플릿 JSON query 참조는 FK 보호 대상 아님 |

근거: [S04](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/post/PostService.java:107) [S05](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/PageService.java:85) [S08](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/SiteService.java:32) [S09](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/MediaService.java:95) [S10](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/mapper/CmsMapper.xml:36) [S06](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/PageTemplateService.java:26) [S22](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/db/migration/h2/V4__content_classification_schema.sql:1) [S30](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/next/NextClassificationApi.java:16).

현재 콘텐츠의 soft delete는 **완전한 휴지통이 아니다.** posts 본문 행이 남더라도 관계·공개본·주소 등을 지우므로 deleted_at만 되돌려 원상 복구할 수 없다. 페이지·미디어도 운영자가 되돌리는 삭제 취소 기능이 없다.

콘텐츠를 공개 중단하면 manual 설정은 남지만 그 콘텐츠는 공개 결과에서 제외된다. 다른 공개 가능한 콘텐츠는 계속 표시된다. 분류/조건 방식에서도 해당 콘텐츠가 빠진다. 이는 참조 데이터의 자동 삭제와 다르다.

**추천:**
- 일시적으로 내리는 작업은 공개 중단을 기본으로 제공한다. 메뉴 숨김과 대상 비공개는 구분한다.
- 콘텐츠 삭제를 운영자용으로 유지하려면 전체 관계를 보존하는 휴지통·복구 정책을 먼저 마련한다. 현재 기능을 복구 가능한 삭제라고 표시하지 않는다.
- SUPPORTER는 본인 미발행 초안 삭제까지만, 발행 경험이 있는 콘텐츠의 공개 중단/삭제는 SUPER_ADMIN·ADMIN이 담당하는 안을 권한 결정 후보로 둔다.
- 페이지 삭제는 SUPER_ADMIN 중심, 참조 사용처 확인 후 수행한다. 일상 운영은 비공개 전환을 우선한다.
- 공용 템플릿과 신규 분류 사전은 비활성화를 우선하고, 참조 중인 사전/미디어의 실제 삭제는 차단한다.
- 참조 사용처에는 초안/발행본/템플릿과 향후 보존 이력을 포함한다. 템플릿의 query/manual/category 설정을 자동 제거해서 오류를 숨기지 않는다.
- 공개 중단 시 첫 화면이나 메뉴 영향은 확인시켜야 한다. 긴급 공개 중단을 무조건 막을지는 별도 정책 결정이다.

## 6. 이력과 복구

세 가지는 현재 다른 기능이다.

| 구현 | 지금 저장하는 것 | 할 수 없는 것 |
|---|---|---|
| 활동 이력 | 작업자·작업명·대상·간단한 설명·시각 | 당시 전체 문서, 전후 데이터 비교, 원문 복구 |
| revision | 현재 저장 버전 및 충돌 검사 숫자 | 과거 revision의 내용 조회 |
| 발행 snapshot | 현재 최신 공개용 내용·분류·주소·페이지 블록 | 여러 과거 발행 버전 선택 |
| DB/JAR 기준점 백업 | 개발·검증 시점 전체 환경 복구 자료 | 운영자 화면에서 특정 글 하나만 복구 |

콘텐츠와 페이지는 재발행할 때 기존 publication을 지우고 최신 snapshot을 넣는다. 과거 발행본의 연속 이력은 없다. 공용 템플릿도 현재 한 행을 갱신하며 revision이 늘 뿐이다. 활동 로그의 초안 기록은 기본 목록에서 숨겨질 수 있지만 체크하면 조회할 수 있다. [S04](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/post/PostService.java:107) [S05](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/PageService.java:85) [S10](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/mapper/CmsMapper.xml:36) [S16](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/mapper/PageTemplateMapper.xml:10) [S23](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/ReadPanels.tsx:52)

| 대상 | 현재 가능한 범위 | 필요한 복구 기능 |
|---|---|---|
| 콘텐츠 | 현재 초안과 현재 공개본 분리, 비공개 시 최신 snapshot은 DB에 보존 | 불변 버전 저장, 버전 목록/비교, 제목·본문·분류·미디어·주소의 새 초안 복구 |
| 페이지 | 현재 초안과 최신 발행본 비교 재료, 안정 block ID | 버전별 전체 구성·순서·표시·Variation·POSTS 설정 보관, 새 초안 복구 및 ID 수명 검증 |
| 공용 템플릿 | 현재 구성/수정자/시각/revision, 수동 갱신 및 비활성화 | **전용 버전 저장·비교·복원 UI/API 모두 미구현** |

복구 추천 흐름은 다음과 같다.

이전 버전 선택 → 현재 내용과 차이·참조 상태 확인 → 현재 revision 검사 → **이전 내용을 새 초안으로 저장** → 미리보기 → 별도 재발행.

이전 버전을 눌렀다는 이유로 현재 발행본을 변경하지 않는다. 복구 권한과 발행 권한도 분리한다. 복구 이벤트에는 출처 버전·실행자·시각·새 revision을 남긴다. 복구 당시 없는 미디어나 분류는 경고하고 해결하게 하며 추측해서 다른 항목에 연결하지 않는다.

페이지 복구에는 4A의 삭제 ID 재사용 금지 규칙이 추가로 걸린다. 현재 초안에서 살아 있는 동일 ID는 유지할 수 있지만, 이미 폐기한 블록을 복원할 때 과거 ID를 재활성화하면 안 된다. 재도입되는 블록에는 새 ID를 발급하고 복구 출처를 별도로 기록하는 설계가 필요하다. 문구·순서로 블록 대응을 추측하지 않는다. [S17](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/PageBlockService.java:41)

공용 템플릿에는 현재 발행 개념이 없다. 따라서 과거 버전을 우선 **편집 후보**로 불러와 확인하고 명시적으로 템플릿 저장하도록 권한다. 새 페이지 불러오기에만 영향을 주고 기존 적용 페이지는 그대로 둔다. 템플릿을 위한 별도 승인·발행 상태를 이번 정책안에서 자동 추가하지 않는다.

이력 보관 시점과 기간/개수는 결정이 필요하다. 1.8초마다 생기는 모든 자동저장을 영구 보관한다고 약속하지 않는다. 수동 저장·발행·복구 등 의미 있는 지점은 보존하고 자동저장 이력은 묶어서 보관하는 방식을 추천한다. 기존에 사라진 버전은 향후 이력 기능으로 되살릴 수 없다.

삭제 복구와 이전 내용 복구는 별도 범위다. 특히 현재 삭제가 지운 연결·발행본은 대응 백업이 없으면 복원할 근거가 없다.

## 7. 미디어 참조 보호

| 참조 위치 | 현재 삭제 보호 | 근거/제한 |
|---|---|---|
| 콘텐츠 초안 | 있음 | post_media 참조; 리치본문 미디어 ID도 저장 시 동기화 |
| 콘텐츠 최신 발행본 | 있음 | post_publication_media; 초안에서 제거해도 발행본 사용 중이면 삭제 차단 |
| 페이지 초안 | 있음 | page_media의 published=false, 숨김 블록 포함 |
| 페이지 최신 발행본 | 있음, 범위 제한 | published=true에는 **표시 블록**의 미디어를 등록 |
| 공용 템플릿 | **없음** | blocks_json 참조를 mediaUsage가 검사하지 않음 |
| 공용 로고 설정 | 있음 | logoId 설정 참조로 삭제 차단 |
| 과거 버전 | 대상 미구현 | 이력 기능을 추가할 때 함께 보호 설계 필요 |

[S09](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/MediaService.java:95) [S10](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/mapper/CmsMapper.xml:36) [S05](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/PageService.java:85)

페이지 발행 JSON에 남아 있는 숨김 블록의 미디어는 발행 미디어 관계에 등록되지 않는다. 현재 초안에도 남아 있으면 초안 참조가 보호하지만, 초안에서 제거하고 발행 JSON에 숨김으로만 남으면 보호가 사라질 수 있다. 공개 화면 노출 문제와 별개로 과거 구성의 재편집/복구 관점에서 보완 대상이다.

**5B-2 우선 후보:** 활성·비활성 공용 템플릿의 imageId 및 리치본문 미디어 참조를 삭제 검사와 사용처 목록에 포함한다. 현재 페이지/콘텐츠 참조 검사와 동일한 삭제 트랜잭션 경계에서 처리한다. 이후 이력 보관을 도입할 때 이력의 파일도 보관 기간 동안 보호한다.

보호와 공개 허용은 다르다. 템플릿·초안·로고 설정이 파일을 참조한다는 이유만으로 익명 다운로드를 허용하면 안 된다. 5A 공개 미디어는 현재 공개 상태인 문서의 발행 참조가 있어야 접근할 수 있다. [S18](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/publicapi/PublicSiteApi.java:14) [S19](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/publicapi/PublicSiteService.java:37)

## 8. 공개 설정의 반영 시점

| 대상 | 현재 저장/발행 처리 | 현재 공개 연결 | 추천 |
|---|---|---|---|
| 콘텐츠 본문·분류·맛집 주소 | 초안과 publication 분리 | 재발행 후 공개 API 변경 | snapshot 유지 |
| 페이지 내용·block ID/Variation/visible/POSTS 설정 | 초안과 publication 분리 | 재발행 후 공개 API 변경 | snapshot 유지 |
| 메뉴 연결·순서·visible·외부 링크 메뉴 | 저장 즉시 site_menus 변경, 별도 발행 없음 | 다음 공개 메뉴 조회에 반영. PAGE 이름/slug는 페이지 발행본 사용 | 명시적 저장 즉시 반영, 영향 안내 및 변경 이력 |
| 공용 사이트 정보·첫 화면·스타일·헤더/푸터 | 설정 저장 즉시 DB 변경, 별도 발행 없음 | 현재 공개 API에 사이트 설정 조회 계약 없음 | 저장 즉시 반영 설정으로 유지하되 공개 필드 allowlist 계약 필요 |
| 로고 | logoId 즉시 변경, 별도 snapshot 없음 | 로고 전용 공개 계약 없음. 로고 설정만으로 공개 파일 접근을 허용하지 않음 | 공용 설정으로 관리, 실제 연결 시 로고의 공개 허용 규칙을 명시 |
| SNS/외부 링크 목록 | site_links 즉시 저장·정렬·삭제 | 현재 별도 공개 링크 API 없음 | 명시적 저장 즉시 반영, 공개 계약 추가 시 안전한 필드만 |
| 공개 미디어 이름/alt | 미디어 공통 메타데이터를 즉시 수정 | 다음 공개 DTO/파일명 응답에 반영 | 공통 메타데이터 즉시 반영 가능. 여러 문서 영향 안내 |
| 템플릿 | 명시적 저장 즉시 원본 갱신 | 기존 페이지에는 전파 없음 | 현재 복사 원칙 유지 |

[S08](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/cms/SiteService.java:32) [S10](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/mapper/CmsMapper.xml:36) [S18](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/publicapi/PublicSiteApi.java:14) [S19](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/publicapi/PublicSiteService.java:37) [S29](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/EditPanels.tsx:74)

“설정이 DB에 즉시 저장됨”과 “이미 실제 홈페이지가 읽고 있음”을 혼동하면 안 된다. 실제 홈페이지도 없고 사이트 설정/SNS를 공개로 제공하는 현재 API도 없다. 이 연결은 남아 있다. 관리자 설정 API 전체를 공개에 넘기면 안 된다.

공통 설정 전부에 페이지처럼 초안/발행 단계를 넣는 것은 지금 규모에서는 권하지 않는다. 메뉴·연락처·SNS·공통 스타일 등은 명시적 저장, 영향 범위 표시, 변경 기록과 되돌릴 근거를 갖추는 정도를 기본안으로 둔다. 향후 여러 변경을 한 번에 공개해야 하는 운영 요구가 생기면 묶음 발행을 별도로 검토한다.

공통 media.alt와 본문/블록에 저장된 개별 대체 텍스트·캡션은 구분한다. 문서 안의 표현은 해당 문서 snapshot을 따른다. 파일 바이트 자체를 같은 ID로 교체하는 기능은 현재 없으며, 새 업로드 ID로 문서에 연결 후 발행하는 방식을 유지하는 것이 안전하다.

공개 중단은 새 조회를 차단하는 기능이다. 이미 다운로드한 파일이나 방문자 화면에 그려진 내용을 회수하는 기능은 아니다.

## 9. 캐시와 시간대

### 캐시의 현재 범위

- 공개 JSON/파일과 관리자 API는 no-store 정책이다. React fetch도 cache: no-store를 사용한다. CDN/Redis 또는 명시적 Spring 업무 결과 캐시는 설정되어 있지 않다. [S02](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/security/SecurityConfiguration.java:24) [S18](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/publicapi/PublicSiteApi.java:14) [S28](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/docs/PUBLIC_API_V1.md) [S32](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/api.ts:9)
- PublicSiteService의 Map은 페이지 응답 하나를 조립하면서 같은 콘텐츠를 중복 변환하지 않기 위한 요청 내 재사용이다. 다음 요청까지 유지되는 공개 데이터 캐시가 아니다. [S19](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/java/egovframework/backoffice/mvp/publicapi/PublicSiteService.java:37)
- Thymeleaf의 template cache는 기본 true, dev/local/preview에서는 false다. 템플릿 해석 캐시이며 발행 데이터 캐시와 다르다. [S24](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/application.yml:1)
- React는 방문한 편집 화면과 읽은 데이터를 메모리에 유지한다. 다른 창의 수정 내용은 자동 실시간 동기화되지 않아 다시 조회가 필요할 수 있다. 저장 충돌 검사와 구분해야 한다. [S15](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/useWorkspaceRoutes.ts:10)

지금 별도 캐시를 추가할 이유는 확인되지 않았다. 공개 트래픽/실제 홈페이지 배포 구조가 정해진 후 공개 중단·재발행 시 무효화까지 함께 검토한다.

### 시간대의 현재 범위

| 단계 | 현재 구현 | 의미 |
|---|---|---|
| DB | TIMESTAMP, CURRENT_TIMESTAMP | offset/시간대 자체를 저장하지 않음 |
| Java | LocalDateTime, 통계 일부 LocalDate.now() | DB/JVM의 기본 환경 영향을 받음 |
| API | offset 없는 LocalDateTime 문자열 | UTC라고 가정하거나 Z를 붙이면 안 됨 |
| React 관리자 날짜 | T를 공백으로 바꾸고 일부 문자열 표시 | 시간대 변환 없음 |
| Thymeleaf | LocalDateTime 포맷 | 표시 포맷과 시간대 변환은 다름 |
| 자동저장 완료 시각 | 브라우저 new Date().toLocaleTimeString('ko-KR') | 한국어 표기지만 시간대는 브라우저 기준 |
| 기존 설정 | site_settings에 timezone=Asia/Seoul 초기값 | 현재 시간 생성/직렬화/화면 표시에서 이 설정을 사용하는 코드를 찾지 못함 |

[S24](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/application.yml:1) [S25](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/src/main/resources/db/migration/h2/V2__cms.sql:68) [S28](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/docs/PUBLIC_API_V1.md) [S31](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/ui.tsx:17) [S12](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/frontend/src/ContentEditor.tsx:35)

**추천:** 운영 표시 시간대는 Asia/Seoul을 기본 후보로 삼되, 한 곳의 설정에서 서버·API·클라이언트에 일관되게 적용한다. 기존 timezone 값을 실제로 연결할지 별도 중앙 설정으로 대체할지 하나를 선택한다. 파일마다 ZoneId를 박는 식으로 처리하지 않는다.

저장/API를 명시적 시각(UTC/offset 포함)으로 정리하려면 기존 offset 없는 값의 해석을 먼저 확정해야 한다. 기존 DB를 임의로 UTC로 재해석하거나 일괄 변환하지 않는다. 현재 값의 작성 환경 확인, 자정 경계 통계와 API 표시 검증을 5C 전 확인 대상으로 남긴다.

## 10. 추천 운영 기본안

다음은 채택을 기다리는 제안이며, 현재 권한을 바꾼 것이 아니다.

1. 세 역할을 유지한다. SUPER_ADMIN은 계정·공용 템플릿·사이트 구조 생성 책임, ADMIN은 일상 콘텐츠/기존 페이지 운영 및 발행, SUPPORTER는 본인 콘텐츠 초안 작성·수정을 담당한다.
2. 승인/반려 시스템 없이 발행 담당자가 직접 확인 후 발행한다. SUPPORTER 발행/공개 중단 제한 여부는 별도 결정한다.
3. 글·페이지의 자동 초안 저장과 수동 저장을 함께 유지한다. 템플릿·공통 설정은 명시적 저장을 유지한다.
4. 일시적 내림은 공개 중단, 데이터 삭제는 참조/복구 가능성을 확인한 별도 작업으로 취급한다.
5. 이전 버전은 새 초안으로 복구한다. 공개본 직접 롤백을 기본 동작으로 만들지 않는다.
6. 미디어는 현재 문서·공용 템플릿·보존 이력이 참조하는 동안 삭제를 막는다.
7. 공용 템플릿 사용과 관리는 SUPER_ADMIN 전용으로 유지한다. 이미 합의한 범위를 ADMIN에게 자동 확대하지 않는다.
8. 원본 V3는 계속 유지한다. 5B 후속 구현·검증도 새 검증 사본에서 하며 5C 원본 전환과 분리한다.

## 11. 5B-2 구현 후보와 순서

아직 구현 승인이 아니라 다음 단계의 구체적인 범위 제안이다.

| 우선순위 | 제안 기능 | 필요한 변경 / 완료 기준 | 먼저 결정할 것 |
|---|---|---|---|
| 1 | 템플릿 미디어 참조 보호 | 공통 사용처 조회와 삭제 차단에 활성/비활성 템플릿·리치본문 포함; 참조 파일 삭제 실패 검증 | 이력/숨김 snapshot까지 보존할 범위 |
| 1 | 저장/공개 중단/삭제 충돌 검사 통일 | 모든 변경 명령의 expected revision 검증, 상태 변경의 버전 증가, 기존 폼과 React 회귀 검사 | 정책 변경 없이 안전성 보강 가능하나 후속 구현 승인 필요 |
| 1 | 편집 이탈·실패 안내 통일 | 콘텐츠 이동 guard, 템플릿 닫기 확인, 콘텐츠 발행 진입 전 저장 확인, 실패 시 입력 유지 | 숨겨진 편집기의 자동저장 유지 여부 |
| 2 | 콘텐츠·페이지 버전 이력과 새 초안 복구 | 불변 snapshot, 목록/비교/복구 API·UI, 복구 출처 로그, 분류/미디어/주소/POSTS 보존, retired block ID 처리 | 보관 시점·기간·개수 및 복구 권한 |
| 2 | 템플릿 버전 이력과 복원 후보 | 버전 저장/비교, 명시적 복원 저장, 기존 적용 페이지 불변 검증 | 같은 이력 정책 적용 범위; SUPER_ADMIN 전용 유지 |
| 2 | 삭제/공개 중단 영향 안내 | 페이지·템플릿·manual 참조 표시, 첫 화면 영향, category/query 참조 보호 | 휴지통 구현 여부·완전 삭제 권한/보관기간 |
| 3 | 확정된 역할 정책 반영 | SUPPORTER 발행/비공개/삭제와 페이지 생성·slug·삭제 검사 분리, 양 UI 및 직접 요청 테스트 | 아래 정책 결정 후에만 진행 |
| 3 | 공통 설정의 공개 계약/변경 근거 | 공개할 site 정보·SNS·로고 allowlist, 영향 안내와 변경 전후 기록 | 즉시 반영 범위와 로고 공개 접근 정책 |
| 3 | 시간 기준 통일 및 복구 절차 재검증 | 중앙 시간대 계약, 기존 값 해석 확인, DB/JAR 동시 복원 리허설 | 기존 timestamp 해석·운영 DB 선택 |

이력 저장 및 복구 가능한 휴지통은 DB 변경이 필요한 신규 기능이다. 기존 revision/발행본만 재사용해서 완료했다고 처리할 수 없다. 5B-2에서 범위를 정하면 먼저 설계하고 **사본 DB에서만** 검증한다. 승인/반려, 예약 발행, 캐시 신설, 원본 migration은 이 목록에 포함하지 않는다.

## 12. 정책 확정 항목

| 결정 항목 | 현재 사실 | 추천 / 선택 필요 |
|---|---|---|
| SUPPORTER 발행·공개 중단 | 본인 글에 현재 허용 | 초안 담당으로 제한하는 안 추천. 직접 발행 유지도 선택 가능 |
| 승인형 도입 | 미구현 | 지금은 도입하지 않는 A안 추천 |
| 새 페이지/slug/페이지 삭제 | SUPER_ADMIN·ADMIN 가능 | SUPER_ADMIN 중심으로 제한 추천 |
| SUPPORTER 삭제 | 발행 상태와 관계없이 본인 가능 | 본인 미발행 초안만; 발행 경험 있는 글은 관리자 담당 추천 |
| 삭제 데이터 보존 | 콘텐츠 연결 일부 제거, 페이지/미디어 실제 삭제 | 휴지통 도입 대상·보관기간·최종 삭제 권한 결정 |
| 자동저장 | 글/페이지 1.8초, 숨겨진 편집기도 동작 | 주기 유지, 명시 저장 병행. 화면 이동 뒤 저장을 계속할지 안내·정책 통일 |
| 버전 보관/복구 | 과거 버전 기능 없음 | 보관 지점·기간·개수, 자동저장 묶음, 복구 권한 결정 |
| 공통 설정 | 즉시 DB 저장, 일부 공개 계약 미구현 | 명시 저장 즉시 반영 추천; 사이트 정보/SNS/로고 공개 필드 결정 |
| 시간대/API 시각 | offset 없는 값, timezone 설정 미연결 | 중앙 Asia/Seoul 표시 후보와 기존 값 해석, API 시각 표준 결정 |
| 템플릿 권한 | SUPER_ADMIN 사용·관리 | 이미 정한 현재 범위 유지; 이번에 재결정할 필요 없음 |

## 13. 5C 전에 계속 남겨 둘 기술 위험

1. **H2/Flyway 호환 경고:** H2 2.3.232에 대해 사용 중인 Flyway가 검증한 H2 범위는 2.2.224까지라는 경고가 있다. 호환 조합 검토와 필요시 별도 의존성 변경·전체 migration/복원 검증이 필요하다.
2. **파일 DB 연결 수명:** 짧은 연결로 연속 Flyway 실행 시 재실행 건수가 0이 아니었던 문제가 있었고 내부 원인은 미확정이다. 연결 유지·재접속 검증 통과를 원인 해결로 표현하지 않는다. 동일 JDK/Windows/저장 위치/배포 JAR와 최신 원본의 새 사본으로 독립 실행 경로까지 다시 확인한다.
3. **실제 홈페이지 미연결:** 5A는 API/계약 완료다. 실제 렌더링, 배포/CORS, 미디어 표시, 캐시, 관리자 미리보기와 실제 화면의 E2E는 홈페이지 확보 후 검증한다.
4. **원본 V3와 검증 V9 차이:** V4~V6 분류, V7 맛집 주소, V8 block ID, V9 템플릿 및 이후 JSON/API 계약 차이가 있다. 운영 사전과 검증 fixture도 구분해야 한다. 최신 JAR를 원본에 시험 삼아 연결하거나 DB만 바꾸지 않는다.
5. **DB와 실행 파일 동시 복구:** 같은 V8/V9라는 이유만으로 이전 바이너리와 섞으면 query/manual/Variation 같은 JSON 계약을 잃을 수 있다. 대응 DB/JAR·소스·설정·checksum을 한 기준점으로 보존하고 새 사본에서 복구 리허설한다.
6. **운영 전 보완할 데이터 보호:** 템플릿 미디어 보호, 삭제 복구 범위, 과거 버전 부재, 일부 변경 명령의 revision 누락 허용, 시간대 기준은 기능 테스트 성공과 별개로 남아 있다.
7. **운영 DB/부하·배포 확인:** 파일 H2 검증 환경이 실제 운영 배치를 승인한 것은 아니다. 공개 트래픽·백업 중 쓰기·연결 풀·응답 크기/파일 전송과 운영 책임을 5C 전에 확인해야 한다.

기존 위험 원문 [S26](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/docs/DB_MIGRATION_RISKS.md), 5A 기준점 및 검증 기록 [S27](C:/Users/sfsf1/OneDrive/Desktop/exex1/BOFC/AICA-back-office-main/docs/PHASE5A_RESULTS.md)을 유지한다. 이전 단계의 Java 131건·프런트엔드 39건 통과는 **이전 5A 보고 결과**이며 이번 5B-1에서 재실행한 결과가 아니다. 이 문서의 정책 제안이나 미구현 복구 기능이 검증되었다는 뜻도 아니다.

