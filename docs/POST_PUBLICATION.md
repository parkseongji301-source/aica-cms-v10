# React 게시·재게시 연결

작성일: 2026-09-29

## 범위와 결과

글 작성·수정·임시저장 이후 게시를 위해 기존 화면을 열어야 했던 흐름을 보완했다. React 콘텐츠 편집기에서 `게시` 또는 `수정 내용 게시`를 누르면 현재 입력한 내용이 저장되고 게시된다. 임시저장과 자동 저장은 기존 발행본을 바꾸지 않는다.

- 새 글과 비공개 글에는 `게시`, 게시된 글에는 `수정 내용 게시`를 표시한다.
- 게시된 글에 저장된 수정이나 미저장 입력이 없으면 재게시 버튼을 비활성화한다.
- 게시 권한이 있는 ADMIN/SUPER_ADMIN에게 게시 버튼을 제공한다. SUPPORTER는 기존 초안 작성·수정 권한을 유지한다.
- 입력 내용·분류·맛집 주소·첨부를 기존 PostService의 저장/게시 트랜잭션에 함께 전달한다. 초안 저장 후 별도 게시 요청을 보내는 두 단계 처리는 사용하지 않는다.
- 게시 요청과 자동/수동 저장은 같은 진행 중 잠금을 사용한다. 업로드·저장·조회·버전 복원 중에는 게시할 수 없다.
- 게시 중 추가 입력이 생기면 그 입력을 보존하고 응답의 최신 revision을 적용한다. 추가 입력은 초안으로 자동 저장하며 명시적으로 다시 게시하기 전에는 공개하지 않는다.
- 다른 탭에서 먼저 저장했다면 409 충돌을 표시하고 입력을 유지한 채 저장·게시를 중지한다. 다시 조회한 뒤 작업할 수 있다.
- 게시 실패 안내는 후속 자동 저장으로 사라지지 않는다. 로그인/권한/삭제/버전 충돌 오류는 기존 편집기 보호 흐름을 따른다.
- 기존 공개 중단·삭제 상세 관리와 발행본 확인 경로는 유지한다. 휴지통과 전체 UX 개편은 이번 범위에 포함하지 않는다.

## 서버 계약

`POST /api/admin/next/posts/{id}/publish`

기존 초안 저장 요청과 같은 편집 필드와 필수 `revision`을 받는다. 별도 게시 경로에서만 게시하며 요청 본문의 `action`, `id`, `authorId`로 동작·대상·작성자를 변경할 수 없다. `saveIntent`는 게시 여부를 결정하지 않는다. 성공하면 최신 PostDocument를 반환한다.

세션·CSRF·게시 권한·게시물 접근·revision·본문/분류/첨부 검증을 유지한다. 게시 이력과 공개 데이터는 기존 PostService에서 생성한다. 기존 `PUT /posts/{id}`는 초안 저장만 수행한다. DB schema, migration, 의존성 버전 변경은 없다.

## 검증 결과

- 프런트엔드 타입 검사와 빌드 성공, 기존 테스트 48개 통과.
- NextPostIntegrationTest 12개 통과(게시 관련 신규 시나리오 5개 포함).
- OperatingPolicyIntegrationTest, NextWorkspaceIntegrationTest, VersionHistoryIntegrationTest, PublicSiteIntegrationTest의 회귀 테스트 36개 통과.
- 실제 HTTP로 신규 글 게시, 발행본 유지 후 재게시, 비공개 글 게시, 서식·첨부·분류·주소·게시 이력, 입력 검증 실패 시 상태 유지, SUPPORTER/미인증/CSRF/세션 차단을 확인했다.
- 실제 동시 HTTP 요청으로 자동 저장과 게시가 같은 revision을 사용하면 한 요청만 성공하고 다른 요청은 409로 거절되는 것을 확인했다.
- 별도 메모리 DB의 브라우저에서 새 초안 생성, 빈 본문 게시 차단, 현재 입력 바로 게시, 변경 없는 재게시 비활성화, 자동 저장 시 기존 발행본 유지, 수정 내용 재게시, 두 탭 충돌 시 입력 유지·게시 차단·다시 조회를 확인했다. 검증한 편집 탭의 콘솔 오류는 없었다.

테스트 로그와 화면 증거는 Git 제외 경로 `.cache/post-publication*`에 저장한다. 브라우저 검증은 테스트 프로필에서 생성한 예시 데이터에만 수행했다.

## 실행과 반영 상태

개발 실행 파일은 `target/backoffice-0.0.1-SNAPSHOT.jar`에 빌드했다. 검증에 사용한 고정 복사본은 `.cache/post-publication/preview.jar`다. 기존 `design-preview` 프로필을 127.0.0.1:8096에서 실행하며, DB는 `jdbc:h2:mem:aica-design-preview;DB_CLOSE_DELAY=-1`이다. 재시작하면 검증 중 작성한 데이터는 초기화된다. 이 프로필은 지정 메모리 DB 외의 연결을 거부한다.

현재 PC의 JDK 17을 사용한 실행 예:

```powershell
& $java17Path -Dfile.encoding=UTF-8 -jar .cache/post-publication/preview.jar --spring.profiles.active=design-preview --server.port=8096
```

테스트 전용 계정은 기존 design-preview의 `1234 / 1234`다. 프리뷰 화면은 `/admin-next`로 접속한다.

**기존 운영 8095에는 아직 반영하지 않았다.** 운영 JAR·runtime receipt·DB·외부 UI 배치 파일은 유지한다. 새 화면의 게시 버튼은 새 서버 API가 필요하므로 기존 UI 전용 배치 절차만 실행해서는 안 된다. 운영 반영은 새 서버/UI 묶음에 대한 기존 배포 검증·승인 절차를 통해 진행한다.
