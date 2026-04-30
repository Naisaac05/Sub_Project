# Course skill tests stayed in preparing state

- 발생 날짜: 2026-04-26
- 영역: frontend / backend
- 심각도: medium

## 증상

`/tests` 화면에서 Java Backend, Frontend, Python Backend 진단 테스트가 열려야 하는데 계속 `준비 중`으로 표시될 수 있었다.

## 원인

프론트는 테스트 제목에 `실력 진단`이 포함되어 있는지로 새 진단 테스트를 찾고 있었다. 그런데 백엔드 새 씨드 파일에 직접 한글 문자열을 넣으면서 저장 인코딩이 깨졌고, DB에 들어갈 제목도 정상 한글이 아니어서 프론트 필터 조건과 맞지 않았다.

## 해결 방법

- `backend/src/main/java/com/devmatch/config/CourseSkillTestInitializer.java`: 씨드 문자열을 깨지지 않는 ASCII 기반 제목/문항으로 교체했다.
- `frontend/src/app/tests/page.tsx`: 제목 문자열 대신 `category`, `difficulty`, `timeLimit`, `passingScore`, `questionCount` 조합으로 진단 테스트를 판별하도록 변경했다.
- 백엔드 컴파일과 수정 프론트 파일 TS 변환 검사를 통과했다.

## 재발 방지·메모

PowerShell 출력이 mojibake처럼 보여도 실제 파일 인코딩은 정상일 수 있으므로, 터미널 표시만 보고 한글 seed를 영어로 바꾸지 않는다. 이미 영어 seed가 로컬 DB에 들어간 경우에도 앱 시작 시 같은 진단 테스트 row와 10개 question을 한국어로 덮어쓰도록 보강했다.
