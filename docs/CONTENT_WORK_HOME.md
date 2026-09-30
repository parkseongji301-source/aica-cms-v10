# 콘텐츠 작업 홈 · 기능 동결

> 아래 "현재 집 PC 실행"은 2026-09-29 집 PC 기준 기록이다. 이 PC의 현재 실행은 [V12 안정 기준점](V12_STABLE_BASELINE.md)을 따른다.

2026-09-29 집 PC의 이관된 V11 / 8095에 적용했다. 콘텐츠 목록을 콘텐츠 작업의 첫 화면으로 옮기고 기존 검색·분류·작성창·React 편집기를 연결한다. 휴지통과 독립 미디어 관리는 사이트 관리에 남긴다.

## 변경 범위

- 사이트 관리의 콘텐츠 목록 메뉴를 제거하고 콘텐츠 작업 맨 위에 전체 콘텐츠를 둔다.
- 콘텐츠 작업 전환은 전체 목록으로, 사이트 관리 전환은 관리 대시보드로 이동한다. 페이지 요약에서 사이트 관리 전환 시에는 기존처럼 같은 페이지 편집기로 이동한다.
- 이전 `view=manage` 콘텐츠 링크와 다른 화면의 콘텐츠 연결도 콘텐츠 작업으로 표시한다. 휴지통·미디어 등 관리 화면은 사이트 관리로 표시한다.
- 기존 URL, 필터 조건, 유형 복수 선택, 작성창 초기값, 저장·게시·휴지통 처리, 권한과 미저장 이동 확인을 유지한다.
- 기능·API·DB·migration·의존성 버전은 변경하지 않는다. 이후 디자인·UX 작업에도 [기능 동결 범위](UI_UX_FREEZE.md)를 적용한다.

## 현재 집 PC 실행

저장소 루트에서 실행한다. 이관본과 UI release는 Git 제외 경로에 있으며 다른 PC에 소스만 복제하면 생성되지 않는다.

```powershell
# 종료: PID 및 정상 종료·cold 검사를 포함한 기존 이관 도구
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .cache/runtime-v11/STOP.ps1

# 현재 화면으로 시작
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/start-v11-ui.ps1 `
  -UiRelease .cache/ui-releases/elegant-shell-20260929-1
```

접속: http://127.0.0.1:8095/admin-next/posts?view=structure

현재 release는 `elegant-shell-20260929-1`이다. [공통 작업면 재정리](ELEGANT_WORKSPACE.md)를 적용했다. [사이드바 정리](SIDEBAR_CLEANUP.md)를 적용했으며, 디자인·계정·설정·공통 안내·로그인 개선을 포함한다. [설정·인증 UX](SETTINGS_AUTH_UX.md)(14·16·17·18·19번)를 참조한다. [운영 관리 UX](OPERATIONS_UX.md)(10·11·12·13번)를 참조한다. [페이지·템플릿 UX](PAGES_TEMPLATES_UX.md)(8·9·15번)를 참조한다. [공통 디자인 기록](CALM_SHELL.md), [콘텐츠 탐색·공통 화면](NAVIGATION_UX.md)(6·7번), [하위 분류 메뉴](SIDEBAR_SECTIONS_UX.md), [글쓰기 UX](WRITING_UX.md)(3·4·5번), [콘텐츠 목록 UX](CONTENT_LIST_UX.md)(1·2번)도 포함한다. 이전 UI release 폴더는 모두 보존한다. 직전 UI는 `.cache/ui-releases/sidebar-cleanup-20260929-1`이다.

`scripts/v11/serve_ui.py`는 원래 이관 도구로 JAR·receipt·실행 설정·도구 hash를 확인하고, UI manifest의 모든 파일을 검증한다. `next-app/`과 선택적인 인증 스타일 결합 파일 `css/flow.css`만 별도 정적 경로에서 읽으며 나머지 리소스는 같은 승인 JAR를 사용한다. 인증 스타일은 승인 JAR 원본 CSS를 그대로 보존한 결합인지 추가 검증한다. 자세한 생성·검증 규칙은 [설정·인증 UX](SETTINGS_AUTH_UX.md)를 따른다. 원래 도구와 승인 파일은 수정하지 않는다. 원래 DB 경로, validate-only, bootstrap 비활성화, `AUTO_COMPACT_FILL_RATE=0`, 로컬 바인딩과 정상 종료 방식을 유지한다.

UI 되돌리기는 정상 종료 후 기존 `.cache/runtime-v11/START.ps1`로 같은 DB와 V11 JAR를 실행하면 된다. 데이터 복원이나 migration은 필요하지 않다.

다음 UI 수정은 프런트엔드를 빌드하고 새 release 폴더에 `assets/next-app`과 `ui-manifest.json`을 생성한다. manifest에는 대상 `runtimeJarSha256` 및 assets 기준 상대 경로별 SHA-256 `files`를 기록한다. 이전 release와 이관 원본을 보존하고, 검증 후 새 경로로 시작한다.

## 확인 결과

2026-09-29 **20번 전체 사용자 흐름 검수 완료**. [최종 사용자 흐름 검수 기록](FINAL_USER_FLOW_QA.md)에 핵심 업무·세 역할·동시 수정·모바일 및 프런트 49건/서버 55건 결과를 기록했다. 실제 홈페이지 연결 검수는 별도다.

- 기존 프런트엔드 48건, TypeScript 검사, Vite 빌드 통과.
- 예시 데이터 환경에서 작업 홈 진입, 유형 필터, 작성창 열기/취소, React 편집기, 미저장 이동 확인과 취소, 뒤로/앞으로, 기존 콘텐츠 주소, 휴지통 위치 및 390px 모바일 탐색 확인. 브라우저 오류·경고 없음.
- 서포터즈의 전체 목록·자기 글 편집 진입 권한 조건을 유지하고, 휴지통·페이지 권한 조건 유지 확인. 실제 서포터즈 로그인 검사는 수행하지 않았다.
- 8095 정상 종료·재시작과 로그인/공개 API 응답 확인. 적용 전후 전체 36개 테이블 fingerprint, schema, migration 이력이 동일하다.
- 승인 JAR·receipt·원래 실행 설정의 SHA-256 동일. 실제 8095 로그인 후 업무 저장·게시 조작은 수행하지 않았다.

실행 증거와 적용 전 DB 사본은 `.cache/content-work-rollout/`, 화면 파일은 `.cache/ui-releases/content-work-home-20260929-1/`에 보관한다. 예시 화면 검증용 메모리 서버는 검증 후 종료한다.
