# 본문 글꼴 (자체 호스팅)

글쓰기 편집기의 글꼴 선택에서 쓰는 오픈 라이선스 글꼴이다. 모두 **SIL Open Font License 1.1**(상업·웹 사용, 수정, 재배포 허용; 글꼴 파일 자체 판매 금지)이며 각 폴더에 원문 `LICENSE`가 있다. 출처는 npm 공개 저장소의 Fontsource 패키지(버전 5.3.0, 보통 굵기 400, 글자 묶음별 woff2)다. 외부 CDN을 쓰지 않으므로 관리 화면 보안 정책(CSP `style-src 'self'`)을 바꾸지 않는다.

| 편집기 이름 | 클래스(본문 HTML) | font-family | 패키지 |
|---|---|---|---|
| 나눔고딕 | `rt-font-nanumgothic` | Nanum Gothic | @fontsource/nanum-gothic |
| 나눔명조 | `rt-font-nanummyeongjo` | Nanum Myeongjo | @fontsource/nanum-myeongjo |
| 고운돋움 | `rt-font-gowundodum` | Gowun Dodum | @fontsource/gowun-dodum |
| 고운바탕 | `rt-font-gowunbatang` | Gowun Batang | @fontsource/gowun-batang |
| IBM Plex Sans KR | `rt-font-ibmplexsanskr` | IBM Plex Sans KR | @fontsource/ibm-plex-sans-kr |
| 나눔손글씨 펜 | `rt-font-nanumpenscript` | Nanum Pen Script | @fontsource/nanum-pen-script |
| 주아 | `rt-font-jua` | Jua | @fontsource/jua |
| 도현 | `rt-font-dohyeon` | Do Hyeon | @fontsource/do-hyeon |

기존 `rt-font-serif`(명조)·`rt-font-mono`(고정폭)는 그대로다. 값에 하이픈을 쓰지 않는 이유: 편집기(Quill)가 클래스 이름의 마지막 하이픈 앞을 속성 이름으로 읽는다. `fonts.css`는 `../editor.css`가 `@import`하므로 관리 화면·미리보기·발행본 보기에 같이 적용된다. 실제 홈페이지를 연결할 때는 이 폴더와 위 클래스 규칙(`editor.css` 끝부분)을 함께 쓰면 된다.
