# 5C-1 검증 도구

애플리케이션 기능이나 schema migration이 아니다. 사용 전 `docs/PHASE5C1_CUTOVER_REHEARSAL.md`와 `docs/PHASE5C2_CUTOVER_RUNBOOK.md`의 실패 사례와 STOP 조건을 확인한다.

- `StepwiseMigrationAudit.java`: 기존 값/행/BLOB fingerprint와 Flyway helper.
- `CutoverAudit.java`: V3→V10 단계별 검증, 읽기 snapshot, 파일 lock, 연결 수명 진단. **원본 경로 거부**, 이미 존재하는 `.cache/phase5c1` 사본만 허용.
- `CandidateDictionary.java`: 별도 운영 후보 사전, code 기반 insert-if-missing, 충돌 취소, 재실행 동일성. 원본 등록 승인 도구가 아님.
- `http_client.py`: 격리 포트 8095/8096/8097 로그인·기본 조회·baseline.
- `runtime_smoke.py`: 8096 fixture 서버를 변경하는 기능 검사. 원본/승격 후보에 실행 금지. `--resume`은 이번 시험 스크립트의 기대값 수정 후 이어서 검증한 기록용 옵션이다.
- `roles_smoke.py`: 8096에 임시 계정과 콘텐츠를 만들고 역할 제한 검사. 원본/승격 후보에 실행 금지.
- `Start-Rehearsal.ps1`: 기존 리허설 data 경로/세 포트만 허용하는 숨김 기동 helper. 자동으로 schema/runtime 일치를 보장하는 배포 시스템이 아니므로 snapshot을 먼저 확인한다.
- `verify_evidence.py`: 기존 원본/동결 소스 해시, 성공·실패 증거, rollback 일치 확인. `originalCutoverReady=false`가 현재 올바른 결과다.

시작점: 프로젝트 루트, `.cache/phase5c1-current.txt`의 bundle. 컴파일 classpath는 frozen JAR에서 추출한 라이브러리/Java migration과 도구 디렉터리이며 `tools/classpath.txt`에 기록했다. 다른 JAR로 바꾸면 다시 추출·컴파일·검증해야 한다.

```powershell
$taskBundle=(Get-Content .cache/phase5c1-current.txt -Raw).Trim()
$taskCp=(Get-Content "$taskBundle/tools/classpath.txt" -Raw).Trim()
& .tools/jdk/jdk-17.0.20.1+1/bin/javac.exe -encoding UTF-8 -cp $taskCp -d "$taskBundle/tools" workbench/cutover-rehearsal/StepwiseMigrationAudit.java workbench/cutover-rehearsal/CutoverAudit.java workbench/cutover-rehearsal/CandidateDictionary.java
```

`migrate`는 매번 **새 V3 사본**을 요구한다. 최신 DB에 반복해서 호출하는 일반 migration 명령이 아니다. 단계 내부의 재실행 0과 독립 JVM 재조회는 자체 검증한다. `different-revision-fixture` 및 `-durable` 시험은 한 JVM에서 초기 연결을 닫고 다시 여는 실패 재현용이며 현재 실패 결과가 보존돼 있다. 명시적 SHUTDOWN 옵션을 해결책으로 사용하지 않는다.

정상인 서로 다른 revision V8 검사는 별도 JVM의 H2 Shell에서 revision 5를 저장/종료하고 독립 snapshot으로 확인한 후, 새 JVM의 `migrate`로 수행했다. `different-revision-pre.json`과 `different-revision-post.json`이 그 증거다.
