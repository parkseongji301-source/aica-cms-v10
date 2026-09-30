# 02. 역할·권한 운영 가이드

세 역할은 고정된 업무 권한이다. 운영자가 임의 역할이나 승인 단계를 만드는 기능은 없다. 아래는 V10의 현재 규칙이며 과거 단계의 ‘SUPPORTER 직접 발행 허용’은 더 이상 적용하지 않는다.

| 작업 | SUPPORTER | ADMIN | SUPER_ADMIN |
|---|---|---|---|
| 콘텐츠 목록/상세·미리보기 | 본인만 | 전체 | 전체 |
| 생성/초안 저장/수정 | 본인만 | 전체 | 전체 |
| 콘텐츠 발행/재발행/공개 중단 | 차단 | 허용 | 허용 |
| 콘텐츠 영구 삭제 | 차단 | 차단 | 영향 확인 후 허용 |
| 콘텐츠 버전 조회 | 본인만 | 전체 | 전체 |
| 콘텐츠 버전 복구 | 차단 | 전체 | 전체 |
| 미디어 조회·업로드·메타데이터 수정/사용 | 본인 파일 | 전체 | 전체 |
| 미디어 영구 삭제 | 차단 | 차단 | 참조 없는 파일만 |
| 사이트 구조의 페이지/블록 탐색 | 차단 | 허용 | 허용 |
| 기존 페이지 내용/블록/Variation 편집 | 차단 | 허용 | 허용 |
| 기존 페이지 발행/공개 중단 | 차단 | 허용 | 허용 |
| 페이지 이력 조회·새 초안 복구 | 차단 | 허용 | 허용 |
| 새 페이지 생성/slug 변경 | 차단 | 차단 | 허용(기존 화면) |
| 페이지 영구 삭제 | 차단 | 차단 | 참조 확인 후 허용 |
| 템플릿 불러오기·저장·수정·비활성·이력·복구 | 차단 | 차단 | 허용 |
| 메뉴/기존 카테고리 변경 | 차단 | 차단 | 허용 |
| 공통 스타일(메뉴 비노출, 기존 주소 유지)·사이트 정보·SNS/링크 설정 | 차단 | 차단 | 허용 |
| 공통 컴포넌트 관리 화면 | 차단 | 차단 | 등록 목록 조회; 코드 제작 불가 |
| 계정 발급/역할 변경/활동 이력 | 차단 | 차단 | 허용 |
| 신규 유형/기수/주제 사전 편집 UI | 미구현 | 미구현 | 미구현; 별도 승인 데이터 작업 |

공통 컴포넌트 입력을 PageEditor에서 사용하는 것은 ADMIN도 가능하다. ‘공통 구조 관리(구 디자인 관리)의 전역 카탈로그 화면 접근’과 ‘기존 페이지 블록 편집’을 혼동하지 않는다. 템플릿은 ADMIN에게 사용 권한도 열지 않았다.

## 역할별 업무 인계

- SUPPORTER: 본인 초안을 작성·저장하고 ADMIN에게 별도 운영 연락 수단으로 확인을 요청한다. CMS 안의 검토 요청·승인/반려 상태는 없다. 본인 발행 글을 수정해도 초안만 바뀐다.
- ADMIN: 전체 콘텐츠를 검토하고 직접 발행한다. 기존 페이지 내용과 블록을 관리하고 잘못된 초안을 버전으로 복구할 수 있다. URL·사이트 전역 구조·템플릿·영구 삭제는 SUPER_ADMIN에게 요청한다.
- SUPER_ADMIN: 구조·전역 설정·계정·삭제·템플릿을 책임진다. 사용자 권한이 크다고 새 코드를 등록하거나 schema를 운영 UI에서 바꿀 수 있는 것은 아니다.

새 계정 발급은 ADMIN/SUPPORTER를 선택하는 기존 계정 화면을 사용한다. SUPER_ADMIN으로의 변경은 별도 역할 변경 절차에서 수행한다. 서비스는 자기 계정 변경 및 마지막 활성 SUPER_ADMIN 보호를 검사하므로 오류가 나면 우회 SQL을 쓰지 않는다. 운영 업체 교체 시 마지막 관리자 접근을 먼저 확보한 뒤 기존 계정을 처리한다. 초기 관리자 bootstrap을 다시 실행해 계정을 덮어쓰지 않는다.

## UI와 서버의 같은 제한

React는 bootstrap의 권한 값으로 메뉴/버튼을 표시·비활성화한다. Thymeleaf도 같은 역할 정책을 반영한다. 서버는 다음을 별도로 검사한다.

- Spring Security URL 접근 권한, 로그인 세션 및 변경 요청 CSRF.
- CurrentAccount/AccountSessionFilter의 현재 계정 상태와 auth_version.
- AccessPolicy의 콘텐츠 소유권·발행·구조·영구 삭제 capability.
- CmsAccess와 각 서비스의 대상별 권한, VersionHistoryService의 이력/복구 권한.

주소를 직접 입력하거나 버튼 요청을 복사해도 본인 범위/역할을 우회할 수 없다. 미디어 사용처의 상세 링크도 대상 권한에 따라 제한될 수 있다. 공개 API는 별도 익명 읽기 체인이며 관리자 세션·이력·초안을 제공하지 않는다.

403은 권한 또는 CSRF 문제를 확인하고, 401은 재로그인이 필요하다. revision 충돌은 권한을 높인다고 해결되지 않는다. 최신 문서를 다시 확인해야 한다.

## 업체 교체 시 확인

계정 책임자, 현재 활성 SUPER_ADMIN, 새 업체 담당자별 역할, 이전 업체 계정 처리 시점, 비상 연락처를 별도 인수 목록으로 작성한다. 계정은 사람별로 발급하고 비밀번호·세션 값은 이 문서나 소스·티켓에 적지 않는다. 변경 권한 승인은 활동 이력으로 확인하되 이력 파일을 비밀정보 전달 수단으로 쓰지 않는다.

근거: [AccessPolicy](../../../src/main/java/egovframework/backoffice/mvp/security/AccessPolicy.java), [SecurityConfiguration](../../../src/main/java/egovframework/backoffice/mvp/security/SecurityConfiguration.java), [CmsAccess](../../../src/main/java/egovframework/backoffice/mvp/cms/CmsAccess.java), [AccountService](../../../src/main/java/egovframework/backoffice/mvp/account/AccountService.java), [이력 권한](../../../src/main/java/egovframework/backoffice/mvp/version/VersionHistoryService.java).
