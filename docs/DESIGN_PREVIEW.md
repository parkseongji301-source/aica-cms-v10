# AICA 백오피스 실행 안내

현재 구현 범위와 사용 방법은 [CMS_IMPLEMENTATION.md](CMS_IMPLEMENTATION.md)를 참고하세요.

기본 개발 실행은 `scripts/run-local.ps1`입니다. `local` 프로필의 파일 DB에 콘텐츠·이미지·페이지·메뉴·설정이 유지됩니다. 관리자 주소는 http://127.0.0.1:8081/admin 입니다. 가상 홈페이지는 제공하지 않으며 실제 홈페이지는 아직 연결하지 않았습니다.

`run-preview.ps1`과 `design-preview` 프로필은 초기 화면 검토에 사용한 메모리 DB 환경입니다. 해당 환경은 종료하면 데이터가 사라지므로 지속적인 작업에는 `run-local.ps1`을 사용합니다.

방문 통계는 대시보드에만 예시 데이터로 표시합니다. 별도 방문자 통계 메뉴는 없습니다.
