# Local bootRun shadowed Docker backend

- 발생 일시: 2026-06-25
- 영역: backend / docker / local-dev
- 심각도: medium

## 증상
Docker 백엔드를 빌드하고 재기동했는데도 브라우저의 AI 리뷰 화면에는 이전 fallback 증상이 계속 보였다. 사용자는 팀원들이 VSCode에서 백엔드를 띄우는 방식으로 테스트하고 있었고, 화면은 `localhost:3000` 프론트에서 `/api` 프록시를 통해 `localhost:8080`으로 요청을 보내고 있었다.

## 원인
`devmatch-backend` Docker 컨테이너는 `Exited (143)` 상태였고, `localhost:8080`은 Docker가 아니라 로컬 `.\gradlew bootRun --no-daemon` Spring Boot 프로세스가 점유하고 있었다. 그래서 Docker 이미지에 반영한 수정은 브라우저 요청 경로에 적용되지 않았다.

프론트 개발 서버는 `/api` 요청을 `http://localhost:8080/api/:path*`로 프록시한다. 로컬 백엔드 설정은 Python AI 기본 URL을 `http://localhost:8001`로 사용한다.

관련 파일:

- `frontend/next.config.js:8`
- `backend/src/main/resources/application.yml:76`
- `backend/src/main/resources/application.yml:89`

## 해결 방법
Docker 백엔드는 사용하지 않는 기준으로 정리하고, `localhost:8080`을 점유하던 기존 로컬 bootRun 프로세스를 종료한 뒤 수정된 코드로 로컬 백엔드를 재시작했다.

재시작 시 로컬 AI 기준 env를 명시했다.

- `AI_REVIEW_PROVIDER=PYTHON`
- `PYTHON_AI_ENABLED=true`
- `PYTHON_AI_BASE_URL=http://localhost:8001`
- `OLLAMA_BASE_URL=http://localhost:11434`

검증:

- `netstat` 기준 `localhost:8080` 리스너가 새 로컬 Spring Boot PID로 변경됨
- `localhost:8001` Python AI 리스너 유지
- 관리자 후보 API에서 `frontend-conditional-rendering.sourceQuestion`이 `frontend:67` 대신 실제 원문 질문으로 반환됨

## 재발 방지 / 메모
팀원 테스트 기준이 VSCode/로컬 bootRun이면 Docker 백엔드 빌드/재시작만으로는 화면에 반영되지 않는다. 프론트가 보는 대상은 항상 `localhost:8080`이므로, 수정 확인 전 `netstat -ano | Select-String ':8080'`로 포트 점유 프로세스가 Docker인지 로컬 bootRun인지 먼저 확인한다.

Docker 백엔드를 쓰려면 로컬 bootRun을 먼저 내려야 하고, 로컬 bootRun을 쓰려면 코드 수정 후 해당 프로세스를 재시작해야 한다.
