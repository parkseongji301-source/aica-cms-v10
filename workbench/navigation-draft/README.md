# 중단된 탐색 변경 보존
이 폴더의 .txt 파일은 React 1차 착수 직전 변경을 원본 그대로 보존합니다. 자동 컴파일/Flyway 대상이 아닙니다.
V4 자동 IA 등록은 적용하지 않았습니다. FAQ는 최신 IA에서 지원 전 Check!! 하위이며 추후 구조 변경과 데이터 연결을 별도로 설계합니다.
전체 소스/Git 이력/DB 복구 기준점 위치는 .cache/react-pilot-checkpoint.txt 및 해당 RESTORE.md를 참고하세요.
React 1차~3A는 V3 DB를 사용했습니다. 3B-2A에서는 이 미완성 Java V4를 계속 격리하고,
모든 확인 가능한 로컬·검증 DB의 미적용 이력을 확인한 후 신규 SQL V4~V6을 분류 전용으로 추가했습니다.
기존 `.java.txt` 파일은 변경하지 않으며, 신규 migration에 IA/메뉴 트리 입력은 없습니다.
원본 DB는 V3를 유지하고 복사 DB만 V6로 검증합니다. 현재 기준점은 `.cache/react-phase3b2a-checkpoint.txt`입니다.
세부 내용은 `docs/REACT_PHASE3B2A.md`를 참고하세요.
